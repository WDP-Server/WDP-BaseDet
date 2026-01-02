package com.wdp.basedet.detection;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.detection.LocationCluster.ClusterType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages location clusters for multi-location base tracking.
 * 
 * Key features:
 * - Track up to 5 separate location clusters per player
 * - Automatically start new cluster when player moves far from existing ones
 * - New cluster only becomes active after reaching score of 20
 * - Clusters expire after 4 hours of inactivity
 * - Never remove the highest-scoring cluster
 * - Smart detection to identify mining vs base-building activity
 */
public class ClusterManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    
    // Max clusters per player
    private static final int MAX_CLUSTERS = 5;
    
    // Distance threshold to start a new cluster (blocks)
    private static final int NEW_CLUSTER_DISTANCE = 200;
    
    // Minimum score to "activate" a new cluster
    private static final double CLUSTER_ACTIVATION_THRESHOLD = 20.0;
    
    // Expiry time for inactive clusters (4 hours in ms)
    private static final long CLUSTER_EXPIRY_MS = 4 * 60 * 60 * 1000;
    
    // Player clusters - maps player UUID to their location clusters
    private final Map<UUID, List<LocationCluster>> playerClusters = new ConcurrentHashMap<>();
    
    // Debug mode players - for /base debug toggle
    private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();
    
    public ClusterManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        
        // Start cleanup task
        startCleanupTask();
    }
    
    /**
     * Periodic cleanup of expired clusters
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            
            for (Map.Entry<UUID, List<LocationCluster>> entry : playerClusters.entrySet()) {
                List<LocationCluster> clusters = entry.getValue();
                if (clusters == null || clusters.isEmpty()) continue;
                
                // Find highest scoring cluster (never remove)
                LocationCluster highest = clusters.stream()
                        .max(Comparator.comparingDouble(LocationCluster::getScore))
                        .orElse(null);
                
                // Remove expired clusters (except highest)
                clusters.removeIf(cluster -> 
                    cluster != highest && 
                    cluster.isExpired(now, CLUSTER_EXPIRY_MS)
                );
            }
        }, 20 * 60 * 5, 20 * 60 * 5); // Every 5 minutes
    }
    
    /**
     * Get or create the appropriate cluster for an interaction
     */
    public LocationCluster getOrCreateCluster(UUID playerId, String world, int x, int y, int z) {
        List<LocationCluster> clusters = playerClusters.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Find existing cluster within range
        LocationCluster nearestCluster = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (LocationCluster cluster : clusters) {
            // Must be in same world
            if (!cluster.getWorld().equals(world)) continue;
            
            double distance = cluster.horizontalDistanceTo(x, z);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestCluster = cluster;
            }
        }
        
        // If we found a cluster within NEW_CLUSTER_DISTANCE, use it
        if (nearestCluster != null && nearestDistance < NEW_CLUSTER_DISTANCE) {
            // Update center with weighted average (move slightly towards new activity)
            nearestCluster.updateCenter(x, y, z);
            return nearestCluster;
        }
        
        // Need to create a new cluster
        // But first, check if we're at max clusters
        if (clusters.size() >= MAX_CLUSTERS) {
            // Find the cluster to remove (lowest score, except never remove highest)
            LocationCluster highest = clusters.stream()
                    .max(Comparator.comparingDouble(LocationCluster::getScore))
                    .orElse(null);
            
            LocationCluster toRemove = clusters.stream()
                    .filter(c -> c != highest)
                    .min(Comparator.comparingDouble(LocationCluster::getScore))
                    .orElse(null);
            
            if (toRemove != null) {
                clusters.remove(toRemove);
                
                // Debug notification
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && isDebugEnabled(playerId)) {
                    sendDebug(player, "&7[Cluster] Removed inactive cluster at " + 
                            toRemove.getLocationString() + " (score: " + 
                            String.format("%.1f", toRemove.getScore()) + ")");
                }
            }
        }
        
        // Create new cluster
        LocationCluster newCluster = new LocationCluster(playerId, world, x, y, z);
        clusters.add(newCluster);
        
        // Debug notification
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && isDebugEnabled(playerId)) {
            sendDebug(player, "&a[Cluster] Started new activity cluster at " + 
                    newCluster.getLocationString());
            sendDebug(player, "&7You now have " + clusters.size() + "/" + MAX_CLUSTERS + " active clusters");
        }
        
        return newCluster;
    }
    
    /**
     * Process an interaction and add score to the appropriate cluster
     */
    public void processInteraction(Player player, PlayerInteraction interaction) {
        UUID playerId = player.getUniqueId();
        LocationCluster cluster = getOrCreateCluster(
                playerId,
                interaction.getWorld(),
                interaction.getX(),
                interaction.getY(),
                interaction.getZ()
        );
        
        // Get base score from interaction
        double score = interaction.getScore();
        
        // Apply mining penalty if this looks like mining
        if (cluster.getType() == ClusterType.MINING) {
            score *= 0.1; // 90% reduction for mining activity
        } else if (cluster.getType() == ClusterType.HYBRID) {
            score *= 0.5; // 50% reduction for mixed activity
        }
        
        // Track the interaction type
        String blockType = interaction.getBlockType();
        if (interaction.getType() == PlayerInteraction.InteractionType.BLOCK_BREAK) {
            cluster.recordBlockBreak(
                    interaction.getX(), 
                    interaction.getY(), 
                    interaction.getZ(), 
                    blockType
            );
        } else if (interaction.getType() == PlayerInteraction.InteractionType.BLOCK_PLACE) {
            cluster.recordBlockPlace(blockType);
        }
        
        // Track base indicators
        if (blockType != null) {
            if (blockType.contains("_BED")) {
                cluster.recordBaseIndicator("BED");
            } else if (blockType.contains("_DOOR") || blockType.contains("_GATE")) {
                cluster.recordBaseIndicator("DOOR");
            } else if (blockType.contains("CHEST") || blockType.equals("BARREL")) {
                cluster.recordBaseIndicator("CHEST");
            } else if (blockType.equals("CRAFTING_TABLE") || blockType.equals("FURNACE") ||
                       blockType.equals("ANVIL") || blockType.equals("ENCHANTING_TABLE")) {
                cluster.recordBaseIndicator("CRAFTING");
            }
        }
        
        // Add score to cluster
        cluster.addScore(score);
        
        // Debug output
        if (isDebugEnabled(playerId)) {
            String typeColor = switch (cluster.getType()) {
                case BASE -> "&a";
                case MINING -> "&c";
                case HYBRID -> "&e";
                case UNKNOWN -> "&7";
            };
            
            sendDebug(player, String.format(
                    "&7[Score] %s +%.2f &7at cluster %s[%s]&7 (total: %.1f)",
                    interaction.getType().name(),
                    score,
                    typeColor,
                    cluster.getType().name(),
                    cluster.getScore()
            ));
            
            // Show detailed mining stats if MINING or HYBRID
            if (cluster.getType() == ClusterType.MINING || cluster.getType() == ClusterType.HYBRID) {
                sendDebug(player, String.format(
                        "  &7Mining stats: broken=%d, placed=%d, ores=%d",
                        cluster.getBlocksBroken(),
                        cluster.getBlocksPlaced(),
                        cluster.getOresBroken()
                ));
            }
        }
        
        // Check if cluster is ready for detection
        if (cluster.getScore() >= config.getDetectionThreshold() && 
            cluster.getType() != ClusterType.MINING) {
            
            // Only trigger if cluster is activated (past initial threshold)
            if (cluster.getScore() >= CLUSTER_ACTIVATION_THRESHOLD) {
                plugin.getDetectionManager().checkDetectionForCluster(player, cluster);
            }
        }
    }
    
    /**
     * Get all clusters for a player
     */
    public List<LocationCluster> getClusters(UUID playerId) {
        return playerClusters.getOrDefault(playerId, new ArrayList<>());
    }
    
    /**
     * Get the highest scoring cluster for a player
     */
    public LocationCluster getHighestScoringCluster(UUID playerId) {
        List<LocationCluster> clusters = getClusters(playerId);
        if (clusters.isEmpty()) return null;
        
        return clusters.stream()
                .max(Comparator.comparingDouble(LocationCluster::getScore))
                .orElse(null);
    }
    
    /**
     * Get only BASE-type clusters for a player
     */
    public List<LocationCluster> getBaseClusters(UUID playerId) {
        return getClusters(playerId).stream()
                .filter(c -> c.getType() == ClusterType.BASE || c.getType() == ClusterType.UNKNOWN)
                .filter(c -> c.getScore() >= CLUSTER_ACTIVATION_THRESHOLD)
                .collect(Collectors.toList());
    }
    
    /**
     * Get total score across all BASE clusters
     */
    public double getTotalBaseScore(UUID playerId) {
        return getBaseClusters(playerId).stream()
                .mapToDouble(LocationCluster::getScore)
                .sum();
    }
    
    /**
     * Clear a specific cluster (after base confirmation)
     */
    public void clearCluster(UUID playerId, LocationCluster cluster) {
        List<LocationCluster> clusters = playerClusters.get(playerId);
        if (clusters != null) {
            clusters.remove(cluster);
        }
    }
    
    /**
     * Clear all clusters for a player
     */
    public void clearAllClusters(UUID playerId) {
        playerClusters.remove(playerId);
    }
    
    // ==================== Debug Mode ====================
    
    /**
     * Toggle debug mode for a player
     */
    public boolean toggleDebug(UUID playerId) {
        if (debugPlayers.contains(playerId)) {
            debugPlayers.remove(playerId);
            return false;
        } else {
            debugPlayers.add(playerId);
            return true;
        }
    }
    
    /**
     * Check if debug is enabled for a player
     */
    public boolean isDebugEnabled(UUID playerId) {
        return debugPlayers.contains(playerId);
    }
    
    /**
     * Send a debug message to a player
     */
    private void sendDebug(Player player, String message) {
        player.sendMessage(colorize("&8[BaseDet] " + message));
    }
    
    private String colorize(String message) {
        return message.replace("&", "§");
    }
    
    // ==================== Player Events ====================
    
    /**
     * Called when player joins
     */
    public void onPlayerJoin(Player player) {
        // Could load clusters from database here if persisted
    }
    
    /**
     * Called when player quits
     */
    public void onPlayerQuit(Player player) {
        debugPlayers.remove(player.getUniqueId());
        // Could save clusters to database here if persisted
    }
    
    /**
     * Apply decay to all clusters
     */
    public void decayAllClusters(double decayAmount) {
        for (List<LocationCluster> clusters : playerClusters.values()) {
            for (LocationCluster cluster : clusters) {
                double newScore = Math.max(0, cluster.getScore() - decayAmount);
                cluster.setScore(newScore);
            }
        }
    }
}
