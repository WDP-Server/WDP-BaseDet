package com.wdp.basedet.trust;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.TrustEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Manages trust relationships between players and bases
 */
public class TrustManager {
    
    private final WDPBaseDetPlugin plugin;
    
    public TrustManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Add a player to the trust list of a base
     */
    public boolean addTrust(UUID ownerUUID, UUID trustedUUID) {
        // Get owner's base
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(ownerUUID);
        if (bases.isEmpty()) {
            return false;
        }
        
        // Add to all bases
        for (Base base : bases) {
            plugin.getDatabaseManager().addTrust(base.getId(), trustedUUID);
        }
        
        return true;
    }
    
    /**
     * Add a player to a specific base's trust list
     */
    public void addTrustToBase(long baseId, UUID trustedUUID) {
        plugin.getDatabaseManager().addTrust(baseId, trustedUUID);
    }
    
    /**
     * Remove a player from the trust list
     */
    public boolean removeTrust(UUID ownerUUID, UUID trustedUUID) {
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(ownerUUID);
        if (bases.isEmpty()) {
            return false;
        }
        
        for (Base base : bases) {
            plugin.getDatabaseManager().removeTrust(base.getId(), trustedUUID);
        }
        
        return true;
    }
    
    /**
     * Check if a player is trusted in any of owner's bases
     */
    public boolean isTrusted(UUID ownerUUID, UUID playerUUID) {
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(ownerUUID);
        
        for (Base base : bases) {
            TrustEntry trust = plugin.getDatabaseManager().getTrust(base.getId(), playerUUID);
            if (trust != null) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get all trusted players for a base
     */
    public List<TrustEntry> getTrustedPlayers(long baseId) {
        return plugin.getDatabaseManager().getBaseTrusted(baseId);
    }
    
    /**
     * Update a specific trust permission
     */
    public void updatePermission(long trustId, String permission, boolean value) {
        plugin.getDatabaseManager().updateTrustPermission(trustId, permission, value);
    }
    
    /**
     * Get player name from UUID
     */
    public String getPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
    }
    
    /**
     * Get UUID from player name
     */
    public UUID getPlayerUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            return online.getUniqueId();
        }
        
        // Try offline player
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) {
            return offline.getUniqueId();
        }
        
        return null;
    }
}
