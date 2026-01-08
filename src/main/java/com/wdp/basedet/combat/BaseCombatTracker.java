package com.wdp.basedet.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who can attack whom in bases
 * Prevents first-strike attacks on base owners
 */
public class BaseCombatTracker {
    
    // Track who is allowed to attack whom
    // Key: Attacker UUID -> Value: Map of (Victim UUID -> Expiry Time)
    private final Map<UUID, Map<UUID, Long>> attackPermissions = new ConcurrentHashMap<>();
    
    /**
     * Check if attacker is allowed to attack victim
     */
    public boolean canAttack(UUID attacker, UUID victim) {
        Map<UUID, Long> permissions = attackPermissions.get(attacker);
        if (permissions == null) {
            return false;
        }
        
        Long expiryTime = permissions.get(victim);
        if (expiryTime == null) {
            return false;
        }
        
        // Check if permission has expired
        if (System.currentTimeMillis() > expiryTime) {
            permissions.remove(victim);
            if (permissions.isEmpty()) {
                attackPermissions.remove(attacker);
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Grant permission for attacker to attack victim for a duration
     * @param attacker The player who can attack
     * @param victim The player who can be attacked
     * @param durationSeconds How long the permission lasts
     */
    public void grantAttackPermission(UUID attacker, UUID victim, int durationSeconds) {
        long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        
        attackPermissions.computeIfAbsent(attacker, k -> new ConcurrentHashMap<>())
                .put(victim, expiryTime);
    }
    
    /**
     * Revoke attack permission
     */
    public void revokeAttackPermission(UUID attacker, UUID victim) {
        Map<UUID, Long> permissions = attackPermissions.get(attacker);
        if (permissions != null) {
            permissions.remove(victim);
            if (permissions.isEmpty()) {
                attackPermissions.remove(attacker);
            }
        }
    }
    
    /**
     * Clear all permissions for a player (when they leave)
     */
    public void clearPlayer(UUID player) {
        attackPermissions.remove(player);
        
        // Also remove them as victims from all other players
        for (Map<UUID, Long> permissions : attackPermissions.values()) {
            permissions.remove(player);
        }
    }
    
    /**
     * Clear all expired permissions
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        
        attackPermissions.entrySet().removeIf(entry -> {
            Map<UUID, Long> permissions = entry.getValue();
            permissions.entrySet().removeIf(perm -> now > perm.getValue());
            return permissions.isEmpty();
        });
    }
}
