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
        
        // Combat flag check - use CombatManager (supports CMI and custom)
        if (plugin.getCombatManager() != null && config.isCombatEnabled()) {
            if (isInCombatNear(player, base)) {
                if (config.isAllowBlocksDuringCombat()) {
                    return true;
                }
            }
        }
        
        // Check if player is trusted
        TrustEntry trust = plugin.getDatabaseManager().getTrust(base.getId(), playerUUID);
        if (trust != null) {
            // Trusted player - use trust permissions
            if (trust.canDoAction(action, ownerOnline)) {
                return true;
            }
        } else {
            // Untrusted player - use untrusted permissions from config
            String configAction = convertActionToUntrustedConfig(action);
            boolean allowed;
            
            if (ownerOnline) {
                // Check untrusted-online permissions
                allowed = config.getUntrustedOnline(configAction);
            } else {
                // Check untrusted-offline permissions
                allowed = config.getUntrustedOffline(configAction);
            }
            
            if (allowed) {
                return true;
            }
        }
        
        // Log if debug enabled
        if (config.isLogProtectionChecks()) {
            plugin.debug(String.format("Protection blocked: %s tried to %s at %s (owner %s, online: %s, trusted: %s)",
                    player.getName(), action, formatLocation(location), 
                    ownerUUID, ownerOnline, trust != null));
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
     * Convert action name to untrusted config key
     */
    private String convertActionToUntrustedConfig(String action) {
        return switch (action.toLowerCase()) {
            case "break" -> "block-break";
            case "place" -> "block-place";
            case "chest", "container" -> "container-access";
            case "door" -> "door-interact";
            case "redstone" -> "button-interact";
            case "entity_damage" -> "entity-damage";
            case "vehicle" -> "vehicle-interact";
            case "decoration" -> "decoration-interact";
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
