package com.wdp.basedet.ui;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.MessageManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import com.wdp.basedet.model.TrustEntry;
import com.wdp.basedet.ui.menu.UnifiedMenuManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive Menu System for WDP-BaseDet
 * SkillCoins-style navbar at bottom (slots 45-53)
 * Professional design with hex colors
 * 
 * Menus:
 * 1. Main Base Menu - Dashboard with overview
 * 2. Base Settings Menu - Protection settings
 * 3. Trust Manager Menu - List trusted players
 * 4. Trust Permissions Menu (2 pages) - Edit permissions
 * 5. Base Stats Menu - Detailed statistics
 */
public class MenuManager implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final UnifiedMenuManager unifiedMenuManager;
    private final MessageManager messages;
    
    // Track open menus
    private final Map<UUID, MenuSession> openMenus = new ConcurrentHashMap<>();
    
    // Track transitions between menus
    private final Set<UUID> transitioning = ConcurrentHashMap.newKeySet();
    
    // Track pending teleports
    private final Map<UUID, TeleportTask> pendingTeleports = new ConcurrentHashMap<>();
    
    // Track teleport cooldowns
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    
    // Menu identifiers (use Unicode for uniqueness)
    private static final String MENU_ID = "§8§l";
    
    public MenuManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.unifiedMenuManager = new UnifiedMenuManager(plugin);
        this.messages = plugin.getMessages();
    }
    
    // ==================== MAIN BASE MENU ====================
    
    /**
     * Open the main base menu
     */
    public void openMainMenu(Player player, Base base) {
        String title = hex("#808080") + "✦ Base Management";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // ===== HEADER: Base Overview =====
        inv.setItem(4, createBaseOverviewItem(base, player));
        
        // ===== ROW 2: MAIN ACTIONS (slots 19-25) =====
        
        // Trust Manager
        inv.setItem(20, createMenuItem(
                Material.PLAYER_HEAD,
                hex("#55FFFF") + "✦ Trust Manager",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Manage player permissions",
                        hex("#666666") + "Trusted: " + hex("#FFFFFF") + 
                                plugin.getDatabaseManager().getBaseTrusted(base.getId()).size(),
                        "",
                        hex("#FFFF55") + "▸ Click to open"
                ),
                true
        ));
        
        // Base Stats
        inv.setItem(22, createMenuItem(
                Material.BOOK,
                hex("#55FF55") + "✦ Statistics",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "View base details",
                        hex("#AAAAAA") + "and statistics",
                        "",
                        hex("#FFFF55") + "▸ Click to view"
                ),
                false
        ));
        
        // Protection Info
        inv.setItem(24, createMenuItem(
                Material.SHIELD,
                hex("#FFAA00") + "✦ Protection",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "View protection status",
                        hex("#55FF55") + "✓ Active",
                        "",
                        hex("#FFFF55") + "▸ Click for details"
                ),
                false
        ));
        
        // ===== ROW 3: TOOLS & UTILITIES (slots 28-34) =====
        
        // Particles toggle
        if (plugin.getConfigManager().areParticlesEnabled() && plugin.getConfigManager().isAllowParticleToggle()) {
            boolean viewing = plugin.getParticleManager().isViewing(player.getUniqueId());
            inv.setItem(29, createMenuItem(
                    Material.END_ROD,
                    (viewing ? hex("#55FF55") : hex("#888888")) + "Particles",
                    Arrays.asList(
                            "",
                            hex("#AAAAAA") + "Toggle boundary visibility",
                            hex("#666666") + "Status: " + (viewing ? hex("#55FF55") + "ON" : hex("#888888") + "OFF"),
                            "",
                            hex("#FFFF55") + "▸ Click to toggle"
                    ),
                    false
            ));
        }
        
        // Teleport
        if (plugin.getConfigManager().isTeleportEnabled()) {
            inv.setItem(31, createTeleportItem(player, base));
        }
        
        // Selector Tool
        if (plugin.getConfigManager().isSelectorEnabled()) {
            inv.setItem(33, createMenuItem(
                    Material.BLAZE_ROD,
                    hex("#FFD700") + "Modify Tool",
                    Arrays.asList(
                            "",
                            hex("#AAAAAA") + "Adjust base boundaries",
                            hex("#666666") + "Cost: " + hex("#FFD700") + 
                                    plugin.getConfigManager().getSelectorCostPerBlock() + "/block",
                            "",
                            hex("#FFFF55") + "▸ Click to get"
                    ),
                    false
            ));
        }
        
        // ===== ROW 4: DANGER ZONE =====
        inv.setItem(40, createMenuItem(
                Material.BARRIER,
                hex("#FF5555") + "Abandon Base",
                Arrays.asList(
                        "",
                        hex("#FF5555") + "⚠ PERMANENT ACTION",
                        hex("#AAAAAA") + "Removes base protection",
                        "",
                        hex("#FFFF55") + "▸ Shift-Click to confirm"
                ),
                false
        ));
        
        // Add navbar
        addMainNavbar(inv, player, "main");
        
        // Track menu with empty navbar context (no previous menu)
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.MAIN, base, null, 1, null, new HashMap<>()));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }
    
    // ==================== BASE SELECTOR MENU ====================
    
    /**
     * Open the base selector menu when player has multiple bases
     * Uses biome/dimension as icon, center-aligned
     */
    public void openBaseSelector(Player player, List<Base> bases) {
        String title = hex("#808080") + "✦ Select a Base";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // Header
        inv.setItem(4, createMenuItem(
                Material.COMPASS,
                hex("#FFD700") + "✦ Your Bases",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "You have " + hex("#FFFFFF") + bases.size() + hex("#AAAAAA") + " bases.",
                        "",
                        hex("#FFFF55") + "Click a base to manage it"
                ),
                false
        ));
        
        // Calculate center-aligned slots based on number of bases
        int[] slots = getCenteredSlots(bases.size());
        
        for (int i = 0; i < Math.min(bases.size(), slots.length); i++) {
            Base base = bases.get(i);
            inv.setItem(slots[i], createBaseSelectorItem(base, i + 1));
        }
        
        // Add selector navbar
        addSelectorNavbar(inv, player);
        
        // Track menu with list of bases stored
        Map<String, Object> data = new HashMap<>();
        data.put("bases", bases);
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.BASE_SELECTOR, null, null, 1, data, new HashMap<>()));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }
    
    /**
     * Get center-aligned slots for base icons
     */
    private int[] getCenteredSlots(int count) {
        // Row 2 (slots 10-16) and Row 3 (slots 19-25) for bases
        return switch (count) {
            case 1 -> new int[]{22}; // Center
            case 2 -> new int[]{21, 23}; // Two centered
            case 3 -> new int[]{20, 22, 24}; // Three centered
            case 4 -> new int[]{19, 21, 23, 25}; // Four spread
            case 5 -> new int[]{20, 22, 24, 29, 33}; // Five (3 top, 2 bottom)
            case 6 -> new int[]{20, 22, 24, 29, 31, 33}; // Six (3 + 3)
            default -> new int[]{19, 20, 21, 22, 23, 24, 25}; // Max 7
        };
    }
    
    /**
     * Create a base selector item with dimension/biome icon
     */
    private ItemStack createBaseSelectorItem(Base base, int number) {
        World world = Bukkit.getWorld(base.getWorldName());
        Material icon = getDimensionIcon(world);
        String dimensionName = getDimensionName(world);
        
        BoundingBox bounds = base.getBounds();
        int volume = bounds.getWidth() * bounds.getLength() * bounds.getHeight();
        
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(hex("#55FFFF") + "✦ Base #" + number + " " + hex("#666666") + "(" + dimensionName + ")");
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(hex("#AAAAAA") + "World: " + hex("#FFFFFF") + base.getWorldName());
        lore.add(hex("#AAAAAA") + "Size: " + hex("#55FF55") + bounds.getWidth() + "×" + bounds.getLength() + "×" + bounds.getHeight());
        lore.add(hex("#AAAAAA") + "Volume: " + hex("#FFD700") + String.format("%,d", volume) + " blocks");
        lore.add("");
        lore.add(hex("#AAAAAA") + "Center: " + hex("#55FFFF") + 
                ((bounds.getMinX() + bounds.getMaxX()) / 2) + ", " + 
                ((bounds.getMinY() + bounds.getMaxY()) / 2) + ", " + 
                ((bounds.getMinZ() + bounds.getMaxZ()) / 2));
        lore.add("");
        lore.add(hex("#55FF55") + "✓ " + hex("#AAAAAA") + "Protected & Confirmed");
        lore.add("");
        lore.add(hex("#FFFF55") + "▸ Click to manage this base");
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Get icon material based on dimension
     */
    private Material getDimensionIcon(World world) {
        if (world == null) return Material.GRASS_BLOCK;
        
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            case NORMAL -> Material.GRASS_BLOCK;
            default -> Material.STONE;
        };
    }
    
    /**
     * Get dimension display name
     */
    private String getDimensionName(World world) {
        if (world == null) return "Unknown";
        
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            case NORMAL -> "Overworld";
            default -> "Custom";
        };
    }
    

    
    // ==================== TRUST MANAGER MENU ====================
    
    /**
     * Open the trust management menu
     */
    public void openTrustMenu(Player player, Base base) {
        String title = hex("#808080") + "✦ Trust Manager";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // Header
        inv.setItem(4, createMenuItem(
                Material.SHIELD,
                hex("#FFD700") + "✦ Trusted Players",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Players who can interact with",
                        hex("#AAAAAA") + "your base based on their",
                        hex("#AAAAAA") + "permission settings.",
                        "",
                        hex("#FFFF55") + "Click a player to edit permissions"
                ),
                false
        ));
        
        // Add trusted players (rows 2-4, columns 1-7)
        List<TrustEntry> trusted = plugin.getDatabaseManager().getBaseTrusted(base.getId());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        
        for (int i = 0; i < Math.min(trusted.size(), slots.length); i++) {
            TrustEntry entry = trusted.get(i);
            inv.setItem(slots[i], createTrustedPlayerItem(entry));
        }
        
        // Add player button (slot 40)
        inv.setItem(40, createMenuItem(
                Material.EMERALD,
                hex("#55FF55") + "➕ Add Trusted Player",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Add a new player to your",
                        hex("#AAAAAA") + "trusted list.",
                        "",
                        hex("#FFFF55") + "▸ Click and type their name",
                        hex("#666666") + "Or use: /trust <name>"
                ),
                false
        ));
        
        // Add navbar
        addTrustNavbar(inv, player, 1, 1);
        
        // Track menu with previous_menu context
        Map<String, Object> navbarContext = new HashMap<>();
        navbarContext.put("previous_menu", "main");
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.TRUST_LIST, base, null, 1, null, navbarContext));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== TRUST PERMISSIONS MENU ====================
    
    /**
     * Open permissions editing menu for a trusted player (2 pages)
     */
    public void openPermissionsMenu(Player player, Base base, TrustEntry trustEntry, int page) {
        String trustedName = plugin.getTrustManager().getPlayerName(trustEntry.getTrustedUUID());
        String title = hex("#808080") + "✦ " + trustedName + " (" + page + "/2)";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // Header (player head)
        ItemStack header = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) header.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(trustEntry.getTrustedUUID()));
        skullMeta.setDisplayName(hex("#55FFFF") + "✦ " + trustedName);
        List<String> headerLore = new ArrayList<>();
        headerLore.add("");
        headerLore.add(hex("#AAAAAA") + "Configure permissions for");
        headerLore.add(hex("#AAAAAA") + "this player in your base.");
        headerLore.add("");
        headerLore.add(hex("#55FF55") + "Online Perms: " + formatPerms(trustEntry, true));
        headerLore.add(hex("#FFFF55") + "Offline Perms: " + formatPerms(trustEntry, false));
        skullMeta.setLore(headerLore);
        header.setItemMeta(skullMeta);
        inv.setItem(4, header);
        
        if (page == 1) {
            // ===== PAGE 1: BASIC PERMISSIONS =====
            
            // Online section header
            inv.setItem(10, createSectionDivider(hex("#55FF55") + "━━━ When You're ONLINE ━━━", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(11, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(12, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(14, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(15, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(16, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            
            // Online permissions (row 3)
            inv.setItem(19, createPermToggle("Break Blocks", Material.DIAMOND_PICKAXE, trustEntry.canBreakOnline(), "break_online",
                    "Mine and break blocks", "in your base"));
            inv.setItem(20, createPermToggle("Place Blocks", Material.BRICKS, trustEntry.canPlaceOnline(), "place_online",
                    "Place and build", "in your base"));
            inv.setItem(21, createPermToggle("Containers", Material.CHEST, trustEntry.canContainerOnline(), "container_online",
                    "Open chests, barrels", "and other storage"));
            inv.setItem(22, createPermToggle("Doors & Gates", Material.OAK_DOOR, trustEntry.canDoorOnline(), "door_online",
                    "Use doors, trapdoors", "and fence gates"));
            
            // Offline section header
            inv.setItem(28, createSectionDivider(hex("#FFFF55") + "━━━ When You're OFFLINE ━━━", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(29, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(30, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(32, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(33, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(34, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            
            // Offline permissions (row 5)
            inv.setItem(37, createPermToggle("Break Blocks", Material.IRON_PICKAXE, trustEntry.canBreakOffline(), "break_offline",
                    "Break blocks while", "you're offline"));
            inv.setItem(38, createPermToggle("Place Blocks", Material.COBBLESTONE, trustEntry.canPlaceOffline(), "place_offline",
                    "Place blocks while", "you're offline"));
            inv.setItem(39, createPermToggle("Containers", Material.BARREL, trustEntry.canContainerOffline(), "container_offline",
                    "Access storage while", "you're offline"));
            inv.setItem(40, createPermToggle("Doors & Gates", Material.IRON_DOOR, trustEntry.canDoorOffline(), "door_offline",
                    "Use doors while", "you're offline"));
            
        } else {
            // ===== PAGE 2: ADVANCED PERMISSIONS =====
            
            // Online section header
            inv.setItem(10, createSectionDivider(hex("#55FF55") + "━━━ When You're ONLINE ━━━", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(11, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(12, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(14, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(15, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            inv.setItem(16, createSectionDivider("", Material.LIME_STAINED_GLASS_PANE));
            
            // Advanced online permissions
            inv.setItem(19, createPermToggle("Redstone", Material.REDSTONE, trustEntry.canRedstoneOnline(), "redstone_online",
                    "Use buttons, levers", "pressure plates"));
            inv.setItem(20, createPermToggle("Entities", Material.DIAMOND_SWORD, trustEntry.canEntityDamageOnline(), "entity_damage_online",
                    "Damage animals and", "mobs in your base"));
            inv.setItem(21, createPermToggle("Vehicles", Material.MINECART, trustEntry.canVehicleOnline(), "vehicle_online",
                    "Break and place boats", "minecarts, armor stands"));
            inv.setItem(22, createPermToggle("Decorations", Material.ITEM_FRAME, trustEntry.canDecorationOnline(), "decoration_online",
                    "Item frames, paintings", "leads, name tags"));
            
            // Offline section header
            inv.setItem(28, createSectionDivider(hex("#FFFF55") + "━━━ When You're OFFLINE ━━━", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(29, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(30, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(32, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(33, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(34, createSectionDivider("", Material.YELLOW_STAINED_GLASS_PANE));
            
            // Advanced offline permissions
            inv.setItem(37, createPermToggle("Redstone", Material.LEVER, trustEntry.canRedstoneOffline(), "redstone_offline",
                    "Use redstone devices", "while you're offline"));
            inv.setItem(38, createPermToggle("Entities", Material.IRON_SWORD, trustEntry.canEntityDamageOffline(), "entity_damage_offline",
                    "Damage entities while", "you're offline"));
            inv.setItem(39, createPermToggle("Vehicles", Material.OAK_BOAT, trustEntry.canVehicleOffline(), "vehicle_offline",
                    "Vehicle interactions", "while you're offline"));
            inv.setItem(40, createPermToggle("Decorations", Material.PAINTING, trustEntry.canDecorationOffline(), "decoration_offline",
                    "Decoration interactions", "while you're offline"));
        }
        
        // Add permissions navbar
        addPermissionsNavbar(inv, player, page, trustedName);
        
        // Track menu with previous_menu context
        Map<String, Object> navbarContext = new HashMap<>();
        navbarContext.put("previous_menu", "trust_list");
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.TRUST_PERMS, base, trustEntry, page, null, navbarContext));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== STATS MENU ====================
    
    /**
     * Open base statistics menu
     */
    public void openStatsMenu(Player player, Base base) {
        String title = hex("#808080") + "✦ Base Statistics";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        BoundingBox bounds = base.getBounds();
        int volume = bounds.getWidth() * bounds.getLength() * bounds.getHeight();
        List<TrustEntry> trusted = plugin.getDatabaseManager().getBaseTrusted(base.getId());
        
        // Header
        inv.setItem(4, createMenuItem(
                Material.FILLED_MAP,
                hex("#FFD700") + "✦ Base Statistics",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Detailed information about",
                        hex("#AAAAAA") + "your protected base."
                ),
                false
        ));
        
        // Size info (row 2)
        inv.setItem(19, createStatItem(
                Material.SCAFFOLDING,
                hex("#55FFFF") + "Dimensions",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Width: " + hex("#FFFFFF") + bounds.getWidth() + " blocks",
                        hex("#AAAAAA") + "Length: " + hex("#FFFFFF") + bounds.getLength() + " blocks",
                        hex("#AAAAAA") + "Height: " + hex("#FFFFFF") + bounds.getHeight() + " blocks"
                )
        ));
        
        inv.setItem(21, createStatItem(
                Material.DIAMOND_BLOCK,
                hex("#55FF55") + "Total Volume",
                Arrays.asList(
                        "",
                        hex("#FFD700") + String.format("%,d", volume) + hex("#AAAAAA") + " blocks",
                        "",
                        hex("#666666") + "Protected area"
                )
        ));
        
        inv.setItem(23, createStatItem(
                Material.COMPASS,
                hex("#FFAA00") + "Location",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "World: " + hex("#FFFFFF") + base.getWorldName(),
                        "",
                        hex("#AAAAAA") + "From: " + hex("#55FFFF") + bounds.getMinX() + ", " + bounds.getMinY() + ", " + bounds.getMinZ(),
                        hex("#AAAAAA") + "To: " + hex("#55FFFF") + bounds.getMaxX() + ", " + bounds.getMaxY() + ", " + bounds.getMaxZ()
                )
        ));
        
        inv.setItem(25, createStatItem(
                Material.PLAYER_HEAD,
                hex("#FF55FF") + "Trusted Players",
                Arrays.asList(
                        "",
                        hex("#FFD700") + String.valueOf(trusted.size()) + hex("#AAAAAA") + " players trusted",
                        "",
                        trusted.isEmpty() ? hex("#666666") + "No trusted players" :
                                hex("#666666") + "Click Trust Manager to edit"
                )
        ));
        
        // Detection info (row 4)
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        
        inv.setItem(29, createStatItem(
                Material.CLOCK,
                hex("#55FFFF") + "Created",
                Arrays.asList(
                        "",
                        hex("#FFFFFF") + sdf.format(base.getCreatedAt()),
                        "",
                        hex("#666666") + "When the base was detected"
                )
        ));
        
        inv.setItem(31, createStatItem(
                Material.TARGET,
                hex("#55FF55") + "Detection Status",
                Arrays.asList(
                        "",
                        hex("#55FF55") + "✓ Successfully Detected",
                        "",
                        hex("#666666") + "Automatic detection"
                )
        ));
        
        inv.setItem(33, createStatItem(
                base.isConfirmed() ? Material.LIME_WOOL : Material.RED_WOOL,
                (base.isConfirmed() ? hex("#55FF55") : hex("#FF5555")) + "Status",
                Arrays.asList(
                        "",
                        base.isConfirmed() ? hex("#55FF55") + "✓ Confirmed & Protected" : hex("#FF5555") + "✗ Not Confirmed",
                        "",
                        base.isConfirmed() ? hex("#666666") + "Protection is active" : hex("#666666") + "Use /basedet confirm"
                )
        ));
        
        // Add navbar
        addStatsNavbar(inv, player);
        
        // Track menu with previous_menu context
        Map<String, Object> navbarContext = new HashMap<>();
        navbarContext.put("previous_menu", "main");
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.STATS, base, null, 1, null, navbarContext));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== SETTINGS MENU ====================
    
    /**
     * Open base settings menu
     */
    public void openSettingsMenu(Player player, Base base) {
        String title = hex("#808080") + "✦ Protection Info";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // Header
        inv.setItem(4, createMenuItem(
                Material.SHIELD,
                hex("#FFD700") + "✦ Protection Information",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "View your base",
                        hex("#AAAAAA") + "protection status."
                ),
                false
        ));
        
        BoundingBox bounds = base.getBounds();
        int volume = bounds.getWidth() * bounds.getLength() * bounds.getHeight();
        List<TrustEntry> trusted = plugin.getDatabaseManager().getBaseTrusted(base.getId());
        
        // Protection status
        inv.setItem(20, createStatItem(
                Material.SHIELD,
                hex("#55FF55") + "Protection Status",
                Arrays.asList(
                        "",
                        base.isConfirmed() ? hex("#55FF55") + "✓ ACTIVE" : hex("#FF5555") + "✗ INACTIVE",
                        "",
                        hex("#AAAAAA") + "Your base is " + (base.isConfirmed() ? "protected" : "not confirmed"),
                        hex("#AAAAAA") + (base.isConfirmed() ? "from unauthorized access." : "yet. Use /base confirm")
                )
        ));
        
        inv.setItem(22, createStatItem(
                Material.PLAYER_HEAD,
                hex("#55FFFF") + "Trusted Players",
                Arrays.asList(
                        "",
                        hex("#FFD700") + String.valueOf(trusted.size()) + hex("#AAAAAA") + " trusted",
                        "",
                        hex("#AAAAAA") + "Players with access to",
                        hex("#AAAAAA") + "your protected base."
                )
        ));
        
        inv.setItem(24, createStatItem(
                Material.DIAMOND_BLOCK,
                hex("#FFD700") + "Protected Volume",
                Arrays.asList(
                        "",
                        hex("#FFFFFF") + String.format("%,d", volume) + hex("#AAAAAA") + " blocks",
                        "",
                        hex("#AAAAAA") + "Size: " + hex("#55FF55") + bounds.getWidth() + "×" + 
                                bounds.getLength() + "×" + bounds.getHeight()
                )
        ));
        
        // Info note
        inv.setItem(31, createStatItem(
                Material.PAPER,
                hex("#666666") + "Note",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Protection rules are configured",
                        hex("#AAAAAA") + "by server administrators.",
                        "",
                        hex("#666666") + "Use Trust Manager to share access."
                )
        ));
        
        // Add navbar
        addSubNavbar(inv, "settings");
        
        // Track menu with previous_menu context
        Map<String, Object> navbarContext = new HashMap<>();
        navbarContext.put("previous_menu", "main");
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.SETTINGS, base, null, 1, null, navbarContext));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== EVENT HANDLERS ====================
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        if (!title.startsWith(MENU_ID)) return;
        
        event.setCancelled(true);
        
        MenuSession session = openMenus.get(player.getUniqueId());
        if (session == null) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot > 53) return;
        
        // Handle navbar clicks (bottom row)
        if (slot >= 45 && slot <= 53) {
            handleNavbarClick(player, session, slot, event.isShiftClick());
            return;
        }
        
        // Handle menu-specific clicks
        switch (session.type) {
            case MAIN -> handleMainMenuClick(player, session, slot, event.isShiftClick());
            case TRUST_LIST -> handleTrustListClick(player, session, slot, clicked);
            case TRUST_PERMS -> handlePermsClick(player, session, slot);
            case BASE_SELECTOR -> handleBaseSelectorClick(player, session, slot, clicked);
            case STATS -> handleStatsMenuClick(player, session, slot);
            case SETTINGS -> handleSettingsMenuClick(player, session, slot);
        }
    }
    
    private void handleMainMenuClick(Player player, MenuSession session, int slot, boolean shift) {
        switch (slot) {
            case 20 -> { // Trust Manager
                transitioning.add(player.getUniqueId());
                openTrustMenu(player, session.base);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
            }
            case 22 -> { // Stats
                transitioning.add(player.getUniqueId());
                openStatsMenu(player, session.base);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
            }
            case 24 -> { // Protection Info
                transitioning.add(player.getUniqueId());
                openSettingsMenu(player, session.base);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
            }
            case 29 -> { // Toggle particles
                boolean now = plugin.getParticleManager().toggleViewing(player.getUniqueId());
                messages.send(player, now ? "success.particles-enabled" : "success.particles-disabled");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, now ? 1.2f : 0.8f);
                // Refresh menu with updated particle state
                transitioning.add(player.getUniqueId());
                openMainMenu(player, session.base);
                Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
            }
            case 31 -> { // Teleport
                player.closeInventory();
                startTeleport(player, session.base);
            }
            case 33 -> { // Selector Tool
                if (!plugin.getConfigManager().isSelectorEnabled()) {
                    messages.send(player, "errors.selector-disabled");
                    return;
                }
                if (!player.hasPermission("basedet.user.tool")) {
                    messages.send(player, "errors.no-tool-permission");
                    return;
                }
                player.closeInventory();
                plugin.getSelectorTool().giveSelectorTool(player);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            }
            case 40 -> { // Abandon base
                if (shift) {
                    plugin.getDatabaseManager().deleteBase(session.base.getId());
                    player.closeInventory();
                    messages.send(player, "success.base-abandoned");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                } else {
                    messages.send(player, "success.shift-click-to-abandon");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                }
            }
        }
    }
    
    private void handleStatsMenuClick(Player player, MenuSession session, int slot) {
        // Stats menu is view-only, no actions needed
    }
    
    private void handleSettingsMenuClick(Player player, MenuSession session, int slot) {
        // Settings menu is view-only, no actions needed
    }
    
    @SuppressWarnings("unchecked")
    private void handleBaseSelectorClick(Player player, MenuSession session, int slot, ItemStack clicked) {
        // Get bases from session data
        if (session.data == null) return;
        List<Base> bases = (List<Base>) session.data.get("bases");
        if (bases == null) return;
        
        // Check if clicked a base item (grass_block, netherrack, end_stone)
        Material type = clicked.getType();
        if (type == Material.GRASS_BLOCK || type == Material.NETHERRACK || 
            type == Material.END_STONE || type == Material.STONE) {
            
            // Find which base was clicked based on slot
            int[] slots = getCenteredSlots(bases.size());
            for (int i = 0; i < slots.length && i < bases.size(); i++) {
                if (slots[i] == slot) {
                    Base selectedBase = bases.get(i);
                    transitioning.add(player.getUniqueId());
                    openMainMenu(player, selectedBase);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
                    return;
                }
            }
        }
    }
    
    private void handleTrustListClick(Player player, MenuSession session, int slot, ItemStack clicked) {
        // Player head click
        if (clicked.getType() == Material.PLAYER_HEAD && slot != 4) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
                UUID trustedUUID = skullMeta.getOwningPlayer().getUniqueId();
                TrustEntry entry = plugin.getDatabaseManager().getTrust(session.base.getId(), trustedUUID);
                if (entry != null) {
                    transitioning.add(player.getUniqueId());
                    openPermissionsMenu(player, session.base, entry, 1);
                    Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
                }
            }
        }
        // Add player button
        if (slot == 40) {
            player.closeInventory();
            player.sendMessage("");
            messages.sendRaw(player, "trust.add-player-prompt-header");
            messages.sendRaw(player, "trust.add-player-prompt");
            messages.sendRaw(player, "trust.add-player-hint");
            messages.sendRaw(player, "trust.add-player-prompt-header");
            player.sendMessage("");
        }
    }
    
    private void handlePermsClick(Player player, MenuSession session, int slot) {
        if (session.trustEntry == null) return;
        
        String permission = getPermFromSlot(slot, session.page);
        if (permission != null) {
            boolean current = getPermValue(session.trustEntry, permission);
            String column = TrustEntry.getPermissionColumn(
                    permission.replace("_online", "").replace("_offline", ""),
                    permission.contains("_online")
            );
            
            if (column != null) {
                plugin.getDatabaseManager().updateTrustPermission(session.trustEntry.getId(), column, !current);
                TrustEntry updated = plugin.getDatabaseManager().getTrust(session.base.getId(), session.trustEntry.getTrustedUUID());
                if (updated != null) {
                    transitioning.add(player.getUniqueId());
                    openPermissionsMenu(player, session.base, updated, session.page);
                    Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, current ? 0.8f : 1.2f);
            }
        }
    }
    
    private void handleNavbarClick(Player player, MenuSession session, int slot, boolean shift) {
        // Use unified navbar action system with context
        UnifiedMenuManager.NavbarAction action = unifiedMenuManager.getNavbarAction(slot, session.navbarContext);

        switch (action) {
            case BACK:
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                transitioning.add(player.getUniqueId());
                switch (session.type) {
                    case TRUST_LIST -> openMainMenu(player, session.base);
                    case STATS -> openMainMenu(player, session.base);
                    case SETTINGS -> openMainMenu(player, session.base);
                    case TRUST_PERMS -> openTrustMenu(player, session.base);
                    case BASE_SELECTOR -> { player.closeInventory(); transitioning.remove(player.getUniqueId()); return; }
                    case MAIN -> { player.closeInventory(); transitioning.remove(player.getUniqueId()); return; }
                }
                Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
                break;
            case PREVIOUS_PAGE:
                if (session.type == MenuType.TRUST_PERMS && session.page > 1) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    transitioning.add(player.getUniqueId());
                    openPermissionsMenu(player, session.base, session.trustEntry, session.page - 1);
                    Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
                }
                break;
            case NEXT_PAGE:
                if (session.type == MenuType.TRUST_PERMS && session.page < 2) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    transitioning.add(player.getUniqueId());
                    openPermissionsMenu(player, session.base, session.trustEntry, session.page + 1);
                    Bukkit.getScheduler().runTask(plugin, () -> transitioning.remove(player.getUniqueId()));
                }
                break;
            case CLOSE:
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
                player.closeInventory();
                break;
            case NONE:
            default:
                // Not a navbar action
                break;
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            // Only remove session if not transitioning to another menu
            if (!transitioning.contains(uuid)) {
                openMenus.remove(uuid);
            }
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private void fillBackground(Inventory inv, Material material) {
        ItemStack bg = new ItemStack(material);
        ItemMeta meta = bg.getItemMeta();
        meta.setDisplayName(" ");
        bg.setItemMeta(meta);
        
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }
    }
    
    private ItemStack createMenuItem(Material mat, String name, List<String> lore, boolean isHead) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createStatItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createBaseOverviewItem(Base base, Player player) {
        BoundingBox bounds = base.getBounds();
        int volume = bounds.getWidth() * bounds.getLength() * bounds.getHeight();
        
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(hex("#FFD700") + "✦ " + hex("#FFFFFF") + "Your Base");
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(hex("#AAAAAA") + "Size: " + hex("#55FF55") + bounds.getWidth() + "×" + bounds.getLength() + "×" + bounds.getHeight());
        lore.add(hex("#AAAAAA") + "Volume: " + hex("#FFD700") + String.format("%,d", volume) + " blocks");
        lore.add(hex("#AAAAAA") + "World: " + hex("#FFFFFF") + base.getWorldName());
        lore.add("");
        lore.add(hex("#55FF55") + "✓ Protection Active");
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createTrustedPlayerItem(TrustEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getTrustedUUID()));
        
        String name = plugin.getTrustManager().getPlayerName(entry.getTrustedUUID());
        meta.setDisplayName(hex("#55FFFF") + name);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(hex("#55FF55") + "Online: " + hex("#FFFFFF") + formatPerms(entry, true));
        lore.add(hex("#FFFF55") + "Offline: " + hex("#FFFFFF") + formatPerms(entry, false));
        lore.add("");
        lore.add(hex("#FFFF55") + "▸ Click to edit permissions");
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createPermToggle(String name, Material icon, boolean enabled, String key, String desc1, String desc2) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName((enabled ? hex("#55FF55") + "✓ " : hex("#FF5555") + "✗ ") + hex("#FFFFFF") + name);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(hex("#AAAAAA") + desc1);
        lore.add(hex("#AAAAAA") + desc2);
        lore.add("");
        lore.add(hex("#666666") + "Status: " + (enabled ? hex("#55FF55") + "ALLOWED" : hex("#FF5555") + "DENIED"));
        lore.add("");
        lore.add(hex("#FFFF55") + "▸ Click to toggle");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createSectionDivider(String name, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
    
    private void addMainNavbar(Inventory inv, String current) {
        // Use unified navbar system with plugin-specific context
        Map<String, Object> context = new HashMap<>();
        context.put("menu_name", "WDP-BaseDet");
        context.put("menu_description", "Automatic base detection and protection system");
        context.put("page", 1);
        context.put("total_pages", 1);
        
        unifiedMenuManager.applyNavbar(inv, null, "main", context);
    }
    
    private void addSubNavbar(Inventory inv, String current) {
        // Use unified navbar system
        Map<String, Object> context = new HashMap<>();
        context.put("menu_name", "Base Management");
        context.put("menu_description", "Submenu for base operations");
        context.put("page", 1);
        context.put("total_pages", 1);
        context.put("previous_menu", "main");
        
        unifiedMenuManager.applyNavbar(inv, null, "sub", context);
    }
    
    private void addPermissionsNavbar(Inventory inv, int page, String playerName) {
        // Use unified navbar system
        Map<String, Object> context = new HashMap<>();
        context.put("menu_name", "Trust Permissions");
        context.put("menu_description", "Editing permissions for " + playerName);
        context.put("page", page);
        context.put("total_pages", 2);
        context.put("previous_menu", "trust_list");
        
        unifiedMenuManager.applyNavbar(inv, null, "permissions", context);
        
        // Override slot 49 with remove button (this is specific to permissions menu)
        ItemStack remove = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta removeMeta = remove.getItemMeta();
        removeMeta.setDisplayName(hex("#FF5555") + "✗ Remove " + playerName);
        List<String> removeLore = new ArrayList<>();
        removeLore.add("");
        removeLore.add(hex("#AAAAAA") + "Remove this player from");
        removeLore.add(hex("#AAAAAA") + "your trusted list.");
        removeLore.add("");
        removeLore.add(hex("#FF5555") + "⚠ Cannot be undone!");
        removeMeta.setLore(removeLore);
        remove.setItemMeta(removeMeta);
        inv.setItem(49, remove);
    }
    
    private ItemStack createNavItem(Material mat, String name, String description) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(hex("#AAAAAA") + description);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private String formatPerms(TrustEntry e, boolean online) {
        StringBuilder sb = new StringBuilder();
        if (online) {
            if (e.canBreakOnline()) sb.append("B");
            if (e.canPlaceOnline()) sb.append("P");
            if (e.canContainerOnline()) sb.append("C");
            if (e.canDoorOnline()) sb.append("D");
            if (e.canRedstoneOnline()) sb.append("R");
            if (e.canEntityDamageOnline()) sb.append("E");
            if (e.canVehicleOnline()) sb.append("V");
            if (e.canDecorationOnline()) sb.append("A");
        } else {
            if (e.canBreakOffline()) sb.append("B");
            if (e.canPlaceOffline()) sb.append("P");
            if (e.canContainerOffline()) sb.append("C");
            if (e.canDoorOffline()) sb.append("D");
            if (e.canRedstoneOffline()) sb.append("R");
            if (e.canEntityDamageOffline()) sb.append("E");
            if (e.canVehicleOffline()) sb.append("V");
            if (e.canDecorationOffline()) sb.append("A");
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }
    
    private String getPermFromSlot(int slot, int page) {
        if (page == 1) {
            return switch (slot) {
                case 19 -> "break_online";
                case 20 -> "place_online";
                case 21 -> "container_online";
                case 22 -> "door_online";
                case 37 -> "break_offline";
                case 38 -> "place_offline";
                case 39 -> "container_offline";
                case 40 -> "door_offline";
                default -> null;
            };
        } else {
            return switch (slot) {
                case 19 -> "redstone_online";
                case 20 -> "entity_damage_online";
                case 21 -> "vehicle_online";
                case 22 -> "decoration_online";
                case 37 -> "redstone_offline";
                case 38 -> "entity_damage_offline";
                case 39 -> "vehicle_offline";
                case 40 -> "decoration_offline";
                default -> null;
            };
        }
    }
    
    private boolean getPermValue(TrustEntry e, String perm) {
        return switch (perm) {
            case "break_online" -> e.canBreakOnline();
            case "place_online" -> e.canPlaceOnline();
            case "container_online" -> e.canContainerOnline();
            case "door_online" -> e.canDoorOnline();
            case "redstone_online" -> e.canRedstoneOnline();
            case "entity_damage_online" -> e.canEntityDamageOnline();
            case "vehicle_online" -> e.canVehicleOnline();
            case "decoration_online" -> e.canDecorationOnline();
            case "break_offline" -> e.canBreakOffline();
            case "place_offline" -> e.canPlaceOffline();
            case "container_offline" -> e.canContainerOffline();
            case "door_offline" -> e.canDoorOffline();
            case "redstone_offline" -> e.canRedstoneOffline();
            case "entity_damage_offline" -> e.canEntityDamageOffline();
            case "vehicle_offline" -> e.canVehicleOffline();
            case "decoration_offline" -> e.canDecorationOffline();
            default -> false;
        };
    }
    
    private String hex(String code) {
        return ChatColor.of(code).toString();
    }
    
    // ==================== NAVBAR METHODS ====================
    
    /**
     * Add main menu navbar
     */
    private void addMainNavbar(Inventory inv, Player player, String menuContext) {
        Map<String, Object> context = new HashMap<>();
        context.put("coins", getPlayerCoins(player));
        context.put("tokens", getPlayerTokens(player));
        context.put("hide_pages", true);
        unifiedMenuManager.applyNavbar(inv, player, menuContext, context);
    }
    
    /**
     * Add selector navbar (base selection menu)
     */
    private void addSelectorNavbar(Inventory inv, Player player) {
        Map<String, Object> context = new HashMap<>();
        context.put("coins", getPlayerCoins(player));
        context.put("tokens", getPlayerTokens(player));
        context.put("hide_pages", true);
        unifiedMenuManager.applyNavbar(inv, player, "selector", context);
    }
    
    /**
     * Add permissions menu navbar with pagination
     */
    private void addPermissionsNavbar(Inventory inv, Player player, int page, String trustedName) {
        Map<String, Object> context = new HashMap<>();
        context.put("page", page);
        context.put("total_pages", 2);
        context.put("coins", getPlayerCoins(player));
        context.put("tokens", getPlayerTokens(player));
        context.put("previous_menu", "trust");
        unifiedMenuManager.applyNavbar(inv, player, "permissions", context);
    }
    
    /**
     * Add trust list navbar
     */
    private void addTrustNavbar(Inventory inv, Player player, int page, int totalPages) {
        Map<String, Object> context = new HashMap<>();
        context.put("page", page);
        context.put("total_pages", totalPages);
        context.put("coins", getPlayerCoins(player));
        context.put("tokens", getPlayerTokens(player));
        context.put("previous_menu", "main");
        context.put("hide_pages", true);
        unifiedMenuManager.applyNavbar(inv, player, "trust", context);
    }
    
    /**
     * Add stats menu navbar
     */
    private void addStatsNavbar(Inventory inv, Player player) {
        Map<String, Object> context = new HashMap<>();
        context.put("coins", getPlayerCoins(player));
        context.put("tokens", getPlayerTokens(player));
        context.put("previous_menu", "main");
        context.put("hide_pages", true);
        unifiedMenuManager.applyNavbar(inv, player, "stats", context);
    }
    
    /**
     * Get player coins (SkillCoins integration)
     */
    private long getPlayerCoins(Player player) {
        if (player == null || plugin.getEconomyIntegration() == null) {
            return 0;
        }
        return (long) plugin.getEconomyIntegration().getBalance(player);
    }
    
    /**
     * Get player tokens from AuraSkills
     */
    private long getPlayerTokens(Player player) {
        if (player == null) return 0;
        
        try {
            org.bukkit.plugin.Plugin auraSkillsPlugin = Bukkit.getPluginManager().getPlugin("AuraSkills");
            if (auraSkillsPlugin != null) {
                // Use public API method
                java.lang.reflect.Method getTokensMethod = auraSkillsPlugin.getClass().getMethod("getPlayerTokens", java.util.UUID.class);
                Object balance = getTokensMethod.invoke(auraSkillsPlugin, player.getUniqueId());
                return ((Number) balance).longValue();
            }
        } catch (Exception e) {
            // Silent fail - AuraSkills integration optional
        }
        return 0;
    }
    
    // ==================== TELEPORT SYSTEM ====================
    
    /**
     * Create the teleport menu item with proper status display
     */
    private ItemStack createTeleportItem(Player player, Base base) {
        var config = plugin.getConfigManager();
        boolean enabled = config.isTeleportEnabled();
        UUID uuid = player.getUniqueId();
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (!enabled) {
            return createMenuItem(
                    Material.GRAY_DYE,
                    hex("#666666") + "✦ Teleport Disabled",
                    Arrays.asList("", hex("#666666") + "This feature is disabled", hex("#666666") + "by the server."),
                    false
            );
        }
        
        // Check combat
        boolean inCombat = plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player);
        if (inCombat && config.isTeleportBlockedInCombat()) {
            int remaining = plugin.getCombatManager().getCombatTimeRemaining(player);
            return createMenuItem(
                    Material.RED_DYE,
                    hex("#FF5555") + "✦ Teleport Blocked",
                    Arrays.asList(
                            "",
                            hex("#FF5555") + "⚔ You are in combat!",
                            "",
                            hex("#AAAAAA") + "Wait " + hex("#FFFF55") + remaining + "s" + hex("#AAAAAA") + " before teleporting.",
                            "",
                            hex("#666666") + "Combat disables teleportation"
                    ),
                    false
            );
        }
        
        // Check cooldown
        Long lastTp = teleportCooldowns.get(uuid);
        int cooldown = config.getTeleportCooldown();
        if (lastTp != null) {
            long elapsed = (System.currentTimeMillis() - lastTp) / 1000;
            if (elapsed < cooldown) {
                int remaining = (int) (cooldown - elapsed);
                return createMenuItem(
                        Material.CLOCK,
                        hex("#FFAA00") + "✦ Teleport on Cooldown",
                        Arrays.asList(
                                "",
                                hex("#AAAAAA") + "Wait " + hex("#FFFF55") + remaining + "s" + hex("#AAAAAA") + " before teleporting.",
                                "",
                                hex("#666666") + "Cooldown: " + cooldown + "s"
                        ),
                        false
                );
            }
        }
        
        // Ready to teleport
        double cost = config.getTeleportCost();
        int delay = config.getTeleportDelay();
        
        lore.add(hex("#AAAAAA") + "Teleport to the center");
        lore.add(hex("#AAAAAA") + "of your base.");
        lore.add("");
        if (cost > 0) {
            lore.add(hex("#666666") + "Cost: " + hex("#FFD700") + cost + " SkillCoins");
        }
        if (delay > 0) {
            lore.add(hex("#666666") + "Delay: " + hex("#FFFF55") + delay + "s");
            if (config.isTeleportCancelOnMove()) {
                lore.add(hex("#666666") + "• Cancelled if you move");
            }
            if (config.isTeleportCancelOnDamage()) {
                lore.add(hex("#666666") + "• Cancelled if damaged");
            }
        }
        if (config.isTeleportBlockedInCombat()) {
            lore.add(hex("#666666") + "• Blocked during combat");
        }
        lore.add("");
        lore.add(hex("#FFFF55") + "▸ Click to teleport");
        
        return createMenuItem(Material.ENDER_PEARL, hex("#AA55FF") + "✦ Teleport to Base", lore, false);
    }
    
    /**
     * Start the teleport process
     */
    public void startTeleport(Player player, Base base) {
        var config = plugin.getConfigManager();
        UUID uuid = player.getUniqueId();
        
        // Check for bypass permission
        boolean hasBypass = player.hasPermission("basedet.bypass.teleport") || player.hasPermission("basedet.admin.bypass");
        
        // Final checks
        if (!config.isTeleportEnabled()) {
            messages.send(player, "teleport.disabled");
            return;
        }
        
        // Combat check (unless bypassed)
        if (!hasBypass && config.isTeleportBlockedInCombat() && plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player)) {
            messages.send(player, "teleport.blocked-in-combat");
            return;
        }
        
        // Cooldown check (unless bypassed)
        if (!hasBypass) {
            Long lastTp = teleportCooldowns.get(uuid);
            int cooldown = config.getTeleportCooldown();
            if (lastTp != null) {
                long elapsed = (System.currentTimeMillis() - lastTp) / 1000;
                if (elapsed < cooldown) {
                    int remaining = (int) (cooldown - elapsed);
                    messages.send(player, "teleport.on-cooldown", "seconds", String.valueOf(remaining));
                    return;
                }
            }
        }
        
        // Economy check (unless bypassed)
        double cost = hasBypass ? 0 : config.getTeleportCost();
        if (cost > 0 && plugin.getEconomyIntegration() != null && plugin.getEconomyIntegration().isEnabled()) {
            if (!plugin.getEconomyIntegration().hasBalance(player, cost)) {
                messages.send(player, "teleport.not-enough-coins", "cost", String.valueOf((int) cost));
                return;
            }
        }
        
        // Calculate destination (center of base, surface)
        BoundingBox bounds = base.getBounds();
        int centerX = (bounds.getMinX() + bounds.getMaxX()) / 2;
        int centerZ = (bounds.getMinZ() + bounds.getMaxZ()) / 2;
        World world = Bukkit.getWorld(base.getWorldName());
        
        if (world == null) {
            messages.send(player, "teleport.world-not-found");
            return;
        }
        
        // Find safe Y (top of base, then search up)
        int safeY = findSafeY(world, centerX, bounds.getMaxY(), centerZ);
        Location destination = new Location(world, centerX + 0.5, safeY, centerZ + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        
        int delay = hasBypass ? 0 : config.getTeleportDelay();
        
        if (delay <= 0) {
            // Instant teleport
            executeTeleport(player, base, destination, (int) cost);
        } else {
            // Delayed teleport
            messages.send(player, "teleport.starting", "seconds", String.valueOf(delay));
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 2.0f);
            
            Location startLoc = player.getLocation().clone();
            
            TeleportTask task = new TeleportTask(player, base, destination, (int) cost, startLoc);
            BukkitTask bukkitTask = new BukkitRunnable() {
                int countdown = delay;
                
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancelTeleport(uuid, "cancel-disconnected");
                        cancel();
                        return;
                    }
                    
                    TeleportTask current = pendingTeleports.get(uuid);
                    if (current == null || current.cancelled) {
                        cancel();
                        return;
                    }
                    
                    countdown--;
                    
                    if (countdown > 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f + (0.1f * (delay - countdown)));
                    }
                    
                    if (countdown <= 0) {
                        pendingTeleports.remove(uuid);
                        executeTeleport(player, base, destination, (int) cost);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
            
            task.task = bukkitTask;
            pendingTeleports.put(uuid, task);
        }
    }
    
    /**
     * Cancel a pending teleport
     * @param uuid Player UUID
     * @param reasonKey Message key for the cancellation reason (e.g., "cancel-moved", "cancel-damage", "cancel-disconnected")
     */
    public void cancelTeleport(UUID uuid, String reasonKey) {
        TeleportTask task = pendingTeleports.remove(uuid);
        if (task != null && !task.cancelled) {
            task.cancelled = true;
            if (task.task != null) {
                task.task.cancel();
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Get the reason message using the key
                String reasonMessage = messages.get("teleport." + reasonKey);
                messages.send(player, "teleport.cancelled", "reason", reasonMessage);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }
    
    /**
     * Execute the actual teleport
     */
    private void executeTeleport(Player player, Base base, Location destination, int cost) {
        
        // Charge cost
        if (cost > 0 && plugin.getEconomyIntegration() != null && plugin.getEconomyIntegration().isEnabled()) {
            if (!plugin.getEconomyIntegration().withdraw(player, (int) cost)) {
                messages.send(player, "teleport.transaction-failed");
                return;
            }
        }
        
        // Teleport
        player.teleport(destination);
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        
        messages.send(player, "teleport.success");
        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }
    
    /**
     * Find a safe Y coordinate
     */
    private int findSafeY(World world, int x, int startY, int z) {
        for (int y = startY; y < world.getMaxHeight(); y++) {
            if (world.getBlockAt(x, y, z).getType().isAir() && 
                world.getBlockAt(x, y + 1, z).getType().isAir()) {
                return y;
            }
        }
        return startY;
    }
    
    /**
     * Handle player movement for teleport cancellation
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().isTeleportCancelOnMove()) return;
        
        UUID uuid = event.getPlayer().getUniqueId();
        TeleportTask task = pendingTeleports.get(uuid);
        if (task == null || task.cancelled) return;
        
        Location from = task.startLocation;
        Location to = event.getTo();
        
        // Check if actually moved (not just head rotation)
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            cancelTeleport(uuid, "cancel-moved");
        }
    }
    
    /**
     * Handle player damage for teleport cancellation
     */
    public void onPlayerDamage(Player player) {
        if (!plugin.getConfigManager().isTeleportCancelOnDamage()) return;
        
        UUID uuid = player.getUniqueId();
        TeleportTask task = pendingTeleports.get(uuid);
        if (task != null && !task.cancelled) {
            cancelTeleport(uuid, "cancel-damage");
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    private enum MenuType {
        MAIN, TRUST_LIST, TRUST_PERMS, STATS, SETTINGS, BASE_SELECTOR
    }
    
    private static class MenuSession {
        final MenuType type;
        final Base base;
        final TrustEntry trustEntry;
        final int page;
        final Map<String, Object> data;
        final Map<String, Object> navbarContext;
        
        MenuSession(MenuType type, Base base, TrustEntry trustEntry, int page, Map<String, Object> data) {
            this(type, base, trustEntry, page, data, new HashMap<>());
        }
        
        MenuSession(MenuType type, Base base, TrustEntry trustEntry, int page, Map<String, Object> data, Map<String, Object> navbarContext) {
            this.type = type;
            this.base = base;
            this.trustEntry = trustEntry;
            this.page = page;
            this.data = data;
            this.navbarContext = navbarContext;
        }
    }
    
    private static class TeleportTask {
        final Player player;
        final Base base;
        final Location destination;
        final int cost;
        final Location startLocation;
        BukkitTask task;
        boolean cancelled = false;
        
        TeleportTask(Player player, Base base, Location destination, int cost, Location startLocation) {
            this.player = player;
            this.base = base;
            this.destination = destination;
            this.cost = cost;
            this.startLocation = startLocation;
        }
    }
}
