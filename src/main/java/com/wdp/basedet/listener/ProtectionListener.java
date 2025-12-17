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
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for protection-related events
 */
public class ProtectionListener implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final ProtectionManager protectionManager;
    
    // Track players who have been notified about combat mechanics (avoid spam)
    private final Map<UUID, Long> combatNotifications = new ConcurrentHashMap<>();
    private static final long NOTIFICATION_COOLDOWN = 30000; // 30 seconds
    
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
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if significant movement
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check combat and base entry
        checkCombatBaseEntry(player, event.getTo());
        
        // Check for Discord notifications (player entering someone's base)
        checkBaseEntry(player, event.getTo());
    }
    
    /**
     * Check if a combat-tagged player enters a base area
     */
    private void checkCombatBaseEntry(Player player, Location location) {
        if (plugin.getCombatManager() == null || !plugin.getConfigManager().isCombatEnabled()) {
            return;
        }
        
        // Only notify if in combat
        if (!plugin.getCombatManager().isInCombat(player)) {
            return;
        }
        
        // Check if entering a base
        Base base = protectionManager.getBaseAtLocation(location);
        if (base == null) {
            return;
        }
        
        // Don't notify about own base
        if (base.getOwnerUUID().equals(player.getUniqueId())) {
            return;
        }
        
        // Check cooldown
        UUID uuid = player.getUniqueId();
        Long lastNotification = combatNotifications.get(uuid);
        if (lastNotification != null && System.currentTimeMillis() - lastNotification < NOTIFICATION_COOLDOWN) {
            return;
        }
        combatNotifications.put(uuid, System.currentTimeMillis());
        
        // Notify player about combat mechanics
        if (plugin.getConfigManager().isNotifyPlayersAboutCombat()) {
            player.sendMessage(plugin.getConfigManager().getMessage("combat-enter"));
            player.sendMessage(ChatColor.GRAY + "  Combat in this base is allowed while tagged!");
            
            // Notify base owner if online
            Player owner = Bukkit.getPlayer(base.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(plugin.getConfigManager().getMessagePrefix() + ChatColor.RED + 
                        "⚔ " + player.getName() + " entered your base while in combat!");
            }
        }
    }
    
    /**
     * Check if player is entering someone's base (for Discord notifications)
     */
    private void checkBaseEntry(Player player, Location location) {
        Base base = protectionManager.getBaseAtLocation(location);
        if (base == null) {
            return;
        }
        
        // Don't notify about own base
        if (base.getOwnerUUID().equals(player.getUniqueId())) {
            return;
        }
        
        // Check if owner is offline
        Player owner = Bukkit.getPlayer(base.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            return; // Owner is online, no notification needed
        }
        
        // Check if player is trusted
        TrustEntry trust = plugin.getDatabaseManager().getTrust(base.getId(), player.getUniqueId());
        if (trust != null) {
            return; // Player is trusted
        }
        
        // Send Discord notification
        if (plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()) {
            plugin.getDiscordIntegration().notifyBaseEntry(base.getOwnerUUID(), player, base);
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
