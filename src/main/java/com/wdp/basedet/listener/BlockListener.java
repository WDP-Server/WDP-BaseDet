package com.wdp.basedet.listener;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.detection.ClusterManager;
import com.wdp.basedet.detection.PlayerInteraction;
import com.wdp.basedet.detection.PlayerInteraction.InteractionType;
import com.wdp.basedet.detection.ScoreManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Listens for block events to track player activity for base detection.
 * 
 * Updated to use ClusterManager for multi-location tracking and smart mining detection.
 */
public class BlockListener implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final ScoreManager scoreManager;
    private final ClusterManager clusterManager;
    
    public BlockListener(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.scoreManager = plugin.getScoreManager();
        this.clusterManager = plugin.getClusterManager();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        
        // Check if block is excluded globally
        if (scoreManager.isExcludedBlock(material)) {
            return;
        }
        
        // Check if block is excluded in this dimension (e.g., beds in nether)
        if (scoreManager.isDimensionExcluded(material, block.getWorld().getEnvironment())) {
            if (plugin.getConfigManager().isLogInteractions()) {
                plugin.debug(String.format("Block excluded in dimension: %s placed %s in %s",
                        player.getName(), material.name(), block.getWorld().getEnvironment()));
            }
            return;
        }
        
        // Get score for this block type
        double score = scoreManager.getBlockPlaceScore(material);
        
        // Determine interaction type
        InteractionType type = getInteractionType(material, true);
        
        // Create interaction record
        PlayerInteraction interaction = new PlayerInteraction(
                player.getUniqueId(),
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                type,
                material.name(),
                score,
                System.currentTimeMillis()
        );
        
        // Add score via legacy ScoreManager (for compatibility)
        scoreManager.addScore(player, interaction);
        
        // Process via ClusterManager for multi-location tracking
        if (clusterManager != null) {
            clusterManager.processInteraction(player, interaction);
        }
        
        // Track for expansion detection
        if (plugin.getExpansionManager() != null) {
            plugin.getExpansionManager().trackInteraction(player.getUniqueId(), interaction);
        }
        
        // Log if enabled
        if (plugin.getConfigManager().isLogInteractions()) {
            plugin.debug(String.format("Block place: %s placed %s at %d,%d,%d (score: %.2f)",
                    player.getName(), material.name(), 
                    block.getX(), block.getY(), block.getZ(), score));
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        
        // Check if block is excluded globally
        if (scoreManager.isExcludedBlock(material)) {
            return;
        }
        
        // Check if block is excluded in this dimension
        if (scoreManager.isDimensionExcluded(material, block.getWorld().getEnvironment())) {
            return;
        }
        
        // Get score for breaking
        double score = scoreManager.getBlockBreakScore(material);
        
        // Create interaction record
        PlayerInteraction interaction = new PlayerInteraction(
                player.getUniqueId(),
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                InteractionType.BLOCK_BREAK,
                material.name(),
                score,
                System.currentTimeMillis()
        );
        
        // Add score via legacy ScoreManager (for compatibility)
        scoreManager.addScore(player, interaction);
        
        // Process via ClusterManager for multi-location tracking and smart mining detection
        if (clusterManager != null) {
            clusterManager.processInteraction(player, interaction);
        }
        
        // Log if enabled
        if (plugin.getConfigManager().isLogInteractions()) {
            plugin.debug(String.format("Block break: %s broke %s at %d,%d,%d (score: %.2f)",
                    player.getName(), material.name(), 
                    block.getX(), block.getY(), block.getZ(), score));
        }
    }
    
    /**
     * Determine the interaction type based on block material
     */
    private InteractionType getInteractionType(Material material, boolean isPlace) {
        if (!isPlace) {
            return InteractionType.BLOCK_BREAK;
        }
        
        String name = material.name();
        
        // Chest and storage
        if (name.contains("CHEST") || material == Material.BARREL || name.contains("SHULKER_BOX")) {
            return InteractionType.CHEST_PLACE;
        }
        
        // Doors and gates (essential for bases!)
        if (name.contains("_DOOR") || name.contains("_GATE") || name.contains("_TRAPDOOR")) {
            return InteractionType.DOOR_PLACE;
        }
        
        // Beds (essential for bases!)
        if (name.contains("_BED")) {
            return InteractionType.BED_PLACE;
        }
        
        // Workbenches and crafting stations
        if (material == Material.CRAFTING_TABLE ||
            material == Material.FURNACE ||
            material == Material.BLAST_FURNACE ||
            material == Material.SMOKER ||
            material == Material.ENCHANTING_TABLE ||
            material == Material.ANVIL ||
            material == Material.CHIPPED_ANVIL ||
            material == Material.DAMAGED_ANVIL ||
            material == Material.SMITHING_TABLE ||
            material == Material.LOOM ||
            material == Material.CARTOGRAPHY_TABLE ||
            material == Material.GRINDSTONE ||
            material == Material.STONECUTTER ||
            material == Material.FLETCHING_TABLE ||
            material == Material.BREWING_STAND ||
            material == Material.CAULDRON) {
            return InteractionType.WORKBENCH_PLACE;
        }
        
        return InteractionType.BLOCK_PLACE;
    }
}
