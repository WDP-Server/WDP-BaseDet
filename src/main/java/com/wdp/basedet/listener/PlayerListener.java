package com.wdp.basedet.listener;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for player events
 */
public class PlayerListener implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    
    public PlayerListener(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Load player data
        plugin.getScoreManager().onPlayerJoin(player);
        
        // Resume detection prompt if any
        plugin.getDetectionManager().onPlayerJoin(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save player data
        plugin.getScoreManager().onPlayerQuit(player);
        
        // Pause detection prompt
        plugin.getDetectionManager().onPlayerQuit(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only track if player actually moved blocks (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // Track walking
        plugin.getScoreManager().trackWalking(event.getPlayer(), event.getTo());
    }
}
