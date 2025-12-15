package com.wdp.basedet.integration;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integration with CMI for combat tag detection
 */
public class CMIIntegration {
    
    private final WDPBaseDetPlugin plugin;
    private boolean enabled;
    private Object cmiPlugin;
    private Method isCombatTaggedMethod;
    
    public CMIIntegration(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }
    
    public boolean setup() {
        try {
            // Get CMI plugin instance
            cmiPlugin = plugin.getServer().getPluginManager().getPlugin("CMI");
            if (cmiPlugin == null) {
                return false;
            }
            
            // Try to find the combat tag method via reflection
            // CMI API varies between versions, so we use reflection
            Class<?> cmiClass = cmiPlugin.getClass();
            
            // Try to get the combat manager
            Method getPlayerManagerMethod = cmiClass.getMethod("getPlayerManager");
            Object playerManager = getPlayerManagerMethod.invoke(cmiPlugin);
            
            if (playerManager != null) {
                // Try to find the combat tag check method
                Class<?> playerManagerClass = playerManager.getClass();
                for (Method method : playerManagerClass.getMethods()) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("combat") && method.getParameterCount() == 1) {
                        Class<?>[] params = method.getParameterTypes();
                        if (params[0] == UUID.class || Player.class.isAssignableFrom(params[0])) {
                            isCombatTaggedMethod = method;
                            break;
                        }
                    }
                }
            }
            
            enabled = true;
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup CMI integration: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a player is in combat
     */
    public boolean isInCombat(Player player) {
        if (!enabled || cmiPlugin == null) {
            return false;
        }
        
        try {
            // Try using CMI's API
            Class<?> cmiUserClass = Class.forName("com.Zrips.CMI.Containers.CMIUser");
            Method getUser = cmiPlugin.getClass().getMethod("getUser", Player.class);
            Object user = getUser.invoke(cmiPlugin, player);
            
            if (user != null) {
                Method isCombatTagged = user.getClass().getMethod("isCombatTagged");
                Object result = isCombatTagged.invoke(user);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (Exception e) {
            // Try alternative method
            try {
                if (isCombatTaggedMethod != null) {
                    Object result = isCombatTaggedMethod.invoke(
                            cmiPlugin.getClass().getMethod("getPlayerManager").invoke(cmiPlugin),
                            player.getUniqueId()
                    );
                    if (result instanceof Boolean) {
                        return (Boolean) result;
                    }
                }
            } catch (Exception ex) {
                plugin.debug("CMI combat check failed: " + ex.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Get remaining combat time in seconds
     */
    public int getCombatTimeRemaining(Player player) {
        if (!enabled || cmiPlugin == null) {
            return 0;
        }
        
        try {
            Class<?> cmiUserClass = Class.forName("com.Zrips.CMI.Containers.CMIUser");
            Method getUser = cmiPlugin.getClass().getMethod("getUser", Player.class);
            Object user = getUser.invoke(cmiPlugin, player);
            
            if (user != null) {
                Method getCombatTime = user.getClass().getMethod("getCombatTime");
                Object result = getCombatTime.invoke(user);
                if (result instanceof Long) {
                    long endTime = (Long) result;
                    long remaining = endTime - System.currentTimeMillis();
                    return remaining > 0 ? (int) (remaining / 1000) : 0;
                }
            }
        } catch (Exception e) {
            plugin.debug("CMI combat time check failed: " + e.getMessage());
        }
        
        return 0;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}
