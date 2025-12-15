package com.wdp.basedet.protection;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.TrustEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages base protection logic
 */
public class ProtectionManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    
    public ProtectionManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    /**
     * Check if a player can break a block at the given location
     */
    public boolean canBreak(Player player, Location location) {
        return canDoAction(player, location, "break");
    }
    
    /**
     * Check if a player can place a block at the given location
     */
    public boolean canPlace(Player player, Location location) {
        return canDoAction(player, location, "place");
    }
    
    /**
     * Check if a player can interact with a block at the given location
     */
    public boolean canInteract(Player player, Location location, String interactType) {
        return canDoAction(player, location, interactType);
    }
    
    /**
     * Core protection check
     */
    private boolean canDoAction(Player player, Location location, String action) {
        UUID playerUUID = player.getUniqueId();
        
        // Bypass permission
        if (player.hasPermission("basedet.admin.bypass")) {
            return true;
        }
        
        // Get base at location
        Base base = plugin.getDatabaseManager().getBaseAtLocation(location);
        if (base == null) {
            return true; // No base here
        }
        
        // Owner can always do anything
        if (base.getOwnerUUID().equals(playerUUID)) {
            return true;
        }
        
        UUID ownerUUID = base.getOwnerUUID();
        Player owner = Bukkit.getPlayer(ownerUUID);
        boolean ownerOnline = owner != null && owner.isOnline();
        
        // If owner is online, griefing is allowed (per requirements)
        if (ownerOnline && !config.isProtectedAction("block-break")) {
            return true;
        }
        
        // Check if action is protected when offline
        if (!ownerOnline) {
            String configAction = convertActionToConfig(action);
            if (!config.isProtectedAction(configAction)) {
                return true; // Action not protected
            }
        }
        
        // Combat flag check - use CombatManager (supports CMI and custom)
        if (plugin.getCombatManager() != null && config.isCombatEnabled()) {
            if (isInCombatNear(player, base)) {
                if (config.isAllowBlocksDuringCombat()) {
                    return true;
                }
            }
        }
        
        // Check trust
        TrustEntry trust = plugin.getDatabaseManager().getTrust(base.getId(), playerUUID);
        if (trust != null) {
            if (trust.canDoAction(action, ownerOnline)) {
                return true;
            }
        }
        
        // Log if debug enabled
        if (config.isLogProtectionChecks()) {
            plugin.debug(String.format("Protection blocked: %s tried to %s at %s (owner %s, online: %s)",
                    player.getName(), action, formatLocation(location), 
                    ownerUUID, ownerOnline));
        }
        
        return false;
    }
    
    /**
     * Check if player is in combat near a base
     */
    private boolean isInCombatNear(Player player, Base base) {
        if (plugin.getCombatManager() == null) {
            return false;
        }
        
        if (!plugin.getCombatManager().isInCombat(player)) {
            return false;
        }
        
        // Check if player is within combat radius of base
        Location playerLoc = player.getLocation();
        Location baseCenter = base.getCenter();
        
        if (baseCenter == null || !playerLoc.getWorld().equals(baseCenter.getWorld())) {
            return false;
        }
        
        double distance = playerLoc.distance(baseCenter);
        return distance <= config.getCombatRadius();
    }
    
    /**
     * Convert action name to config key
     */
    private String convertActionToConfig(String action) {
        return switch (action.toLowerCase()) {
            case "break" -> "block-break";
            case "place" -> "block-place";
            case "chest" -> "chest-access";
            case "interact" -> "door-interact";
            default -> action;
        };
    }
    
    /**
     * Format location for logging
     */
    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + 
               loc.getBlockY() + "," + loc.getBlockZ();
    }
    
    /**
     * Check if a location is within any protected base
     */
    public Base getBaseAtLocation(Location location) {
        return plugin.getDatabaseManager().getBaseAtLocation(location);
    }
    
    /**
     * Get the base owner (if any) at a location
     */
    public UUID getBaseOwner(Location location) {
        Base base = getBaseAtLocation(location);
        return base != null ? base.getOwnerUUID() : null;
    }
}
