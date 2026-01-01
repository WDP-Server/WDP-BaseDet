package com.wdp.basedet.ui.menu;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
 * 
 * Standard Layout (slots 45-53):
 * - Slot 45: Balance display (gold nugget with coins/tokens)
 * - Slots 46-47: Glass filler
 * - Slot 48: Previous page (when page > 1)
 * - Slot 49: Page info
 * - Slot 50: Next page (when page < total)
 * - Slots 51-52: Glass filler
 * - Slot 53: Back (when previous_menu exists) OR Close (when no previous_menu)
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
     * @param context Context data for placeholders (page, total_pages, previous_menu, coins, tokens, etc.)
     */
    @SuppressWarnings("unchecked")
    public void applyNavbar(Inventory inv, Player player, String menuType, Map<String, Object> context) {
        FileConfiguration config = navbarConfigs.get("default");
        if (config == null) {
            createFallbackNavbar(inv, player, menuType, context);
            return;
        }

        // Check for exceptions first
        if (isExceptionMenu(config, menuType)) {
            applyExceptionNavbar(inv, config, menuType, context);
            return;
        }

        ConfigurationSection navbarSection = config.getConfigurationSection("navbar");
        if (navbarSection == null) {
            createFallbackNavbar(inv, player, menuType, context);
            return;
        }

        // First, fill all navbar slots with glass panes as default
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, globalItems.createGlassPane());
        }

        // Process each navbar item
        for (String itemName : navbarSection.getKeys(false)) {
            ConfigurationSection itemSection = navbarSection.getConfigurationSection(itemName);
            if (itemSection == null) continue;

            // Handle glass_fill with multiple slots
            if ("glass_fill".equals(itemName)) {
                List<Integer> slots = itemSection.getIntegerList("slots");
                ItemStack glassItem = createNavbarItemFromSection(itemSection, player, context);
                if (glassItem != null) {
                    for (int slot : slots) {
                        if (slot >= 45 && slot <= 53) {
                            inv.setItem(slot, glassItem);
                        }
                    }
                }
                continue;
            }

            int slot = itemSection.getInt("slot", -1);
            if (slot < 45 || slot > 53) continue;

            // Handle back button - only show if previous_menu exists
            if ("back".equals(itemName)) {
                if (context == null || !context.containsKey("previous_menu")) {
                    continue; // Skip back button, close will be shown instead
                }
                inv.setItem(slot, createNavbarItemFromSection(itemSection, player, context));
                continue;
            }

            // Handle close button - only show if NO previous_menu
            if ("close".equals(itemName)) {
                if (context != null && context.containsKey("previous_menu")) {
                    continue; // Skip close button, back will be shown instead
                }
                inv.setItem(slot, createNavbarItemFromSection(itemSection, player, context));
                continue;
            }

            // Handle balance display
            if ("balance".equals(itemName)) {
                inv.setItem(slot, createNavbarItemFromSection(itemSection, player, context));
                continue;
            }

            // Handle pagination buttons
            if ("previous_page".equals(itemName)) {
                if (context == null || !context.containsKey("page") || !context.containsKey("total_pages")) {
                    continue;
                }
                int page = getContextInt(context, "page", 1);
                if (page <= 1) {
                    continue; // Already on first page
                }
                inv.setItem(slot, createNavbarItemFromSection(itemSection, player, context));
                continue;
            }

            if ("next_page".equals(itemName)) {
                if (context == null || !context.containsKey("page") || !context.containsKey("total_pages")) {
                    continue;
                }
                int page = getContextInt(context, "page", 1);
                int totalPages = getContextInt(context, "total_pages", 1);
                if (page >= totalPages) {
                    continue; // Already on last page
                }
                inv.setItem(slot, createNavbarItemFromSection(itemSection, player, context));
                continue;
            }

            if ("page_info".equals(itemName)) {
                if (context == null || !context.containsKey("page") || !context.containsKey("total_pages")) {
                    continue;
                }
                inv.setItem(slot, createNavbarItemFromSection(itemSection, player, context));
                continue;
            }

            // Default: create the item
            ItemStack item = createNavbarItemFromSection(itemSection, player, context);
            if (item != null) {
                inv.setItem(slot, item);
            }
        }
    }

    /**
     * Check if this menu type is in the exceptions list
     */
    private boolean isExceptionMenu(FileConfiguration config, String menuType) {
        ConfigurationSection exceptions = config.getConfigurationSection("exceptions");
        if (exceptions == null) return false;

        for (String exceptionType : exceptions.getKeys(false)) {
            List<String> menuList = exceptions.getStringList(exceptionType);
            if (menuList.contains(menuType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Apply navbar for exception menus (e.g., skill selection - only back button)
     */
    private void applyExceptionNavbar(Inventory inv, FileConfiguration config, String menuType, Map<String, Object> context) {
        // Fill with glass first
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, globalItems.createGlassPane());
        }

        ConfigurationSection exceptions = config.getConfigurationSection("exceptions");
        if (exceptions == null) return;

        // Determine which exception type applies
        for (String exceptionType : exceptions.getKeys(false)) {
            List<String> menuList = exceptions.getStringList(exceptionType);
            if (!menuList.contains(menuType)) continue;

            if ("skill_selection".equals(exceptionType)) {
                // Only show back button at slot 53
                ConfigurationSection navbarSection = config.getConfigurationSection("navbar.back");
                if (navbarSection != null) {
                    inv.setItem(53, createNavbarItemFromSection(navbarSection, null, context));
                } else {
                    inv.setItem(53, globalItems.createBackItem("previous menu"));
                }
            } else if ("no_balance".equals(exceptionType)) {
                // Hide balance, show everything else normally
                // Re-apply navbar but skip balance
                applyNavbarWithoutBalance(inv, config, menuType, context);
            }
            break;
        }
    }

    /**
     * Apply navbar without balance display
     */
    private void applyNavbarWithoutBalance(Inventory inv, FileConfiguration config, String menuType, Map<String, Object> context) {
        ConfigurationSection navbarSection = config.getConfigurationSection("navbar");
        if (navbarSection == null) return;

        for (String itemName : navbarSection.getKeys(false)) {
            if ("balance".equals(itemName)) continue; // Skip balance

            ConfigurationSection itemSection = navbarSection.getConfigurationSection(itemName);
            if (itemSection == null) continue;

            if ("glass_fill".equals(itemName)) {
                List<Integer> slots = itemSection.getIntegerList("slots");
                // Add slot 45 to glass fill since balance is hidden
                if (!slots.contains(45)) {
                    slots = new ArrayList<>(slots);
                    slots.add(45);
                }
                ItemStack glassItem = createNavbarItemFromSection(itemSection, null, context);
                if (glassItem != null) {
                    for (int slot : slots) {
                        if (slot >= 45 && slot <= 53) {
                            inv.setItem(slot, glassItem);
                        }
                    }
                }
                continue;
            }

            int slot = itemSection.getInt("slot", -1);
            if (slot < 45 || slot > 53) continue;

            ItemStack item = createNavbarItemFromSection(itemSection, null, context);
            if (item != null) {
                inv.setItem(slot, item);
            }
        }
    }

    /**
     * Create a navbar item from a configuration section
     */
    private ItemStack createNavbarItemFromSection(ConfigurationSection section, Player player, Map<String, Object> context) {
        String materialStr = section.getString("material");
        if (materialStr == null) return null;

        Material material = Material.getMaterial(materialStr.toUpperCase());
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = section.getString("display_name");
        if (displayName != null) {
            meta.setDisplayName(replacePlaceholders(displayName, player, context));
        }

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
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
     * Get integer from context with default value
     */
    private int getContextInt(Map<String, Object> context, String key, int defaultValue) {
        if (context == null) return defaultValue;
        Object value = context.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
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
            result = result.replace("{player_name}", player.getName());
        }
        
        // Replace context values
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        
        return result;
    }

    /**
     * Create a fallback navbar when YAML is not available
     */
    private void createFallbackNavbar(Inventory inv, Player player, String menuType, Map<String, Object> context) {
        // Fill row 5 with glass
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, globalItems.createGlassPane());
        }
        
        // Balance at slot 45
        double coins = context != null ? getContextDouble(context, "coins", 0) : 0;
        double tokens = context != null ? getContextDouble(context, "tokens", 0) : 0;
        inv.setItem(45, globalItems.createBalanceItem(coins, tokens));
        
        // Previous page at 48 if needed
        if (context != null && context.containsKey("page") && context.containsKey("total_pages")) {
            int page = getContextInt(context, "page", 1);
            int totalPages = getContextInt(context, "total_pages", 1);
            
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
     * Get double from context with default value
     */
    private double getContextDouble(Map<String, Object> context, String key, double defaultValue) {
        if (context == null) return defaultValue;
        Object value = context.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Helper method to get integer from config map
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
     * Reload navbar configuration
     */
    public void reload() {
        navbarConfigs.clear();
        loadNavbarConfig();
    }

    /**
     * Get the navbar action for a specific slot
     * This provides universal slot-based action determination
     * 
     * @param slot The slot number (45-53)
     * @param context The context to check for previous_menu
     * @return The NavbarAction for this slot
     */
    public NavbarAction getNavbarAction(int slot, Map<String, Object> context) {
        if (slot < 45 || slot > 53) return NavbarAction.NONE;
        
        // Standard navbar layout
        return switch (slot) {
            case 45 -> NavbarAction.INFO; // Balance
            case 46, 47, 51, 52 -> NavbarAction.NONE; // Glass fillers
            case 48 -> NavbarAction.PREVIOUS_PAGE;
            case 49 -> NavbarAction.PAGE_INFO;
            case 50 -> NavbarAction.NEXT_PAGE;
            case 53 -> {
                // Back or Close depending on context
                if (context != null && context.containsKey("previous_menu")) {
                    yield NavbarAction.BACK;
                } else {
                    yield NavbarAction.CLOSE;
                }
            }
            default -> NavbarAction.NONE;
        };
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