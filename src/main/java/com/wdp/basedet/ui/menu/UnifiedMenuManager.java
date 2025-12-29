package com.wdp.basedet.ui.menu;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Menu Manager - handles navbar from YAML configuration
 * All WDP plugins will use this exact same system
 */
public class UnifiedMenuManager {

    private final WDPBaseDetPlugin plugin;
    private final GlobalItems globalItems;
    private final Map<String, FileConfiguration> navbarConfigs;

    public UnifiedMenuManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.globalItems = new GlobalItems(plugin);
        this.navbarConfigs = new HashMap<>();
        loadNavbarConfig();
    }

    /**
     * Load navbar configuration from YAML file
     */
    private void loadNavbarConfig() {
        try {
            // Try to load from plugin folder first
            File configFile = new File(plugin.getDataFolder(), "navbar.yml");
            if (!configFile.exists()) {
                // Create default navbar.yml
                plugin.saveResource("navbar.yml", false);
            }
            
            // Load the config
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            
            // If file is empty, load defaults from resources
            if (config.getKeys(false).isEmpty()) {
                InputStream defaultStream = plugin.getResource("navbar.yml");
                if (defaultStream != null) {
                    config = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                }
            }
            
            navbarConfigs.put("default", config);
            plugin.getLogger().info("Loaded navbar configuration");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load navbar.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply navbar to an inventory
     * This reads from navbar.yml and creates the bottom row (slots 45-53)
     * 
     * @param inv The inventory to apply navbar to
     * @param player The player viewing the menu
     * @param menuType The type of menu (e.g., "main", "trust", "quest")
     * @param context Context data for placeholders (menu_name, menu_description, page, total_pages, previous_menu, etc.)
     */
    public void applyNavbar(Inventory inv, Player player, String menuType, Map<String, Object> context) {
        FileConfiguration config = navbarConfigs.get("default");
        if (config == null) {
            // Fallback: create basic navbar
            createFallbackNavbar(inv, player, menuType, context);
            return;
        }

        // Get navbar section
        Map<String, Object> navbar = config.getConfigurationSection("navbar").getValues(false);
        
        // Apply each navbar item
        for (Map.Entry<String, Object> entry : navbar.entrySet()) {
            String itemName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) continue;
            
            Map<String, Object> itemConfig = (Map<String, Object>) entry.getValue();
            Integer slot = getInteger(itemConfig, "slot");
            
            if (slot == null || slot < 45 || slot > 53) continue;
            
            // Handle special cases
            if ("back".equals(itemName)) {
                // Only show back if previous_menu is provided
                if (context == null || !context.containsKey("previous_menu")) {
                    inv.setItem(slot, globalItems.createGlassPane());
                    continue;
                }
            }
            
            if ("close".equals(itemName)) {
                // Only show close if no previous_menu
                if (context != null && context.containsKey("previous_menu")) {
                    inv.setItem(slot, globalItems.createGlassPane());
                    continue;
                }
            }
            
            if ("previous_page".equals(itemName) || "next_page".equals(itemName)) {
                // Only show pagination if page context is provided
                if (context == null || !context.containsKey("page") || !context.containsKey("total_pages")) {
                    inv.setItem(slot, globalItems.createGlassPane());
                    continue;
                }
                
                int page = (Integer) context.get("page");
                int totalPages = (Integer) context.get("total_pages");
                
                if ("previous_page".equals(itemName) && page <= 1) {
                    inv.setItem(slot, globalItems.createGlassPane());
                    continue;
                }
                
                if ("next_page".equals(itemName) && page >= totalPages) {
                    inv.setItem(slot, globalItems.createGlassPane());
                    continue;
                }
            }
            
            if ("page_info".equals(itemName)) {
                // Only show page info if page context is provided
                if (context == null || !context.containsKey("page") || !context.containsKey("total_pages")) {
                    inv.setItem(slot, globalItems.createGlassPane());
                    continue;
                }
            }
            
            ItemStack item = createNavbarItem(itemName, itemConfig, player, menuType, context);
            if (item != null) {
                inv.setItem(slot, item);
            }
        }

        // Handle exceptions (menus that should only show back button)
        if (config.contains("exceptions")) {
            Map<String, Object> exceptions = config.getConfigurationSection("exceptions").getValues(false);
            for (Map.Entry<String, Object> entry : exceptions.entrySet()) {
                String exceptionType = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> menuList = (List<String>) value;
                    if (menuList.contains(menuType)) {
                        // Apply exception behavior
                        applyException(inv, exceptionType, context);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Create a navbar item based on configuration
     */
    private ItemStack createNavbarItem(String itemName, Map<String, Object> config, Player player, String menuType, Map<String, Object> context) {
        String materialStr = (String) config.get("material");
        String displayName = (String) config.get("display_name");
        List<String> lore = (List<String>) config.get("lore");
        
        if (materialStr == null) return null;
        
        Material material = Material.getMaterial(materialStr.toUpperCase());
        if (material == null) return null;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (displayName != null) {
            meta.setDisplayName(replacePlaceholders(displayName, player, context));
        }
        
        if (lore != null) {
            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                processedLore.add(replacePlaceholders(line, player, context));
            }
            meta.setLore(processedLore);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Replace placeholders in display names and lore
     */
    private String replacePlaceholders(String text, Player player, Map<String, Object> context) {
        if (text == null) return "";
        
        String result = text;
        
        // Replace player name
        if (player != null) {
            result = result.replace("{player}", player.getName());
        }
        
        // Replace context values
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        
        // Replace color codes (from &#RRGGBB format)
        result = replaceHexColors(result);
        
        return result;
    }

    /**
     * Replace hex color codes in format &#RRGGBB
     */
    private String replaceHexColors(String text) {
        // Simple hex replacement - convert &#RRGGBB to §x§R§R§G§G§B§B format
        return text.replaceAll("&#([A-Fa-f0-9]{6})", "§x§$1".replaceAll("(.)", "§$1"));
    }

    /**
     * Create a fallback navbar when YAML is not available
     */
    private void createFallbackNavbar(Inventory inv, Player player, String menuType, Map<String, Object> context) {
        // Fill row 5 with glass
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, globalItems.createGlassPane());
        }
        
        // Info item at 45 if context provided
        if (context != null && context.containsKey("menu_name")) {
            String menuName = (String) context.get("menu_name");
            String description = (String) context.getOrDefault("menu_description", "");
            
            List<String> lore = new ArrayList<>();
            if (!description.isEmpty()) {
                lore.add("");
                lore.add("§7" + description);
                lore.add("");
            }
            
            inv.setItem(45, globalItems.createInfoItem("§b§l" + menuName, lore));
        }
        
        // Previous page at 48 if needed
        if (context != null && context.containsKey("page") && context.containsKey("total_pages")) {
            int page = (Integer) context.get("page");
            int totalPages = (Integer) context.get("total_pages");
            
            if (page > 1) {
                inv.setItem(48, globalItems.createPreviousPageItem(page));
            }
            
            // Page info at 49
            inv.setItem(49, globalItems.createPageInfoItem(page, totalPages));
            
            // Next page at 50 if needed
            if (page < totalPages) {
                inv.setItem(50, globalItems.createNextPageItem(page, totalPages));
            }
        }
        
        // Back/Close at 53
        String previousMenu = context != null ? (String) context.get("previous_menu") : null;
        if (previousMenu != null) {
            inv.setItem(53, globalItems.createBackItem(previousMenu));
        } else {
            inv.setItem(53, globalItems.createCloseItem());
        }
    }

    /**
     * Apply exception behavior for specific menu types
     */
    private void applyException(Inventory inv, String exceptionType, Map<String, Object> context) {
        if ("skill_selection".equals(exceptionType)) {
            // For skill selection menus, only show back button at slot 45
            // Clear other navbar items
            for (int i = 45; i <= 53; i++) {
                inv.setItem(i, globalItems.createGlassPane());
            }
            // Back button at 45
            String previousMenu = (String) context.get("previous_menu");
            inv.setItem(45, globalItems.createBackItem(previousMenu));
        }
    }

    /**
     * Helper method to get integer from config
     */
    private Integer getInteger(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * Get the GlobalItems instance
     */
    public GlobalItems getGlobalItems() {
        return globalItems;
    }

    /**
     * Handle navbar click based on slot position (universal system)
     * This is more robust than checking display names
     */
    public NavbarAction getNavbarAction(int slot) {
        if (slot < 45 || slot > 53) return null;
        
        FileConfiguration config = navbarConfigs.get("default");
        if (config == null) return null;
        
        // Get the navbar item configuration for this slot
        Map<String, Object> navbar = config.getConfigurationSection("navbar").getValues(false);
        
        for (Map.Entry<String, Object> entry : navbar.entrySet()) {
            String itemName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) continue;
            
            Map<String, Object> itemConfig = (Map<String, Object>) entry.getValue();
            Integer itemSlot = getInteger(itemConfig, "slot");
            
            if (itemSlot != null && itemSlot == slot) {
                // Return the action based on the item type
                return switch (itemName) {
                    case "back" -> NavbarAction.BACK;
                    case "close" -> NavbarAction.CLOSE;
                    case "previous_page" -> NavbarAction.PREVIOUS_PAGE;
                    case "next_page" -> NavbarAction.NEXT_PAGE;
                    case "page_info" -> NavbarAction.PAGE_INFO;
                    case "balance", "info" -> NavbarAction.INFO;
                    default -> NavbarAction.NONE;
                };
            }
        }
        
        return NavbarAction.NONE;
    }
    
    /**
     * Universal navbar actions enum
     */
    public enum NavbarAction {
        BACK,
        CLOSE,
        PREVIOUS_PAGE,
        NEXT_PAGE,
        PAGE_INFO,
        INFO,
        NONE
    }
}