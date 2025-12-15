package com.wdp.basedet.detection;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages base detection logic and prompts
 */
public class DetectionManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    
    // Players currently in detection prompt
    private final Map<UUID, DetectionPrompt> activePrompts = new ConcurrentHashMap<>();
    
    // Cooldowns for detection
    private final Map<UUID, Long> detectionCooldowns = new ConcurrentHashMap<>();
    
    public DetectionManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    /**
     * Check if a player has reached detection threshold
     */
    public void checkDetection(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Check if already in prompt
        if (activePrompts.containsKey(uuid)) {
            return;
        }
        
        // Check cooldown
        Long cooldownEnd = detectionCooldowns.get(uuid);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            return;
        }
        
        // Check if already has max bases
        List<Base> existingBases = plugin.getDatabaseManager().getPlayerBases(uuid);
        if (existingBases.size() >= config.getMaxBasesPerPlayer() && !config.isAutoAbandonOld()) {
            return;
        }
        
        // Analyze interactions to calculate base bounds
        BoundingBox bounds = analyzeInteractions(uuid);
        if (bounds == null) {
            plugin.debug("No valid bounds calculated for " + player.getName());
            return;
        }
        
        // Check minimum distance from existing bases
        if (!checkMinimumDistance(player, bounds, existingBases)) {
            return;
        }
        
        // Start detection prompt
        startDetectionPrompt(player, bounds);
    }
    
    /**
     * Manually trigger detection for a player
     */
    public void triggerManualDetection(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Clear any existing prompt
        cancelPrompt(uuid);
        
        // Analyze interactions
        BoundingBox bounds = analyzeInteractions(uuid);
        if (bounds == null) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Not enough activity detected to determine a base location.");
            return;
        }
        
        // Start prompt
        startDetectionPrompt(player, bounds);
    }
    
    /**
     * Analyze player interactions to determine base bounds
     */
    private BoundingBox analyzeInteractions(UUID uuid) {
        List<PlayerInteraction> interactions = plugin.getScoreManager().getRecentInteractions(uuid);
        
        if (interactions.isEmpty()) {
            // Try loading from database
            interactions = plugin.getDatabaseManager().getPlayerInteractions(uuid);
        }
        
        if (interactions.isEmpty()) {
            return null;
        }
        
        // Filter to only block-related interactions (not walking)
        List<PlayerInteraction> blockInteractions = interactions.stream()
                .filter(i -> i.getType() != PlayerInteraction.InteractionType.WALKING)
                .toList();
        
        if (blockInteractions.isEmpty()) {
            return null;
        }
        
        // Find the most common world
        Map<String, Integer> worldCounts = new HashMap<>();
        for (PlayerInteraction interaction : blockInteractions) {
            worldCounts.merge(interaction.getWorld(), 1, Integer::sum);
        }
        
        String primaryWorld = worldCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        
        if (primaryWorld == null) {
            return null;
        }
        
        // Filter to primary world
        List<PlayerInteraction> worldInteractions = blockInteractions.stream()
                .filter(i -> i.getWorld().equals(primaryWorld))
                .toList();
        
        if (worldInteractions.size() < 3) {
            return null; // Need at least 3 interactions
        }
        
        // Cluster detection - find the densest cluster of interactions
        BoundingBox cluster = findDensestCluster(worldInteractions);
        if (cluster == null) {
            return null;
        }
        
        // Apply offsets
        cluster.expandBy(config.getHorizontalOffset(), config.getVerticalOffset());
        
        // Enforce size limits
        cluster.clampToMax(config.getMaxBaseWidth(), config.getMaxBaseLength(), config.getMaxBaseHeight());
        cluster.ensureMinimum(config.getMinBaseWidth(), config.getMinBaseLength(), config.getMinBaseHeight());
        
        return cluster;
    }
    
    /**
     * Find the densest cluster of interactions
     */
    private BoundingBox findDensestCluster(List<PlayerInteraction> interactions) {
        if (interactions.isEmpty()) {
            return null;
        }
        
        // Use a simple approach: start with bounds of all interactions, then shrink to remove outliers
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (PlayerInteraction interaction : interactions) {
            minX = Math.min(minX, interaction.getX());
            maxX = Math.max(maxX, interaction.getX());
            minY = Math.min(minY, interaction.getY());
            maxY = Math.max(maxY, interaction.getY());
            minZ = Math.min(minZ, interaction.getZ());
            maxZ = Math.max(maxZ, interaction.getZ());
        }
        
        // Check if bounds are reasonable before applying limits
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        
        // If bounds are way too big, use clustering to find core
        if (width > config.getMaxBaseWidth() * 2 || length > config.getMaxBaseLength() * 2) {
            return findCoreBounds(interactions);
        }
        
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Find core bounds when interactions are spread too far
     */
    private BoundingBox findCoreBounds(List<PlayerInteraction> interactions) {
        // Find the centroid
        double sumX = 0, sumY = 0, sumZ = 0;
        for (PlayerInteraction i : interactions) {
            sumX += i.getX();
            sumY += i.getY();
            sumZ += i.getZ();
        }
        int centerX = (int) (sumX / interactions.size());
        int centerY = (int) (sumY / interactions.size());
        int centerZ = (int) (sumZ / interactions.size());
        
        // Filter interactions within a reasonable radius of centroid
        int radius = config.getMaxBaseWidth() / 2;
        List<PlayerInteraction> coreInteractions = interactions.stream()
                .filter(i -> Math.abs(i.getX() - centerX) <= radius &&
                            Math.abs(i.getZ() - centerZ) <= radius)
                .toList();
        
        if (coreInteractions.isEmpty()) {
            return null;
        }
        
        // Recalculate bounds with core interactions
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (PlayerInteraction interaction : coreInteractions) {
            minX = Math.min(minX, interaction.getX());
            maxX = Math.max(maxX, interaction.getX());
            minY = Math.min(minY, interaction.getY());
            maxY = Math.max(maxY, interaction.getY());
            minZ = Math.min(minZ, interaction.getZ());
            maxZ = Math.max(maxZ, interaction.getZ());
        }
        
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Check minimum distance from existing bases
     */
    private boolean checkMinimumDistance(Player player, BoundingBox newBounds, List<Base> existingBases) {
        int minDistance = config.getMinBaseDistance();
        
        for (Base base : existingBases) {
            BoundingBox existing = base.getBounds();
            double distance = calculateDistance(newBounds, existing);
            
            if (distance < minDistance) {
                player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                        "This location is too close to one of your existing bases.");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Calculate distance between two bounding boxes
     */
    private double calculateDistance(BoundingBox a, BoundingBox b) {
        int dx = Math.max(0, Math.max(a.getMinX() - b.getMaxX(), b.getMinX() - a.getMaxX()));
        int dz = Math.max(0, Math.max(a.getMinZ() - b.getMaxZ(), b.getMinZ() - a.getMaxZ()));
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Start the detection prompt for a player
     */
    private void startDetectionPrompt(Player player, BoundingBox bounds) {
        UUID uuid = player.getUniqueId();
        
        // Create pending base in database
        Base pendingBase = plugin.getDatabaseManager().createBase(
                uuid,
                player.getWorld().getName(),
                bounds
        );
        
        if (pendingBase == null) {
            plugin.getLogger().warning("Failed to create pending base for " + player.getName());
            return;
        }
        
        // Create prompt
        DetectionPrompt prompt = new DetectionPrompt(player, pendingBase, config.getAutoConfirmTime());
        activePrompts.put(uuid, prompt);
        
        // Show particles
        if (config.showParticlesDuringPrompt()) {
            plugin.getParticleManager().startShowingBounds(uuid, bounds, player.getWorld().getName());
        }
        
        // Send message with clickable buttons
        player.sendMessage("");
        player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + "⬢ Base Detected!");
        player.sendMessage(ChatColor.GRAY + "  Location: " + ChatColor.WHITE + pendingBase.getLocationString());
        player.sendMessage(ChatColor.GRAY + "  Size: " + ChatColor.WHITE + pendingBase.getDimensionsString() + " blocks");
        player.sendMessage("");
        
        // Create clickable buttons using Paper's Component API
        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text("  ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text("[✓ Confirm]", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/basedet confirm"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Click to confirm this base", net.kyori.adventure.text.format.NamedTextColor.GREEN))))
                .append(net.kyori.adventure.text.Component.text("  ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text("[✗ Deny]", net.kyori.adventure.text.format.NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/basedet deny"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Click to deny this base", net.kyori.adventure.text.format.NamedTextColor.RED))))
                .build();
        
        player.sendMessage(message);
        player.sendMessage(ChatColor.GRAY + "  Auto-confirms in " + (config.getAutoConfirmTime() / 60) + " minutes");
        player.sendMessage("");
        
        // Start auto-confirm timer
        prompt.startTimer();
        
        // Set cooldown
        detectionCooldowns.put(uuid, System.currentTimeMillis() + (config.getDetectionCooldown() * 1000L));
    }
    
    /**
     * Confirm a detected base
     */
    public boolean confirmBase(Player player, boolean manual) {
        UUID uuid = player.getUniqueId();
        DetectionPrompt prompt = activePrompts.get(uuid);
        
        if (prompt == null) {
            return false;
        }
        
        Base base = prompt.getBase();
        
        // Check if we need to abandon old base
        List<Base> existingBases = plugin.getDatabaseManager().getPlayerBases(uuid);
        if (existingBases.size() >= config.getMaxBasesPerPlayer() && config.isAutoAbandonOld()) {
            // Delete oldest base
            Base oldest = existingBases.stream()
                    .min(Comparator.comparingLong(Base::getCreatedAt))
                    .orElse(null);
            if (oldest != null) {
                plugin.getDatabaseManager().deleteBase(oldest.getId());
                player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                        "Your oldest base has been abandoned to make room for this one.");
            }
        }
        
        // Confirm the base
        plugin.getDatabaseManager().confirmBase(base.getId());
        base.setConfirmed(true);
        
        // Clear score
        plugin.getScoreManager().clearScore(uuid);
        
        // Give reward
        double reward = manual ? config.getConfirmReward() : config.getAutoConfirmReward();
        if (reward > 0 && plugin.getEconomyIntegration() != null) {
            plugin.getEconomyIntegration().deposit(player, reward);
            player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                    "You received " + ChatColor.GOLD + reward + " SkillCoins" + 
                    ChatColor.GREEN + " for " + (manual ? "confirming" : "auto-confirming") + " your base!");
        }
        
        // Send confirmation message
        player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                "Your base has been " + (manual ? "confirmed" : "auto-confirmed") + "!");
        player.sendMessage(ChatColor.GRAY + "  Your base is now protected when you're offline.");
        player.sendMessage(ChatColor.GRAY + "  Use " + ChatColor.WHITE + "/trust" + 
                ChatColor.GRAY + " to manage trusted players.");
        
        // Cleanup
        cleanupPrompt(uuid);
        
        return true;
    }
    
    /**
     * Deny a detected base
     */
    public boolean denyBase(Player player) {
        UUID uuid = player.getUniqueId();
        DetectionPrompt prompt = activePrompts.get(uuid);
        
        if (prompt == null) {
            return false;
        }
        
        // Delete the pending base
        plugin.getDatabaseManager().deleteBase(prompt.getBase().getId());
        
        // Give small reward for denying
        double reward = config.getDenyReward();
        if (reward > 0 && plugin.getEconomyIntegration() != null) {
            plugin.getEconomyIntegration().deposit(player, reward);
            player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                    "You received " + ChatColor.GOLD + reward + " SkillCoins" + 
                    ChatColor.GREEN + " for responding to the detection prompt.");
        }
        
        // Send message
        player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                "Base detection cancelled. Keep building!");
        
        // Cleanup
        cleanupPrompt(uuid);
        
        return true;
    }
    
    /**
     * Cancel a prompt without confirmation
     */
    public void cancelPrompt(UUID uuid) {
        DetectionPrompt prompt = activePrompts.get(uuid);
        if (prompt != null) {
            plugin.getDatabaseManager().deleteBase(prompt.getBase().getId());
            cleanupPrompt(uuid);
        }
    }
    
    /**
     * Cleanup prompt resources
     */
    private void cleanupPrompt(UUID uuid) {
        DetectionPrompt prompt = activePrompts.remove(uuid);
        if (prompt != null) {
            prompt.cancelTimer();
        }
        plugin.getParticleManager().stopShowingBounds(uuid);
    }
    
    /**
     * Check if player has an active prompt
     */
    public boolean hasActivePrompt(UUID uuid) {
        return activePrompts.containsKey(uuid);
    }
    
    /**
     * Get active prompt for player
     */
    public DetectionPrompt getActivePrompt(UUID uuid) {
        return activePrompts.get(uuid);
    }
    
    /**
     * Called when player quits - pause timer
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        DetectionPrompt prompt = activePrompts.get(uuid);
        
        if (prompt != null && config.isPauseOnLeave()) {
            prompt.pauseTimer();
        }
    }
    
    /**
     * Called when player joins - resume timer
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        DetectionPrompt prompt = activePrompts.get(uuid);
        
        if (prompt != null) {
            prompt.resumeTimer();
            
            // Remind player about pending detection
            player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                    "You have a pending base detection! Use " + ChatColor.WHITE + 
                    "/basedet confirm" + ChatColor.YELLOW + " or " + ChatColor.WHITE + "/basedet deny");
            
            // Re-show particles
            if (config.showParticlesDuringPrompt()) {
                plugin.getParticleManager().startShowingBounds(
                        uuid, 
                        prompt.getBase().getBounds(), 
                        prompt.getBase().getWorldName()
                );
            }
        }
    }
    
    /**
     * Inner class representing an active detection prompt
     */
    public class DetectionPrompt {
        private final Player player;
        private final Base base;
        private final int totalSeconds;
        private int remainingSeconds;
        private BukkitTask timerTask;
        private boolean paused;
        
        public DetectionPrompt(Player player, Base base, int seconds) {
            this.player = player;
            this.base = base;
            this.totalSeconds = seconds;
            this.remainingSeconds = seconds;
            this.paused = false;
        }
        
        public void startTimer() {
            timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (paused) return;
                
                remainingSeconds--;
                
                // Notify at certain intervals
                if (remainingSeconds == 300 || remainingSeconds == 60 || remainingSeconds == 30) {
                    player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                            "Base will auto-confirm in " + formatTime(remainingSeconds));
                }
                
                if (remainingSeconds <= 0) {
                    // Auto-confirm
                    confirmBase(player, false);
                }
            }, 20L, 20L); // Every second
        }
        
        public void pauseTimer() {
            paused = true;
        }
        
        public void resumeTimer() {
            paused = false;
        }
        
        public void cancelTimer() {
            if (timerTask != null) {
                timerTask.cancel();
            }
        }
        
        public Player getPlayer() { return player; }
        public Base getBase() { return base; }
        public int getRemainingSeconds() { return remainingSeconds; }
        public boolean isPaused() { return paused; }
        
        private String formatTime(int seconds) {
            if (seconds >= 60) {
                int minutes = seconds / 60;
                int secs = seconds % 60;
                return minutes + " minute" + (minutes != 1 ? "s" : "") + 
                       (secs > 0 ? " and " + secs + " seconds" : "");
            }
            return seconds + " seconds";
        }
    }
}
