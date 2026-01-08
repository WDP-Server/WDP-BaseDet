package com.wdp.basedet.listener;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.combat.BaseCombatTracker;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.config.MessageManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.TrustEntry;
import com.wdp.basedet.protection.ProtectionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles PvP protection in bases
 */
public class CombatProtectionListener implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final ProtectionManager protectionManager;
    private final ConfigManager config;
    private final MessageManager messages;
    private final BaseCombatTracker combatTracker;
    
    public CombatProtectionListener(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.protectionManager = plugin.getProtectionManager();
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessages();
        this.combatTracker = new BaseCombatTracker();
        
        // Schedule cleanup task every 30 seconds
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, 
            combatTracker::cleanupExpired, 600L, 600L);
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Only handle PvP
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        
        // Get attacker
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        
        if (attacker == null) {
            return;
        }
        
        // Don't protect from self
        if (attacker.equals(victim)) {
            return;
        }
        
        // Check if combat protection is enabled
        if (!config.isCombatEnabled() || !config.isCombatBaseProtectionEnabled()) {
            return;
        }
        
        // Check if victim has bypass permission
        if (victim.hasPermission("basedet.bypass.combat") || 
            victim.hasPermission("basedet.admin.bypass")) {
            return;
        }
        
        // Get the base at victim's location
        Base base = protectionManager.getBaseAtLocation(victim.getLocation());
        if (base == null) {
            return;
        }
        
        UUID victimUUID = victim.getUniqueId();
        UUID attackerUUID = attacker.getUniqueId();
        UUID ownerUUID = base.getOwnerUUID();
        
        // Only protect the base owner (and optionally trusted players)
        boolean isVictimOwner = victimUUID.equals(ownerUUID);
        boolean isVictimTrusted = false;
        
        if (!isVictimOwner) {
            TrustEntry trust = plugin.getDatabaseManager().getTrust(base.getId(), victimUUID);
            isVictimTrusted = trust != null;
            
            // Don't protect non-trusted, non-owner players
            if (!isVictimTrusted) {
                return;
            }
            
            // Check if we should protect trusted players
            if (!config.isProtectTrustedFromFirstStrike()) {
                return;
            }
        }
        
        // Don't protect if attacker is the owner
        if (attackerUUID.equals(ownerUUID)) {
            // Owner attacking someone - grant retaliation permission
            if (config.isPreventFirstStrike()) {
                int window = config.getRetaliationWindow();
                combatTracker.grantAttackPermission(victimUUID, attackerUUID, window);
            }
            return;
        }
        
        // Check if attacker is trusted
        TrustEntry attackerTrust = plugin.getDatabaseManager().getTrust(base.getId(), attackerUUID);
        boolean isAttackerTrusted = attackerTrust != null;
        
        // If both are trusted/owner, allow combat
        if (isAttackerTrusted && isVictimTrusted) {
            return;
        }
        
        // Check if attacker already has permission to attack (from retaliation)
        if (combatTracker.canAttack(attackerUUID, victimUUID)) {
            return;
        }
        
        // Check if prevent-first-strike is enabled
        if (!config.isPreventFirstStrike()) {
            return;
        }
        
        // BLOCK THE ATTACK - untrusted player trying to attack owner/trusted first
        event.setCancelled(true);
        
        // Send message to attacker
        if (config.isShowBlockedMessage()) {
            messages.send(attacker, "combat.first-strike-blocked");
        }
        
        // Notify victim (owner)
        if (config.isNotifyOwnerOfAttack() && victim.isOnline()) {
            String message = messages.get("combat.attempted-attack");
            if (message != null && !message.isEmpty()) {
                message = message.replace("{attacker}", attacker.getName());
                victim.sendMessage(message);
            }
        }
        
        // Grant victim permission to fight back
        int window = config.getRetaliationWindow();
        combatTracker.grantAttackPermission(victimUUID, attackerUUID, window);
        
        // Debug log
        if (config.isLogProtectionChecks()) {
            plugin.debug(String.format("Blocked first strike: %s tried to attack %s in their base",
                    attacker.getName(), victim.getName()));
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up combat tracker when player leaves
        combatTracker.clearPlayer(event.getPlayer().getUniqueId());
    }
    
    public BaseCombatTracker getCombatTracker() {
        return combatTracker;
    }
}
