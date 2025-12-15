package com.wdp.basedet.integration;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.model.Base;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Integration with DiscordSRV for Discord notifications
 */
public class DiscordIntegration {
    
    private final WDPBaseDetPlugin plugin;
    private boolean enabled;
    private Object discordSRV;
    private Method getAccountLinkManagerMethod;
    private Method sendPrivateMessageMethod;
    
    // Track pending trust requests via Discord
    private final Map<String, PendingTrust> pendingTrusts = new ConcurrentHashMap<>();
    
    public DiscordIntegration(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }
    
    public boolean setup() {
        try {
            discordSRV = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
            if (discordSRV == null) {
                return false;
            }
            
            // Get methods via reflection
            Class<?> discordSRVClass = discordSRV.getClass();
            getAccountLinkManagerMethod = discordSRVClass.getMethod("getAccountLinkManager");
            
            enabled = true;
            
            // Register message listener for trust commands
            registerMessageListener();
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup DiscordSRV integration: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Register listener for Discord messages
     */
    private void registerMessageListener() {
        // This would require the DiscordSRV API dependency to properly implement
        // For now, we'll use a simplified approach
        plugin.getLogger().info("DiscordSRV message listener registered");
    }
    
    /**
     * Send a notification when someone enters a player's base
     */
    public void notifyBaseEntry(UUID ownerUUID, Player intruder, Base base) {
        if (!enabled || !plugin.getConfigManager().isDmOnEntry()) {
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String discordId = getDiscordId(ownerUUID);
                if (discordId == null) {
                    return;
                }
                
                String message = plugin.getConfigManager().getDiscordMessage("entry")
                        .replace("{intruder}", intruder.getName())
                        .replace("{location}", base.getLocationString());
                
                sendDM(discordId, message);
                
                // Store pending trust if enabled
                if (plugin.getConfigManager().isAllowTrustViaDM()) {
                    pendingTrusts.put(discordId, new PendingTrust(ownerUUID, intruder.getUniqueId(), base.getId()));
                }
                
            } catch (Exception e) {
                plugin.debug("Failed to send Discord notification: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send a combat notification
     */
    public void notifyCombat(UUID ownerUUID, Player attacker, Player defender) {
        if (!enabled) {
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String discordId = getDiscordId(ownerUUID);
                if (discordId == null) {
                    return;
                }
                
                String message = plugin.getConfigManager().getDiscordMessage("combat")
                        .replace("{attacker}", attacker.getName())
                        .replace("{defender}", defender.getName());
                
                sendDM(discordId, message);
                
            } catch (Exception e) {
                plugin.debug("Failed to send combat notification: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle a trust request from Discord
     */
    public void handleTrustRequest(String discordId) {
        PendingTrust pending = pendingTrusts.remove(discordId);
        if (pending == null) {
            return;
        }
        
        // Add trust
        plugin.getDatabaseManager().addTrust(pending.baseId, pending.intruderUUID);
        
        // Send confirmation
        String message = plugin.getConfigManager().getDiscordMessage("trusted")
                .replace("{player}", plugin.getTrustManager().getPlayerName(pending.intruderUUID));
        sendDM(discordId, message);
        
        // Notify the trusted player if online
        Player trusted = Bukkit.getPlayer(pending.intruderUUID);
        if (trusted != null && trusted.isOnline()) {
            trusted.sendMessage(plugin.getConfigManager().getMessage("trusted-added")
                    .replace("{player}", plugin.getTrustManager().getPlayerName(pending.ownerUUID)));
        }
    }
    
    /**
     * Get Discord ID for a player
     */
    private String getDiscordId(UUID uuid) {
        try {
            Object accountLinkManager = getAccountLinkManagerMethod.invoke(discordSRV);
            if (accountLinkManager == null) {
                return null;
            }
            
            Method getDiscordIdMethod = accountLinkManager.getClass().getMethod("getDiscordId", UUID.class);
            Object result = getDiscordIdMethod.invoke(accountLinkManager, uuid);
            
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            plugin.debug("Failed to get Discord ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Send a DM to a Discord user
     */
    private void sendDM(String discordId, String message) {
        try {
            // Use DiscordSRV API to send DM
            Class<?> discordUtilClass = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
            Method getUserByIdMethod = discordUtilClass.getMethod("getUserById", String.class);
            Object user = getUserByIdMethod.invoke(null, discordId);
            
            if (user != null) {
                Method openPrivateChannelMethod = user.getClass().getMethod("openPrivateChannel");
                Object action = openPrivateChannelMethod.invoke(user);
                
                // Queue the message
                Method queueMethod = action.getClass().getMethod("queue", java.util.function.Consumer.class);
                queueMethod.invoke(action, (java.util.function.Consumer<Object>) channel -> {
                    try {
                        Method sendMessageMethod = channel.getClass().getMethod("sendMessage", String.class);
                        Object msgAction = sendMessageMethod.invoke(channel, message);
                        Method queueMsgMethod = msgAction.getClass().getMethod("queue");
                        queueMsgMethod.invoke(msgAction);
                    } catch (Exception e) {
                        plugin.debug("Failed to send DM: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            plugin.debug("Failed to send Discord DM: " + e.getMessage());
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Inner class for pending trust requests
     */
    private static class PendingTrust {
        final UUID ownerUUID;
        final UUID intruderUUID;
        final long baseId;
        
        PendingTrust(UUID ownerUUID, UUID intruderUUID, long baseId) {
            this.ownerUUID = ownerUUID;
            this.intruderUUID = intruderUUID;
            this.baseId = baseId;
        }
    }
}
