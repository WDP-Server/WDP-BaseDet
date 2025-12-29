package com.wdp.basedet.integration;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import java.text.SimpleDateFormat;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * PlaceholderAPI expansion for WDP-BaseDet
 * 
 * Available Placeholders:
 * 
 * === PLAYER STATS ===
 * %basedet_score%                    - Current detection score
 * %basedet_base_count%               - Number of confirmed bases
 * %basedet_has_base%                 - true/false if player has a base
 * %basedet_trusted_count%            - Total players trusted across all bases
 * 
 * === PRIMARY BASE (First/Only Base) ===
 * %basedet_base_world%               - World name of primary base
 * %basedet_base_dimension%           - Dimension (Overworld/Nether/End)
 * %basedet_base_width%               - Width of base (X axis)
 * %basedet_base_length%              - Length of base (Z axis)
 * %basedet_base_height%              - Height of base (Y axis)
 * %basedet_base_volume%              - Total volume in blocks
 * %basedet_base_volume_formatted%    - Volume with comma formatting
 * %basedet_base_center_x%            - Center X coordinate
 * %basedet_base_center_y%            - Center Y coordinate
 * %basedet_base_center_z%            - Center Z coordinate
 * %basedet_base_center%              - Center coordinates (x, y, z)
 * %basedet_base_min_x%               - Minimum X coordinate
 * %basedet_base_min_y%               - Minimum Y coordinate
 * %basedet_base_min_z%               - Minimum Z coordinate
 * %basedet_base_max_x%               - Maximum X coordinate
 * %basedet_base_max_y%               - Maximum Y coordinate
 * %basedet_base_max_z%               - Maximum Z coordinate
 * %basedet_base_size%                - Size as WxLxH
 * %basedet_base_created%             - Date base was created
 * %basedet_base_confirmed%           - true/false if base is confirmed
 * %basedet_base_trusted%             - Number of trusted players
 * %basedet_base_id%                  - Internal base ID
 * 
 * === COMBAT STATUS ===
 * %basedet_combat%                   - true/false if in combat
 * %basedet_combat_time%              - Seconds remaining in combat (0 if not)
 * 
 * === DETECTION STATUS ===
 * %basedet_pending%                  - true/false if has pending detection
 * %basedet_threshold%                - Detection threshold from config
 * %basedet_score_percent%            - Score as percentage of threshold
 * 
 * === LOCATION CONTEXT ===
 * %basedet_in_base%                  - true/false if player is in their base
 * %basedet_in_any_base%              - true/false if in any protected base
 * %basedet_base_owner%               - Owner name if in someone's base
 * 
 * === PROTECTION STATUS ===
 * %basedet_protection_active%        - true/false if base protection active
 * %basedet_owner_online%             - true/false if base owner is online
 * 
 * === CONFIG VALUES ===
 * %basedet_config_threshold%         - Detection threshold
 * %basedet_config_max_bases%         - Max bases per player
 * %basedet_config_teleport_cost%     - Teleport cost
 * %basedet_config_teleport_cooldown% - Teleport cooldown in seconds
 */
public class BaseDetPlaceholders extends PlaceholderExpansion {
    
    private final WDPBaseDetPlugin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");
    
    public BaseDetPlaceholders(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "basedet";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return "WDP";
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";
        
        Player player = offlinePlayer.getPlayer();
        
        // Get primary base (first confirmed base)
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(offlinePlayer.getUniqueId());
        Base primaryBase = bases.stream().filter(Base::isConfirmed).findFirst().orElse(null);
        
        // === PLAYER STATS ===
        
        if (params.equalsIgnoreCase("score")) {
            return String.format("%.1f", plugin.getScoreManager().getScore(offlinePlayer.getUniqueId()));
        }
        
        if (params.equalsIgnoreCase("base_count")) {
            return String.valueOf(bases.stream().filter(Base::isConfirmed).count());
        }
        
        if (params.equalsIgnoreCase("has_base")) {
            return primaryBase != null ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("trusted_count")) {
            int total = 0;
            for (Base base : bases) {
                if (base.isConfirmed()) {
                    total += plugin.getDatabaseManager().getBaseTrusted(base.getId()).size();
                }
            }
            return String.valueOf(total);
        }
        
        // === PRIMARY BASE STATS ===
        
        if (params.startsWith("base_")) {
            if (primaryBase == null) return "N/A";
            
            BoundingBox bounds = primaryBase.getBounds();
            String subParam = params.substring(5); // Remove "base_"
            
            switch (subParam) {
                case "world" -> { return primaryBase.getWorldName(); }
                case "dimension" -> { return getDimensionName(primaryBase.getWorldName()); }
                case "width" -> { return String.valueOf(bounds.getWidth()); }
                case "length" -> { return String.valueOf(bounds.getLength()); }
                case "height" -> { return String.valueOf(bounds.getHeight()); }
                case "volume" -> { return String.valueOf(bounds.getWidth() * bounds.getLength() * bounds.getHeight()); }
                case "volume_formatted" -> { return String.format("%,d", bounds.getWidth() * bounds.getLength() * bounds.getHeight()); }
                case "center_x" -> { return String.valueOf((bounds.getMinX() + bounds.getMaxX()) / 2); }
                case "center_y" -> { return String.valueOf((bounds.getMinY() + bounds.getMaxY()) / 2); }
                case "center_z" -> { return String.valueOf((bounds.getMinZ() + bounds.getMaxZ()) / 2); }
                case "center" -> { 
                    return String.format("%d, %d, %d",
                            (bounds.getMinX() + bounds.getMaxX()) / 2,
                            (bounds.getMinY() + bounds.getMaxY()) / 2,
                            (bounds.getMinZ() + bounds.getMaxZ()) / 2);
                }
                case "min_x" -> { return String.valueOf(bounds.getMinX()); }
                case "min_y" -> { return String.valueOf(bounds.getMinY()); }
                case "min_z" -> { return String.valueOf(bounds.getMinZ()); }
                case "max_x" -> { return String.valueOf(bounds.getMaxX()); }
                case "max_y" -> { return String.valueOf(bounds.getMaxY()); }
                case "max_z" -> { return String.valueOf(bounds.getMaxZ()); }
                case "size" -> { return bounds.getWidth() + "×" + bounds.getLength() + "×" + bounds.getHeight(); }
                case "created" -> { return dateFormat.format(primaryBase.getCreatedAt()); }
                case "confirmed" -> { return primaryBase.isConfirmed() ? "true" : "false"; }
                case "trusted" -> { return String.valueOf(plugin.getDatabaseManager().getBaseTrusted(primaryBase.getId()).size()); }
                case "id" -> { return String.valueOf(primaryBase.getId()); }
            }
        }
        
        // === COMBAT STATUS ===
        
        if (params.equalsIgnoreCase("combat")) {
            if (player == null) return "false";
            return plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player) ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("combat_time")) {
            if (player == null) return "0";
            return plugin.getCombatManager() != null ? 
                    String.valueOf(plugin.getCombatManager().getCombatTimeRemaining(player)) : "0";
        }
        
        // === DETECTION STATUS ===
        
        if (params.equalsIgnoreCase("pending")) {
            // Check if player has active detection prompt
            return "false"; // This would require tracking in DetectionManager
        }
        
        if (params.equalsIgnoreCase("threshold")) {
            return String.valueOf(plugin.getConfigManager().getDetectionThreshold());
        }
        
        if (params.equalsIgnoreCase("score_percent")) {
            double score = plugin.getScoreManager().getScore(offlinePlayer.getUniqueId());
            double threshold = plugin.getConfigManager().getDetectionThreshold();
            return String.format("%.0f", (score / threshold) * 100);
        }
        
        // === LOCATION CONTEXT ===
        
        if (params.equalsIgnoreCase("in_base")) {
            if (player == null) return "false";
            if (primaryBase == null) return "false";
            Location loc = player.getLocation();
            return primaryBase.getBounds().contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()) ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("in_any_base")) {
            if (player == null) return "false";
            Base baseAt = plugin.getProtectionManager().getBaseAtLocation(player.getLocation());
            return baseAt != null ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("base_owner")) {
            if (player == null) return "";
            Base baseAt = plugin.getProtectionManager().getBaseAtLocation(player.getLocation());
            if (baseAt == null) return "";
            OfflinePlayer owner = Bukkit.getOfflinePlayer(baseAt.getOwnerUUID());
            return owner.getName() != null ? owner.getName() : "Unknown";
        }
        
        // === PROTECTION STATUS ===
        
        if (params.equalsIgnoreCase("protection_active")) {
            if (primaryBase == null) return "false";
            OfflinePlayer owner = Bukkit.getOfflinePlayer(primaryBase.getOwnerUUID());
            // Protection is active when owner is offline (config setting for when offline protection is enabled)
            return !owner.isOnline() ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("owner_online")) {
            if (player == null) return "false";
            Base baseAt = plugin.getProtectionManager().getBaseAtLocation(player.getLocation());
            if (baseAt == null) return "false";
            return Bukkit.getOfflinePlayer(baseAt.getOwnerUUID()).isOnline() ? "true" : "false";
        }
        
        // === CONFIG VALUES ===
        
        if (params.equalsIgnoreCase("config_threshold")) {
            return String.valueOf(plugin.getConfigManager().getDetectionThreshold());
        }
        
        if (params.equalsIgnoreCase("config_max_bases")) {
            return String.valueOf(plugin.getConfigManager().getMaxBasesPerPlayer());
        }
        
        if (params.equalsIgnoreCase("config_teleport_cost")) {
            return String.valueOf(plugin.getConfigManager().getTeleportCost());
        }
        
        if (params.equalsIgnoreCase("config_teleport_cooldown")) {
            return String.valueOf(plugin.getConfigManager().getTeleportCooldown());
        }
        
        return null;
    }
    
    private String getDimensionName(String worldName) {
        var world = Bukkit.getWorld(worldName);
        if (world == null) return "Unknown";
        
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            case NORMAL -> "Overworld";
            default -> "Custom";
        };
    }
}
