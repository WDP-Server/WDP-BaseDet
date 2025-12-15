package com.wdp.basedet.listener;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.TrustEntry;
import com.wdp.basedet.protection.ProtectionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Listens for protection-related events
 */
public class ProtectionListener implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final ProtectionManager protectionManager;
    
    public ProtectionListener(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.protectionManager = plugin.getProtectionManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Check if player can break here
        if (!protectionManager.canBreak(player, block.getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Check if player can place here
        if (!protectionManager.canPlace(player, block.getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();
        
        // Determine interaction type
        String action = getInteractionType(material);
        if (action == null) {
            return; // Not a protected interaction
        }
        
        // Check if player can interact
        if (!protectionManager.canInteract(player, block.getLocation(), action)) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }
    
    /**
     * Get the type of interaction for a block
     */
    private String getInteractionType(Material material) {
        String name = material.name();
        
        // Chests and containers
        if (name.contains("CHEST") || material == Material.BARREL || 
            name.contains("SHULKER_BOX") || material == Material.HOPPER ||
            material == Material.DROPPER || material == Material.DISPENSER) {
            return "chest";
        }
        
        // Doors
        if (name.contains("_DOOR") || name.contains("_TRAPDOOR") || 
            name.contains("_GATE")) {
            return "interact";
        }
        
        // Buttons and levers
        if (name.contains("_BUTTON") || material == Material.LEVER) {
            return "interact";
        }
        
        return null;
    }
    
    /**
     * Send protection message to player
     */
    private void sendProtectionMessage(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("protection-active"));
    }
}
