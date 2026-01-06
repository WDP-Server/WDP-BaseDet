package com.wdp.basedet.detection;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.config.MessageManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages base detection logic and prompts
 * 
 * API Methods for external plugins:
 * - isLocationNearBase(World, int x, int z, int distance) - Check if location is near any base
 * - getAllBases() - Get all detected bases
 * - getBasesInWorld(String worldName) - Get bases in a specific world
 * - getNearbyBases(Location, int radius) - Get bases near a location
 */
public class DetectionManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    
    // Players currently in detection prompt
    private final Map<UUID, DetectionPrompt> activePrompts = new ConcurrentHashMap<>();
    
    // Cooldowns for detection
    private final Map<UUID, Long> detectionCooldowns = new ConcurrentHashMap<>();
    
    public DetectionManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessages();
    }
    
    // ==================== PUBLIC API METHODS ====================
    
    /**
     * API: Check if a location is near any detected base
     * Used by external plugins like WDP-Start for RTP safety checks
     * 
     * @param world The world to check in
     * @param x X coordinate
     * @param z Z coordinate
     * @param minDistance Minimum distance from any base
     * @return true if location is within minDistance of any base
     */
    public boolean isLocationNearBase(World world, int x, int z, int minDistance) {
        if (world == null) return false;
        
        List<Base> bases = plugin.getDatabaseManager().getAllBases(world.getName());
        if (bases == null || bases.isEmpty()) return false;
        
        for (Base base : bases) {
            BoundingBox bounds = base.getBounds();
            if (bounds == null) continue;
            
            // Calculate distance from point to bounding box
            int dx = Math.max(0, Math.max(bounds.getMinX() - x, x - bounds.getMaxX()));
            int dz = Math.max(0, Math.max(bounds.getMinZ() - z, z - bounds.getMaxZ()));
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance < minDistance) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * API: Get all detected bases
     * 
     * @return List of all bases
     */
    public List<Base> getAllBases() {
        return plugin.getDatabaseManager().getAllBases();
    }
    
    /**
     * API: Get all bases in a specific world
     * 
     * @param worldName The world name
     * @return List of bases in that world
     */
    public List<Base> getBasesInWorld(String worldName) {
        return plugin.getDatabaseManager().getAllBases(worldName);
    }
    
    /**
     * API: Get bases near a specific location
     * 
     * @param location Center location
     * @param radius Search radius
     * @return List of bases within radius
     */
    public List<Base> getNearbyBases(Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return Collections.emptyList();
        }
        
        List<Base> allBases = plugin.getDatabaseManager().getAllBases(location.getWorld().getName());
        if (allBases == null || allBases.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Base> nearby = new ArrayList<>();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        for (Base base : allBases) {
            BoundingBox bounds = base.getBounds();
            if (bounds == null) continue;
            
            // Calculate distance from point to bounding box center
            int centerX = (bounds.getMinX() + bounds.getMaxX()) / 2;
            int centerZ = (bounds.getMinZ() + bounds.getMaxZ()) / 2;
            double distance = Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerZ - z, 2));
            
            if (distance <= radius) {
                nearby.add(base);
            }
        }
        
        return nearby;
    }
    
    /**
     * API: Get the closest base to a location
     * 
     * @param location The location to check from
     * @return The closest base, or null if no bases exist
     */
    public Base getClosestBase(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        
        List<Base> allBases = plugin.getDatabaseManager().getAllBases(location.getWorld().getName());
        if (allBases == null || allBases.isEmpty()) {
            return null;
        }
        
        Base closest = null;
        double closestDistance = Double.MAX_VALUE;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        for (Base base : allBases) {
            BoundingBox bounds = base.getBounds();
            if (bounds == null) continue;
            
            int dx = Math.max(0, Math.max(bounds.getMinX() - x, x - bounds.getMaxX()));
            int dz = Math.max(0, Math.max(bounds.getMinZ() - z, z - bounds.getMaxZ()));
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = base;
            }
        }
        
        return closest;
    }
    
    /**
     * API: Get distance to the nearest base
     * 
     * @param location The location to check from
     * @return Distance in blocks, or -1 if no bases exist
     */
    public double getDistanceToNearestBase(Location location) {
        if (location == null || location.getWorld() == null) {
            return -1;
        }
        
        List<Base> allBases = plugin.getDatabaseManager().getAllBases(location.getWorld().getName());
        if (allBases == null || allBases.isEmpty()) {
            return -1;
        }
        
        double closestDistance = Double.MAX_VALUE;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        for (Base base : allBases) {
            BoundingBox bounds = base.getBounds();
            if (bounds == null) continue;
            
            int dx = Math.max(0, Math.max(bounds.getMinX() - x, x - bounds.getMaxX()));
            int dz = Math.max(0, Math.max(bounds.getMinZ() - z, z - bounds.getMaxZ()));
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance < closestDistance) {
                closestDistance = distance;
            }
        }
        
        return closestDistance == Double.MAX_VALUE ? -1 : closestDistance;
    }
    
    // ==================== INTERNAL METHODS ====================
    
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
     * Check detection for a specific location cluster (used by ClusterManager)
     * This allows multi-location tracking with separate scores per location.
     */
    public void checkDetectionForCluster(Player player, LocationCluster cluster) {
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
        
        // Don't trigger for mining clusters
        if (cluster.getType() == LocationCluster.ClusterType.MINING) {
            plugin.debug("Skipping detection for mining cluster: " + cluster.getLocationString());
            return;
        }
        
        // Check if already has max bases
        List<Base> existingBases = plugin.getDatabaseManager().getPlayerBases(uuid);
        if (existingBases.size() >= config.getMaxBasesPerPlayer() && !config.isAutoAbandonOld()) {
            return;
        }
        
        // Create bounds from cluster center
        // Use a default radius for the initial bounds
        int radius = config.getMinBaseWidth() / 2;
        int height = config.getMinBaseHeight();
        
        BoundingBox bounds = new BoundingBox(
                cluster.getCenterX() - radius,
                cluster.getCenterY() - height / 2,
                cluster.getCenterZ() - radius,
                cluster.getCenterX() + radius,
                cluster.getCenterY() + height / 2,
                cluster.getCenterZ() + radius
        );
        
        // Apply offsets
        bounds.expandBy(config.getHorizontalOffset(), config.getVerticalOffset());
        
        // Check minimum distance from existing bases
        if (!checkMinimumDistance(player, bounds, existingBases)) {
            return;
        }
        
        // Debug notification if enabled
        if (plugin.getClusterManager().isDebugEnabled(uuid)) {
            player.sendMessage("§8[BaseDet] §aDetection triggered! Cluster type: " + cluster.getType());
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
            messages.send(player, "commands.not-enough-for-detection");
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
        // Find the centroid (X and Z only, for horizontal radius calculation)
        double sumX = 0, sumZ = 0;
        for (PlayerInteraction i : interactions) {
            sumX += i.getX();
            sumZ += i.getZ();
        }
        int centerX = (int) (sumX / interactions.size());
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
                messages.send(player, "detection.too-close-to-base");
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
        messages.send(player, "detection.base-detected");
        messages.sendRaw(player, "detection.location-info", "location", pendingBase.getLocationString());
        messages.sendRaw(player, "detection.size-info", "size", pendingBase.getDimensionsString());
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
        messages.sendRaw(player, "detection.auto-confirm-timer", "minutes", String.valueOf(config.getAutoConfirmTime() / 60));
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
                messages.send(player, "detection.old-base-abandoned");
            }
        }
        
        // Confirm the base
        plugin.getDatabaseManager().confirmBase(base.getId());
        base.setConfirmed(true);
        
        // Clear score and reset score limits
        plugin.getScoreManager().clearScore(uuid);
        plugin.getScoreManager().resetScoreLimits(uuid);
        
        // Give reward
        double reward = manual ? config.getConfirmReward() : config.getAutoConfirmReward();
        if (reward > 0 && plugin.getEconomyIntegration() != null) {
            plugin.getEconomyIntegration().deposit(player, reward);
            String rewardPath = manual ? "detection.reward-confirm" : "detection.reward-auto-confirm";
            messages.send(player, rewardPath, "amount", String.format("%.0f", reward));
        }
        
        // Send confirmation message with size info
        BoundingBox bounds = base.getBounds();
        int volume = bounds.getWidth() * bounds.getLength() * bounds.getHeight();
        
        String confirmType = manual ? messages.get("detection.base-confirmed-manual") : messages.get("detection.base-confirmed-auto");
        player.sendMessage("");
        messages.send(player, "detection.base-confirmed", "type", confirmType);
        messages.sendRaw(player, "detection.size-confirmed", 
                "dimensions", bounds.getWidth() + "×" + bounds.getLength() + "×" + bounds.getHeight(),
                "volume", String.format("%,d", volume));
        messages.sendRaw(player, "detection.protection-info");
        messages.sendRaw(player, "detection.trust-info");
        player.sendMessage("");
        
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
        
        // IMPORTANT: Reset score to 0 when denying to prevent re-triggering
        plugin.getScoreManager().clearScore(uuid);
        plugin.getScoreManager().resetScoreLimits(uuid);
        
        // Also clear the cluster that triggered this detection
        plugin.getClusterManager().clearAllClusters(uuid);
        
        // Give small reward for denying
        double reward = config.getDenyReward();
        if (reward > 0 && plugin.getEconomyIntegration() != null) {
            plugin.getEconomyIntegration().deposit(player, reward);
            messages.send(player, "detection.reward-deny", "amount", String.format("%.0f", reward));
        }
        
        // Send message
        messages.send(player, "detection.base-denied");
        
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
            messages.send(player, "detection.pending-detection-reminder");
            
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
                    messages.send(player, "detection.auto-confirm-warning", "time", formatTime(remainingSeconds));
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
