package com.wdp.basedet.detection;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player scores for base detection
 */
public class ScoreManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    
    // In-memory score cache for performance
    private final Map<UUID, Double> scoreCache = new ConcurrentHashMap<>();
    
    // Recent interactions for proximity calculation
    private final Map<UUID, List<PlayerInteraction>> recentInteractions = new ConcurrentHashMap<>();
    
    // Walking tracking (per 10 blocks)
    private final Map<UUID, Location> lastWalkLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Double> walkDistance = new ConcurrentHashMap<>();
    
    public ScoreManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        
        // Load scores from database
        loadScoresFromDatabase();
    }
    
    private void loadScoresFromDatabase() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Double> dbScores = plugin.getDatabaseManager().getAllOnlineScores();
            scoreCache.putAll(dbScores);
            plugin.getLogger().info("Loaded " + dbScores.size() + " player scores from database");
        });
    }
    
    /**
     * Get a player's current score
     */
    public double getScore(UUID uuid) {
        return scoreCache.computeIfAbsent(uuid, k -> 
            plugin.getDatabaseManager().getPlayerScore(k)
        );
    }
    
    /**
     * Set a player's score directly
     */
    public void setScore(UUID uuid, double score) {
        score = Math.max(0, score); // Never negative
        scoreCache.put(uuid, score);
        
        if (config.isLogScoreChanges()) {
            plugin.debug("Score set: " + uuid + " -> " + score);
        }
    }
    
    /**
     * Add score for a player interaction
     */
    public void addScore(Player player, PlayerInteraction interaction) {
        UUID uuid = player.getUniqueId();
        double baseScore = interaction.getScore();
        double bonus = calculateProximityBonus(uuid, interaction);
        double totalScore = baseScore * bonus;
        
        double currentScore = getScore(uuid);
        double newScore = currentScore + totalScore;
        setScore(uuid, newScore);
        
        // Store interaction
        storeInteraction(uuid, interaction);
        
        // Log if enabled
        if (config.isLogScoreChanges()) {
            plugin.debug(String.format("Score added: %s +%.2f (base=%.2f, bonus=%.2fx) = %.2f",
                    player.getName(), totalScore, baseScore, bonus, newScore));
        }
        
        // Check for detection threshold
        if (newScore >= config.getDetectionThreshold()) {
            plugin.getDetectionManager().checkDetection(player);
        }
    }
    
    /**
     * Calculate proximity bonus for an interaction
     */
    private double calculateProximityBonus(UUID uuid, PlayerInteraction interaction) {
        if (!config.isProximityEnabled()) {
            return 1.0;
        }
        
        List<PlayerInteraction> recent = recentInteractions.get(uuid);
        if (recent == null || recent.isEmpty()) {
            return 1.0;
        }
        
        int nearbyCount = 0;
        int radius = config.getProximityRadius();
        
        for (PlayerInteraction past : recent) {
            if (interaction.isWithinRange(past, radius)) {
                nearbyCount++;
            }
        }
        
        if (nearbyCount == 0) {
            return 1.0;
        }
        
        // Scale bonus based on nearby interactions
        double bonus = 1.0 + (nearbyCount * (config.getProximityBonusMultiplier() - 1.0) / 10.0);
        return Math.min(bonus, config.getProximityMaxBonus());
    }
    
    /**
     * Store an interaction for future proximity calculations
     */
    private void storeInteraction(UUID uuid, PlayerInteraction interaction) {
        recentInteractions.computeIfAbsent(uuid, k -> new ArrayList<>()).add(interaction);
        
        // Keep only recent interactions (last 100)
        List<PlayerInteraction> interactions = recentInteractions.get(uuid);
        if (interactions.size() > 100) {
            interactions.remove(0);
        }
        
        // Also save to database asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().saveInteraction(interaction);
        });
    }
    
    /**
     * Track player walking
     */
    public void trackWalking(Player player, Location newLocation) {
        UUID uuid = player.getUniqueId();
        Location lastLocation = lastWalkLocation.get(uuid);
        
        if (lastLocation == null || !lastLocation.getWorld().equals(newLocation.getWorld())) {
            lastWalkLocation.put(uuid, newLocation.clone());
            return;
        }
        
        double distance = lastLocation.distance(newLocation);
        double accumulated = walkDistance.getOrDefault(uuid, 0.0) + distance;
        
        // Award points per 10 blocks walked
        while (accumulated >= 10.0) {
            accumulated -= 10.0;
            
            PlayerInteraction interaction = new PlayerInteraction(
                    uuid,
                    newLocation.getWorld().getName(),
                    newLocation.getBlockX(),
                    newLocation.getBlockY(),
                    newLocation.getBlockZ(),
                    PlayerInteraction.InteractionType.WALKING,
                    null,
                    config.getWalkingScore(),
                    System.currentTimeMillis()
            );
            
            addScore(player, interaction);
        }
        
        walkDistance.put(uuid, accumulated);
        lastWalkLocation.put(uuid, newLocation.clone());
    }
    
    /**
     * Get score value for a block placement
     */
    public double getBlockPlaceScore(Material material) {
        // Check for special blocks first
        String materialName = material.name();
        
        // Chests and storage
        if (materialName.contains("CHEST") || materialName.equals("BARREL")) {
            return config.getChestPlaceScore();
        }
        
        // Workbenches
        if (material == Material.CRAFTING_TABLE) {
            return config.getCraftingTableScore();
        }
        
        // Furnaces
        if (material == Material.FURNACE) {
            return config.getFurnaceScore();
        }
        if (material == Material.SMOKER) {
            return config.getSmokerScore();
        }
        if (material == Material.BLAST_FURNACE) {
            return config.getBlastFurnaceScore();
        }
        
        // Anvil
        if (materialName.contains("ANVIL")) {
            return config.getAnvilScore();
        }
        
        // Enchanting table
        if (material == Material.ENCHANTING_TABLE) {
            return config.getEnchantingTableScore();
        }
        
        // Beds (ESSENTIAL - very high confidence!)
        if (materialName.contains("_BED")) {
            return config.getBedScore();
        }
        
        // Doors and gates (ESSENTIAL - high confidence!)
        if (materialName.contains("_DOOR") || materialName.contains("_GATE") || materialName.contains("_TRAPDOOR")) {
            return config.getDoorScore();
        }
        
        // Shulker boxes
        if (materialName.contains("SHULKER_BOX")) {
            return config.getShulkerBoxScore();
        }
        
        // Barrel
        if (material == Material.BARREL) {
            return config.getBarrelScore();
        }
        
        // Default block placement
        return config.getBlockPlaceScore();
    }
    
    /**
     * Get score value for a block break
     */
    public double getBlockBreakScore(Material material) {
        // Breaking has slightly lower value than placing
        return config.getBlockBreakScore();
    }
    
    /**
     * Check if a block type is excluded from scoring
     */
    public boolean isExcludedBlock(Material material) {
        return config.getExcludedBlocks().contains(material);
    }
    
    /**
     * Check if a block is excluded in a specific dimension
     * (e.g., beds in nether/end, respawn anchors in overworld)
     */
    public boolean isDimensionExcluded(Material material, org.bukkit.World.Environment environment) {
        return config.isDimensionExcluded(material, environment);
    }
    
    /**
     * Decay scores for all online players
     * Called periodically by scheduled task
     */
    public void decayScores() {
        if (!config.isScoreDecayEnabled()) {
            return;
        }
        
        double decayAmount = config.getScoreDecayAmount();
        double minimumScore = config.getMinimumScore();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            double currentScore = getScore(uuid);
            
            if (currentScore > minimumScore) {
                double newScore = Math.max(minimumScore, currentScore - decayAmount);
                setScore(uuid, newScore);
                
                if (config.isLogScoreChanges()) {
                    plugin.debug(String.format("Score decay: %s %.2f -> %.2f",
                            player.getName(), currentScore, newScore));
                }
            }
        }
    }
    
    /**
     * Save all scores to database
     */
    public void saveAllScores() {
        for (Map.Entry<UUID, Double> entry : scoreCache.entrySet()) {
            plugin.getDatabaseManager().setPlayerScore(entry.getKey(), entry.getValue());
        }
        plugin.debug("Saved " + scoreCache.size() + " player scores to database");
    }
    
    /**
     * Clear a player's score (after base confirmation)
     */
    public void clearScore(UUID uuid) {
        scoreCache.put(uuid, 0.0);
        recentInteractions.remove(uuid);
        walkDistance.remove(uuid);
        lastWalkLocation.remove(uuid);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().setPlayerScore(uuid, 0);
        });
    }
    
    /**
     * Get recent interactions for a player (for analysis)
     */
    public List<PlayerInteraction> getRecentInteractions(UUID uuid) {
        return recentInteractions.getOrDefault(uuid, new ArrayList<>());
    }
    
    /**
     * Called when player joins
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Load score from database if not cached
        if (!scoreCache.containsKey(uuid)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                double score = plugin.getDatabaseManager().getPlayerScore(uuid);
                scoreCache.put(uuid, score);
            });
        }
        
        // Update last online
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().updateLastOnline(uuid);
        });
    }
    
    /**
     * Called when player quits
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Save score
        double score = getScore(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().setPlayerScore(uuid, score);
            plugin.getDatabaseManager().updateLastOnline(uuid);
        });
        
        // Clear temporary data
        lastWalkLocation.remove(uuid);
        walkDistance.remove(uuid);
    }
}
