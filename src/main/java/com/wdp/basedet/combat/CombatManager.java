package com.wdp.basedet.combat;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.integration.CMIIntegration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom combat tag manager with CMI fallback
 */
public class CombatManager implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final CMIIntegration cmiIntegration;
    
    // Track combat tagged players
    private final Map<UUID, CombatTag> combatTags = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> combatBossBars = new ConcurrentHashMap<>();
    
    // Config values
    private boolean enabled;
    private boolean useCMI;
    private boolean customEnabled;
    private int combatDuration;
    private boolean triggerPvP;
    private boolean triggerPvE;
    private boolean triggerProjectile;
    private boolean showMessage;
    private boolean showBossBar;
    private BarColor bossBarColor;
    
    public CombatManager(WDPBaseDetPlugin plugin, CMIIntegration cmiIntegration) {
        this.plugin = plugin;
        this.cmiIntegration = cmiIntegration;
        loadConfig();
    }
    
    public void loadConfig() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("combat.enabled", true);
        this.useCMI = config.getBoolean("combat.use-cmi", true);
        this.customEnabled = config.getBoolean("combat.custom.enabled", true);
        this.combatDuration = config.getInt("combat.custom.duration", 15);
        this.triggerPvP = config.getBoolean("combat.custom.triggers.player-damage-player", true);
        this.triggerPvE = config.getBoolean("combat.custom.triggers.player-damage-entity", false);
        this.triggerProjectile = config.getBoolean("combat.custom.triggers.projectile-hit", true);
        this.showMessage = config.getBoolean("combat.custom.show-message", true);
        this.showBossBar = config.getBoolean("combat.custom.show-bossbar", true);
        
        String colorStr = config.getString("combat.custom.bossbar-color", "RED");
        try {
            this.bossBarColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.bossBarColor = BarColor.RED;
        }
    }
    
    /**
     * Check if a player is in combat (uses CMI if available, otherwise custom)
     */
    public boolean isInCombat(Player player) {
        if (!enabled) return false;
        
        // Try CMI first if enabled
        if (useCMI && cmiIntegration != null && cmiIntegration.isEnabled()) {
            if (cmiIntegration.isInCombat(player)) {
                return true;
            }
        }
        
        // Use custom combat detection
        if (customEnabled) {
            CombatTag tag = combatTags.get(player.getUniqueId());
            if (tag != null && !tag.isExpired()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get remaining combat time in seconds
     */
    public int getCombatTimeRemaining(Player player) {
        if (!enabled) return 0;
        
        // Try CMI first
        if (useCMI && cmiIntegration != null && cmiIntegration.isEnabled()) {
            int cmiTime = cmiIntegration.getCombatTimeRemaining(player);
            if (cmiTime > 0) return cmiTime;
        }
        
        // Use custom
        if (customEnabled) {
            CombatTag tag = combatTags.get(player.getUniqueId());
            if (tag != null) {
                return tag.getRemainingSeconds();
            }
        }
        
        return 0;
    }
    
    /**
     * Tag a player as in combat
     */
    public void tagPlayer(Player player, Player opponent) {
        if (!enabled || !customEnabled) return;
        
        UUID uuid = player.getUniqueId();
        long expireTime = System.currentTimeMillis() + (combatDuration * 1000L);
        
        CombatTag existingTag = combatTags.get(uuid);
        if (existingTag == null) {
            // New combat tag
            CombatTag tag = new CombatTag(uuid, expireTime, opponent != null ? opponent.getUniqueId() : null);
            combatTags.put(uuid, tag);
            
            if (showMessage) {
                String msg = plugin.getConfigManager().getMessage("combat-tagged");
                if (msg == null || msg.isEmpty()) {
                    msg = "&c⚔ You are now in combat! Don't log out!";
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
            
            if (showBossBar) {
                createBossBar(player);
            }
            
            // Schedule expiry check
            scheduleExpiryCheck(player);
            
        } else {
            // Extend existing tag
            existingTag.setExpireTime(expireTime);
            if (opponent != null) {
                existingTag.setOpponentUUID(opponent.getUniqueId());
            }
        }
    }
    
    private void createBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Remove existing
        BossBar existing = combatBossBars.remove(uuid);
        if (existing != null) {
            existing.removeAll();
        }
        
        BossBar bar = Bukkit.createBossBar(
                ChatColor.RED + "⚔ " + ChatColor.WHITE + "In Combat " + ChatColor.GRAY + "(" + combatDuration + "s)",
                bossBarColor,
                BarStyle.SOLID
        );
        bar.setProgress(1.0);
        bar.addPlayer(player);
        combatBossBars.put(uuid, bar);
        
        // Update bar task
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            CombatTag tag = combatTags.get(uuid);
            if (tag == null || tag.isExpired() || !player.isOnline()) {
                bar.removeAll();
                combatBossBars.remove(uuid);
                task.cancel();
                return;
            }
            
            int remaining = tag.getRemainingSeconds();
            double progress = (double) remaining / combatDuration;
            bar.setProgress(Math.max(0, Math.min(1, progress)));
            bar.setTitle(ChatColor.RED + "⚔ " + ChatColor.WHITE + "In Combat " + ChatColor.GRAY + "(" + remaining + "s)");
            
        }, 20L, 20L);
    }
    
    private void scheduleExpiryCheck(Player player) {
        UUID uuid = player.getUniqueId();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CombatTag tag = combatTags.get(uuid);
            if (tag != null && tag.isExpired()) {
                combatTags.remove(uuid);
                
                if (player.isOnline() && showMessage) {
                    String msg = plugin.getConfigManager().getMessage("combat-ended");
                    if (msg == null || msg.isEmpty()) {
                        msg = "&a✓ You are no longer in combat.";
                    }
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
                
                // Remove boss bar
                BossBar bar = combatBossBars.remove(uuid);
                if (bar != null) {
                    bar.removeAll();
                }
            }
        }, (combatDuration + 1) * 20L);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !customEnabled) return;
        
        Player attacker = null;
        Player victim = null;
        
        // Get attacker
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (triggerProjectile && projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        
        // Get victim
        if (event.getEntity() instanceof Player) {
            victim = (Player) event.getEntity();
        }
        
        // PvP
        if (attacker != null && victim != null && triggerPvP) {
            tagPlayer(attacker, victim);
            tagPlayer(victim, attacker);
            return;
        }
        
        // PvE (player attacks entity)
        if (attacker != null && victim == null && triggerPvE) {
            tagPlayer(attacker, null);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        combatTags.remove(uuid);
        
        BossBar bar = combatBossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }
    
    /**
     * Clear all combat tags (for reload)
     */
    public void clearAll() {
        combatTags.clear();
        for (BossBar bar : combatBossBars.values()) {
            bar.removeAll();
        }
        combatBossBars.clear();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isUsingCustom() {
        return customEnabled && (!useCMI || cmiIntegration == null || !cmiIntegration.isEnabled());
    }
    
    // ==================== INNER CLASS ====================
    
    public static class CombatTag {
        private final UUID playerUUID;
        private long expireTime;
        private UUID opponentUUID;
        
        public CombatTag(UUID playerUUID, long expireTime, UUID opponentUUID) {
            this.playerUUID = playerUUID;
            this.expireTime = expireTime;
            this.opponentUUID = opponentUUID;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
        
        public int getRemainingSeconds() {
            long remaining = expireTime - System.currentTimeMillis();
            return remaining > 0 ? (int) (remaining / 1000) : 0;
        }
        
        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }
        
        public void setOpponentUUID(UUID opponentUUID) {
            this.opponentUUID = opponentUUID;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public UUID getOpponentUUID() { return opponentUUID; }
    }
}
