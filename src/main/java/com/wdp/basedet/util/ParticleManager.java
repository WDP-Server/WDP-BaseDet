package com.wdp.basedet.util;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages particle visualization for base boundaries
 */
public class ParticleManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    
    // Active particle displays
    private final Map<UUID, ParticleDisplay> activeDisplays = new ConcurrentHashMap<>();
    
    // Players who have toggled particles on for viewing their base
    private final Set<UUID> viewingPlayers = ConcurrentHashMap.newKeySet();
    
    public ParticleManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    /**
     * Start showing bounds for a player (detection prompt)
     */
    public void startShowingBounds(UUID playerUUID, BoundingBox bounds, String worldName) {
        ParticleDisplay display = new ParticleDisplay(bounds, worldName, true);
        activeDisplays.put(playerUUID, display);
    }
    
    /**
     * Stop showing bounds for a player
     */
    public void stopShowingBounds(UUID playerUUID) {
        activeDisplays.remove(playerUUID);
    }
    
    /**
     * Toggle viewing for a player's own base
     */
    public boolean toggleViewing(UUID playerUUID) {
        if (viewingPlayers.contains(playerUUID)) {
            viewingPlayers.remove(playerUUID);
            return false;
        } else {
            viewingPlayers.add(playerUUID);
            return true;
        }
    }
    
    /**
     * Check if player is viewing particles
     */
    public boolean isViewing(UUID playerUUID) {
        return viewingPlayers.contains(playerUUID) || activeDisplays.containsKey(playerUUID);
    }
    
    /**
     * Called by scheduled task to show particles
     */
    public void showParticles() {
        if (!config.areParticlesEnabled()) {
            return;
        }
        
        Particle.DustOptions dustOptions = new Particle.DustOptions(
                config.getParticleColor(),
                config.getParticleSize()
        );
        
        // Show detection prompt particles
        for (Map.Entry<UUID, ParticleDisplay> entry : activeDisplays.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            
            ParticleDisplay display = entry.getValue();
            showBoundsParticles(player, display.bounds, display.worldName, dustOptions);
        }
        
        // Show viewing particles for players viewing their bases
        for (UUID uuid : viewingPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            
            // Get player's bases
            List<Base> bases = plugin.getDatabaseManager().getPlayerBases(uuid);
            for (Base base : bases) {
                if (base.getWorldName().equals(player.getWorld().getName())) {
                    showBoundsParticles(player, base.getBounds(), base.getWorldName(), dustOptions);
                }
            }
        }
    }
    
    /**
     * Show particles for a bounding box
     */
    private void showBoundsParticles(Player player, BoundingBox bounds, String worldName, 
                                      Particle.DustOptions dustOptions) {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !player.getWorld().equals(world)) {
            return;
        }
        
        double spacing = config.getParticleSpacing();
        
        // Draw the 12 edges of the bounding box
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minY = bounds.getMinY();
        int maxY = bounds.getMaxY();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();
        
        // Determine view height for horizontal rectangle
        int viewY = calculateViewHeight(player, bounds);
        
        // Bottom edges (at minY)
        drawLine(player, world, dustOptions, spacing, minX, minY, minZ, maxX, minY, minZ);
        drawLine(player, world, dustOptions, spacing, minX, minY, minZ, minX, minY, maxZ);
        drawLine(player, world, dustOptions, spacing, maxX, minY, minZ, maxX, minY, maxZ);
        drawLine(player, world, dustOptions, spacing, minX, minY, maxZ, maxX, minY, maxZ);
        
        // Top edges (at maxY)
        drawLine(player, world, dustOptions, spacing, minX, maxY, minZ, maxX, maxY, minZ);
        drawLine(player, world, dustOptions, spacing, minX, maxY, minZ, minX, maxY, maxZ);
        drawLine(player, world, dustOptions, spacing, maxX, maxY, minZ, maxX, maxY, maxZ);
        drawLine(player, world, dustOptions, spacing, minX, maxY, maxZ, maxX, maxY, maxZ);
        
        // Vertical edges (corners)
        drawLine(player, world, dustOptions, spacing, minX, minY, minZ, minX, maxY, minZ);
        drawLine(player, world, dustOptions, spacing, maxX, minY, minZ, maxX, maxY, minZ);
        drawLine(player, world, dustOptions, spacing, minX, minY, maxZ, minX, maxY, maxZ);
        drawLine(player, world, dustOptions, spacing, maxX, minY, maxZ, maxX, maxY, maxZ);
        
        // Horizontal rectangle at view height (for better visibility)
        if (viewY != minY && viewY != maxY) {
            Particle.DustOptions viewDust = new Particle.DustOptions(
                    Color.fromRGB(0, 255, 255), // Cyan for view height
                    config.getParticleSize()
            );
            drawLine(player, world, viewDust, spacing, minX, viewY, minZ, maxX, viewY, minZ);
            drawLine(player, world, viewDust, spacing, minX, viewY, minZ, minX, viewY, maxZ);
            drawLine(player, world, viewDust, spacing, maxX, viewY, minZ, maxX, viewY, maxZ);
            drawLine(player, world, viewDust, spacing, minX, viewY, maxZ, maxX, viewY, maxZ);
        }
    }
    
    /**
     * Calculate the view height for the horizontal rectangle
     */
    private int calculateViewHeight(Player player, BoundingBox bounds) {
        int playerY = player.getLocation().getBlockY();
        int viewOffset = config.getViewHeightOffset();
        
        // If player is inside or below the base, show at player's eye level
        if (playerY >= bounds.getMinY() && playerY <= bounds.getMaxY()) {
            // Player is inside the base vertically
            return Math.min(playerY + viewOffset, bounds.getMaxY());
        } else if (playerY < bounds.getMinY()) {
            // Player is below the base
            return bounds.getMinY();
        } else {
            // Player is above the base
            return bounds.getMaxY();
        }
    }
    
    /**
     * Draw a line of particles between two points
     */
    private void drawLine(Player player, World world, Particle.DustOptions dustOptions,
                          double spacing, int x1, int y1, int z1, int x2, int y2, int z2) {
        double distance = Math.sqrt(
                Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2)
        );
        
        if (distance < 0.1) {
            // Single point
            spawnParticle(player, world, dustOptions, x1 + 0.5, y1 + 0.5, z1 + 0.5);
            return;
        }
        
        // Calculate number of particles
        int particleCount = (int) Math.ceil(distance / spacing);
        
        double dx = (x2 - x1) / (double) particleCount;
        double dy = (y2 - y1) / (double) particleCount;
        double dz = (z2 - z1) / (double) particleCount;
        
        for (int i = 0; i <= particleCount; i++) {
            double x = x1 + (dx * i) + 0.5;
            double y = y1 + (dy * i) + 0.5;
            double z = z1 + (dz * i) + 0.5;
            
            spawnParticle(player, world, dustOptions, x, y, z);
        }
    }
    
    /**
     * Spawn a particle at a location (only visible to specific player)
     */
    private void spawnParticle(Player player, World world, Particle.DustOptions dustOptions,
                               double x, double y, double z) {
        Location loc = new Location(world, x, y, z);
        
        // Only show if within reasonable distance (64 blocks)
        if (player.getLocation().distanceSquared(loc) > 4096) {
            return;
        }
        
        player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dustOptions);
    }
    
    /**
     * Inner class for tracking particle displays
     */
    private static class ParticleDisplay {
        final BoundingBox bounds;
        final String worldName;
        final boolean isPrompt;
        
        ParticleDisplay(BoundingBox bounds, String worldName, boolean isPrompt) {
            this.bounds = bounds;
            this.worldName = worldName;
            this.isPrompt = isPrompt;
        }
    }
}
