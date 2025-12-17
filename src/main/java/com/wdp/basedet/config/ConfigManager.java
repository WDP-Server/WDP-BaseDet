package com.wdp.basedet.config;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages all configuration values for WDP-BaseDet
 */
public class ConfigManager {
    
    private final WDPBaseDetPlugin plugin;
    private FileConfiguration config;
    
    // Cached values for performance
    private String databaseType;
    private double detectionThreshold;
    private int detectionCooldown;
    private boolean scoreDecayEnabled;
    private double scoreDecayAmount;
    private double minimumScore;
    private boolean proximityEnabled;
    private int proximityRadius;
    private double proximityBonusMultiplier;
    private double proximityMaxBonus;
    private int maxBaseWidth, maxBaseLength, maxBaseHeight;
    private int minBaseWidth, minBaseLength, minBaseHeight;
    private int horizontalOffset, verticalOffset;
    private int viewHeightOffset;
    private boolean allowMultipleBases;
    private int maxBasesPerPlayer;
    private int minBaseDistance;
    private boolean autoAbandonOld;
    private int autoConfirmTime;
    private boolean pauseOnLeave;
    private double confirmReward;
    private double denyReward;
    private double autoConfirmReward;
    private boolean particlesEnabled;
    private Particle particleType;
    private Color particleColor;
    private float particleSize;
    private double particleSpacing;
    private boolean showParticlesDuringPrompt;
    private boolean allowParticleToggle;
    private boolean combatEnabled;
    private int combatRadius;
    private boolean allowFightingWhenTagged;
    private boolean allowBlocksDuringCombat;
    private boolean notifyPlayersAboutCombat;
    private int postCombatGrace;
    private boolean discordEnabled;
    private boolean dmOnEntry;
    private boolean allowTrustViaDM;
    private String trustKeyword;
    private Set<Material> excludedBlocks;
    private boolean dimensionExclusionsEnabled;
    private Set<Material> netherExcludedBlocks;
    private Set<Material> endExcludedBlocks;
    private Set<Material> overworldExcludedBlocks;
    private boolean debugEnabled;
    private boolean logInteractions;
    private boolean logScoreChanges;
    private boolean logProtectionChecks;
    private String messagePrefix;
    
    public ConfigManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        config = plugin.getConfig();
        
        // Database
        databaseType = config.getString("database.type", "sqlite").toLowerCase();
        
        // Detection
        detectionThreshold = config.getDouble("detection.detection-threshold", 100);
        detectionCooldown = config.getInt("detection.detection-cooldown", 300);
        
        // Decay
        scoreDecayEnabled = config.getBoolean("detection.decay.enabled", true);
        scoreDecayAmount = config.getDouble("detection.decay.amount-per-minute", 0.5);
        minimumScore = config.getDouble("detection.decay.minimum-score", 0);
        
        // Proximity
        proximityEnabled = config.getBoolean("detection.proximity.enabled", true);
        proximityRadius = config.getInt("detection.proximity.radius", 10);
        proximityBonusMultiplier = config.getDouble("detection.proximity.bonus-multiplier", 1.5);
        proximityMaxBonus = config.getDouble("detection.proximity.max-bonus", 2.0);
        
        // Limits
        maxBaseWidth = config.getInt("limits.max.width", 200);
        maxBaseLength = config.getInt("limits.max.length", 200);
        maxBaseHeight = config.getInt("limits.max.height", 64);
        minBaseWidth = config.getInt("limits.min.width", 10);
        minBaseLength = config.getInt("limits.min.length", 10);
        minBaseHeight = config.getInt("limits.min.height", 3);
        horizontalOffset = config.getInt("limits.offset.horizontal", 3);
        verticalOffset = config.getInt("limits.offset.vertical", 6);
        viewHeightOffset = config.getInt("limits.view-height-offset", 1);
        
        // Multiple bases
        allowMultipleBases = config.getBoolean("bases.allow-multiple", true);
        maxBasesPerPlayer = config.getInt("bases.max-per-player", 3);
        minBaseDistance = config.getInt("bases.min-distance", 500);
        autoAbandonOld = config.getBoolean("bases.auto-abandon-old", true);
        
        // Prompt
        autoConfirmTime = config.getInt("prompt.auto-confirm-time", 600);
        pauseOnLeave = config.getBoolean("prompt.pause-on-leave", true);
        confirmReward = config.getDouble("prompt.confirm-reward", 50);
        denyReward = config.getDouble("prompt.deny-reward", 25);
        autoConfirmReward = config.getDouble("prompt.auto-confirm-reward", 0);
        
        // Particles
        particlesEnabled = config.getBoolean("particles.enabled", true);
        try {
            particleType = Particle.valueOf(config.getString("particles.type", "DUST").toUpperCase());
        } catch (IllegalArgumentException e) {
            particleType = Particle.DUST;
        }
        int red = config.getInt("particles.color.red", 255);
        int green = config.getInt("particles.color.green", 215);
        int blue = config.getInt("particles.color.blue", 0);
        particleColor = Color.fromRGB(red, green, blue);
        particleSize = (float) config.getDouble("particles.size", 1.0);
        particleSpacing = config.getDouble("particles.spacing", 0.5);
        showParticlesDuringPrompt = config.getBoolean("particles.show-during-prompt", true);
        allowParticleToggle = config.getBoolean("particles.allow-toggle", true);
        
        // Combat
        combatEnabled = config.getBoolean("combat.enabled", true);
        combatRadius = config.getInt("combat.radius", 50);
        allowFightingWhenTagged = config.getBoolean("combat.allow-fighting-when-tagged", true);
        allowBlocksDuringCombat = config.getBoolean("combat.allow-blocks-during-combat", false);
        notifyPlayersAboutCombat = config.getBoolean("combat.notify-players", true);
        postCombatGrace = config.getInt("combat.post-combat-grace", 10);
        
        // Discord
        discordEnabled = config.getBoolean("discord.enabled", true);
        dmOnEntry = config.getBoolean("discord.dm-on-entry", true);
        allowTrustViaDM = config.getBoolean("discord.allow-trust-via-dm", true);
        trustKeyword = config.getString("discord.trust-keyword", "trust");
        
        // Excluded blocks
        excludedBlocks = new HashSet<>();
        List<String> excludedList = config.getStringList("excluded-blocks");
        for (String blockName : excludedList) {
            try {
                Material mat = Material.valueOf(blockName.toUpperCase());
                excludedBlocks.add(mat);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in excluded-blocks: " + blockName);
            }
        }
        
        // Dimension-specific exclusions
        dimensionExclusionsEnabled = config.getBoolean("dimension-exclusions.enabled", true);
        netherExcludedBlocks = loadMaterialSet("dimension-exclusions.nether");
        endExcludedBlocks = loadMaterialSet("dimension-exclusions.end");
        overworldExcludedBlocks = loadMaterialSet("dimension-exclusions.overworld");
        
        // Debug
        debugEnabled = config.getBoolean("debug.enabled", false);
        logInteractions = config.getBoolean("debug.log-interactions", false);
        logScoreChanges = config.getBoolean("debug.log-score-changes", false);
        logProtectionChecks = config.getBoolean("debug.log-protection-checks", false);
        
        // Messages
        messagePrefix = config.getString("messages.prefix", "&8[&6BaseDet&8] ");
        
        plugin.getLogger().info("Configuration loaded successfully");
    }
    
    // Scoring getters
    public double getBlockPlaceScore() {
        return config.getDouble("detection.scoring.block-place", 2.0);
    }
    
    public double getBlockBreakScore() {
        return config.getDouble("detection.scoring.block-break", 1.5);
    }
    
    public double getChestPlaceScore() {
        return config.getDouble("detection.scoring.chest-place", 10.0);
    }
    
    public double getCraftingTableScore() {
        return config.getDouble("detection.scoring.crafting-table", 8.0);
    }
    
    public double getFurnaceScore() {
        return config.getDouble("detection.scoring.furnace", 8.0);
    }
    
    public double getAnvilScore() {
        return config.getDouble("detection.scoring.anvil", 7.0);
    }
    
    public double getEnchantingTableScore() {
        return config.getDouble("detection.scoring.enchanting-table", 12.0);
    }
    
    public double getBedScore() {
        return config.getDouble("detection.scoring.bed", 20.0);
    }
    
    public double getDoorScore() {
        return config.getDouble("detection.scoring.door", 12.0);
    }
    
    public double getBarrelScore() {
        return config.getDouble("detection.scoring.barrel", 6.0);
    }
    
    public double getSmokerScore() {
        return config.getDouble("detection.scoring.smoker", 6.0);
    }
    
    public double getBlastFurnaceScore() {
        return config.getDouble("detection.scoring.blast-furnace", 6.0);
    }
    
    public double getShulkerBoxScore() {
        return config.getDouble("detection.scoring.shulker-box", 8.0);
    }
    
    public double getWalkingScore() {
        return config.getDouble("detection.scoring.walking", 0.1);
    }
    
    // Trust default permissions
    public boolean getTrustDefaultOnline(String permission) {
        return config.getBoolean("trust.default-online." + permission, true);
    }
    
    public boolean getTrustDefaultOffline(String permission) {
        return config.getBoolean("trust.default-offline." + permission, false);
    }
    
    // Protection settings
    public boolean isProtectedAction(String action) {
        return config.getBoolean("protection.protected-actions." + action, true);
    }
    
    // Expansion
    public boolean isExpansionEnabled() {
        return config.getBoolean("expansion.enabled", true);
    }
    
    public int getExpansionMinBlocks() {
        return config.getInt("expansion.min-blocks", 25);
    }
    
    public int getExpansionDistanceThreshold() {
        return config.getInt("expansion.distance-threshold", 15);
    }
    
    public double getExpansionReward() {
        return config.getDouble("expansion.expansion-reward", 0);
    }
    
    // Selector
    public Material getSelectorMaterial() {
        try {
            return Material.valueOf(config.getString("selector.material", "BLAZE_ROD").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.BLAZE_ROD;
        }
    }
    
    public String getSelectorName() {
        return config.getString("selector.name", "&6Base Selector Tool");
    }
    
    public double getSelectorCostPerBlock() {
        return config.getDouble("selector.cost-per-block", 5);
    }
    
    public boolean isShrinkFree() {
        return config.getBoolean("selector.shrink-free", true);
    }
    
    // MySQL settings
    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }
    
    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }
    
    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "wdp_basedet");
    }
    
    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }
    
    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }
    
    public int getPoolMaxSize() {
        return config.getInt("database.mysql.pool.maximum-pool-size", 10);
    }
    
    public int getPoolMinIdle() {
        return config.getInt("database.mysql.pool.minimum-idle", 5);
    }
    
    public long getPoolConnectionTimeout() {
        return config.getLong("database.mysql.pool.connection-timeout", 30000);
    }
    
    public long getPoolIdleTimeout() {
        return config.getLong("database.mysql.pool.idle-timeout", 600000);
    }
    
    public long getPoolMaxLifetime() {
        return config.getLong("database.mysql.pool.max-lifetime", 1800000);
    }
    
    // Message getters
    public String getMessage(String key) {
        String message = config.getString("messages." + key, "&cMessage not found: " + key);
        return translateColors(messagePrefix + message);
    }
    
    public String getRawMessage(String key) {
        return translateColors(config.getString("messages." + key, "&cMessage not found: " + key));
    }
    
    public String getDiscordMessage(String key) {
        return config.getString("discord.messages." + key, "Message not found: " + key);
    }
    
    private String translateColors(String message) {
        return message.replace("&", "§");
    }
    
    /**
     * Load a set of materials from a config list
     */
    private Set<Material> loadMaterialSet(String path) {
        Set<Material> materials = new HashSet<>();
        List<String> list = config.getStringList(path);
        for (String blockName : list) {
            try {
                Material mat = Material.valueOf(blockName.toUpperCase());
                materials.add(mat);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in " + path + ": " + blockName);
            }
        }
        return materials;
    }
    
    /**
     * Check if a block is excluded in a specific dimension
     * @param material The block material
     * @param environment The world environment (NORMAL, NETHER, THE_END)
     * @return true if the block should be excluded in this dimension
     */
    public boolean isDimensionExcluded(Material material, org.bukkit.World.Environment environment) {
        if (!dimensionExclusionsEnabled) {
            return false;
        }
        
        return switch (environment) {
            case NETHER -> netherExcludedBlocks.contains(material);
            case THE_END -> endExcludedBlocks.contains(material);
            case NORMAL -> overworldExcludedBlocks.contains(material);
            default -> false;
        };
    }
    
    // All cached getters
    public String getDatabaseType() { return databaseType; }
    public double getDetectionThreshold() { return detectionThreshold; }
    public int getDetectionCooldown() { return detectionCooldown; }
    public boolean isScoreDecayEnabled() { return scoreDecayEnabled; }
    public double getScoreDecayAmount() { return scoreDecayAmount; }
    public double getMinimumScore() { return minimumScore; }
    public boolean isProximityEnabled() { return proximityEnabled; }
    public int getProximityRadius() { return proximityRadius; }
    public double getProximityBonusMultiplier() { return proximityBonusMultiplier; }
    public double getProximityMaxBonus() { return proximityMaxBonus; }
    public int getMaxBaseWidth() { return maxBaseWidth; }
    public int getMaxBaseLength() { return maxBaseLength; }
    public int getMaxBaseHeight() { return maxBaseHeight; }
    public int getMinBaseWidth() { return minBaseWidth; }
    public int getMinBaseLength() { return minBaseLength; }
    public int getMinBaseHeight() { return minBaseHeight; }
    public int getHorizontalOffset() { return horizontalOffset; }
    public int getVerticalOffset() { return verticalOffset; }
    public int getViewHeightOffset() { return viewHeightOffset; }
    public boolean isAllowMultipleBases() { return allowMultipleBases; }
    public int getMaxBasesPerPlayer() { return maxBasesPerPlayer; }
    public int getMinBaseDistance() { return minBaseDistance; }
    public boolean isAutoAbandonOld() { return autoAbandonOld; }
    public int getAutoConfirmTime() { return autoConfirmTime; }
    public boolean isPauseOnLeave() { return pauseOnLeave; }
    public double getConfirmReward() { return confirmReward; }
    public double getDenyReward() { return denyReward; }
    public double getAutoConfirmReward() { return autoConfirmReward; }
    public boolean areParticlesEnabled() { return particlesEnabled; }
    public Particle getParticleType() { return particleType; }
    public Color getParticleColor() { return particleColor; }
    public float getParticleSize() { return particleSize; }
    public double getParticleSpacing() { return particleSpacing; }
    public boolean showParticlesDuringPrompt() { return showParticlesDuringPrompt; }
    public boolean isAllowParticleToggle() { return allowParticleToggle; }
    public boolean isCombatEnabled() { return combatEnabled; }
    public int getCombatRadius() { return combatRadius; }
    public boolean isAllowFightingWhenTagged() { return allowFightingWhenTagged; }
    public boolean isAllowBlocksDuringCombat() { return allowBlocksDuringCombat; }
    public boolean isNotifyPlayersAboutCombat() { return notifyPlayersAboutCombat; }
    public int getPostCombatGrace() { return postCombatGrace; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public boolean isDmOnEntry() { return dmOnEntry; }
    public boolean isAllowTrustViaDM() { return allowTrustViaDM; }
    public String getTrustKeyword() { return trustKeyword; }
    public Set<Material> getExcludedBlocks() { return excludedBlocks; }
    public boolean isDimensionExclusionsEnabled() { return dimensionExclusionsEnabled; }
    public Set<Material> getNetherExcludedBlocks() { return netherExcludedBlocks; }
    public Set<Material> getEndExcludedBlocks() { return endExcludedBlocks; }
    public Set<Material> getOverworldExcludedBlocks() { return overworldExcludedBlocks; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isLogInteractions() { return logInteractions; }
    public boolean isLogScoreChanges() { return logScoreChanges; }
    public boolean isLogProtectionChecks() { return logProtectionChecks; }
    public String getMessagePrefix() { return translateColors(messagePrefix); }
}
