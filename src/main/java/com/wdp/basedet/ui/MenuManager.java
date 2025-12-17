package com.wdp.basedet.ui;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import com.wdp.basedet.model.TrustEntry;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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
    
    // Track open menus
    private final Map<UUID, MenuSession> openMenus = new ConcurrentHashMap<>();
    
    // Menu identifiers (use Unicode for uniqueness)
    private static final String MENU_ID = "§8§l";
    
    public MenuManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    // ==================== MAIN BASE MENU ====================
    
    /**
     * Open the main base menu
     */
    public void openMainMenu(Player player, Base base) {
        String title = hex("#FFD700") + "✦ " + hex("#FFFFFF") + "Base Management";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // ===== TOP SECTION: Base Overview =====
        
        // Base icon (center top)
        inv.setItem(4, createBaseOverviewItem(base, player));
        
        // ===== MIDDLE SECTION: Main Options =====
        
        // Trust Manager (slot 20)
        inv.setItem(20, createMenuItem(
                Material.PLAYER_HEAD,
                hex("#55FFFF") + "✦ Trust Manager",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Manage who can interact with",
                        hex("#AAAAAA") + "your base when you're online",
                        hex("#AAAAAA") + "or offline.",
                        "",
                        hex("#666666") + "Trusted: " + hex("#FFFFFF") + 
                                plugin.getDatabaseManager().getBaseTrusted(base.getId()).size() + " players",
                        "",
                        hex("#FFFF55") + "▸ Click to manage"
                ),
                true
        ));
        
        // Base Settings (slot 22)
        inv.setItem(22, createMenuItem(
                Material.COMPARATOR,
                hex("#FFAA00") + "✦ Protection Settings",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Configure how your base",
                        hex("#AAAAAA") + "protection works.",
                        "",
                        hex("#666666") + "Currently: " + hex("#55FF55") + "Protected",
                        "",
                        hex("#FFFF55") + "▸ Click to configure"
                ),
                false
        ));
        
        // Selector Tool (slot 24)
        inv.setItem(24, createMenuItem(
                Material.BLAZE_ROD,
                hex("#FF5555") + "✦ Modify Boundaries",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Get the selector tool to",
                        hex("#AAAAAA") + "adjust your base boundaries.",
                        "",
                        hex("#666666") + "Cost: " + hex("#FFD700") + 
                                plugin.getConfigManager().getSelectorCostPerBlock() + " SkillCoins/block",
                        "",
                        hex("#FFFF55") + "▸ Click to get tool"
                ),
                false
        ));
        
        // ===== BOTTOM SECTION: Quick Actions =====
        
        // View Particles (slot 29)
        boolean viewing = plugin.getParticleManager().isViewing(player.getUniqueId());
        inv.setItem(29, createMenuItem(
                Material.END_ROD,
                (viewing ? hex("#55FF55") : hex("#FF5555")) + "✦ Boundary Particles",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Toggle particle visualization",
                        hex("#AAAAAA") + "of your base boundaries.",
                        "",
                        hex("#666666") + "Status: " + (viewing ? hex("#55FF55") + "ON" : hex("#FF5555") + "OFF"),
                        "",
                        hex("#FFFF55") + "▸ Click to toggle"
                ),
                false
        ));
        
        // Base Stats (slot 31)
        inv.setItem(31, createMenuItem(
                Material.BOOK,
                hex("#55FF55") + "✦ Base Statistics",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "View detailed statistics",
                        hex("#AAAAAA") + "about your base.",
                        "",
                        hex("#FFFF55") + "▸ Click to view"
                ),
                false
        ));
        
        // Abandon Base (slot 33)
        inv.setItem(33, createMenuItem(
                Material.BARRIER,
                hex("#FF5555") + "✦ Abandon Base",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Remove this base from your",
                        hex("#AAAAAA") + "protected bases list.",
                        "",
                        hex("#FF5555") + "⚠ This cannot be undone!",
                        "",
                        hex("#FFFF55") + "▸ Shift-Click to abandon"
                ),
                false
        ));
        
        // Add navbar
        addMainNavbar(inv, "main");
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.MAIN, base, null, 1, null));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }
    
    // ==================== TRUST MANAGER MENU ====================
    
    /**
     * Open the trust management menu
     */
    public void openTrustMenu(Player player, Base base) {
        String title = hex("#FFD700") + "✦ " + hex("#FFFFFF") + "Trust Manager";
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
        addSubNavbar(inv, "trust");
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.TRUST_LIST, base, null, 1, null));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== TRUST PERMISSIONS MENU ====================
    
    /**
     * Open permissions editing menu for a trusted player (2 pages)
     */
    public void openPermissionsMenu(Player player, Base base, TrustEntry trustEntry, int page) {
        String trustedName = plugin.getTrustManager().getPlayerName(trustEntry.getTrustedUUID());
        String title = hex("#FFD700") + "✦ " + hex("#FFFFFF") + trustedName + " §7(" + page + "/2)";
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
        addPermissionsNavbar(inv, page, trustedName);
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.TRUST_PERMS, base, trustEntry, page, null));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== STATS MENU ====================
    
    /**
     * Open base statistics menu
     */
    public void openStatsMenu(Player player, Base base) {
        String title = hex("#FFD700") + "✦ " + hex("#FFFFFF") + "Base Statistics";
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
        addSubNavbar(inv, "stats");
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.STATS, base, null, 1, null));
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    // ==================== SETTINGS MENU ====================
    
    /**
     * Open base settings menu
     */
    public void openSettingsMenu(Player player, Base base) {
        String title = hex("#FFD700") + "✦ " + hex("#FFFFFF") + "Protection Settings";
        Inventory inv = Bukkit.createInventory(null, 54, MENU_ID + title);
        
        // Fill background
        fillBackground(inv, Material.GRAY_STAINED_GLASS_PANE);
        
        // Header
        inv.setItem(4, createMenuItem(
                Material.COMPARATOR,
                hex("#FFD700") + "✦ Protection Settings",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Configure your base",
                        hex("#AAAAAA") + "protection behavior."
                ),
                false
        ));
        
        // Settings info
        inv.setItem(20, createStatItem(
                Material.SHIELD,
                hex("#55FF55") + "Offline Protection",
                Arrays.asList(
                        "",
                        hex("#55FF55") + "✓ ACTIVE",
                        "",
                        hex("#AAAAAA") + "Your base is protected",
                        hex("#AAAAAA") + "when you're offline."
                )
        ));
        
        inv.setItem(22, createStatItem(
                Material.GOLDEN_SWORD,
                hex("#FFAA00") + "Combat System",
                Arrays.asList(
                        "",
                        hex("#55FF55") + "✓ ENABLED",
                        "",
                        hex("#AAAAAA") + "Smart combat detection",
                        hex("#AAAAAA") + "allows fights when tagged."
                )
        ));
        
        inv.setItem(24, createStatItem(
                Material.BELL,
                hex("#55FFFF") + "Discord Alerts",
                Arrays.asList(
                        "",
                        hex("#55FF55") + "✓ ENABLED",
                        "",
                        hex("#AAAAAA") + "Get DMs when players",
                        hex("#AAAAAA") + "enter your base."
                )
        ));
        
        // Info note
        inv.setItem(31, createStatItem(
                Material.PAPER,
                hex("#666666") + "Note",
                Arrays.asList(
                        "",
                        hex("#AAAAAA") + "Global settings are configured",
                        hex("#AAAAAA") + "in the server config.",
                        "",
                        hex("#666666") + "Contact staff for changes."
                )
        ));
        
        // Add navbar
        addSubNavbar(inv, "settings");
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.SETTINGS, base, null, 1, null));
        
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
            case STATS, SETTINGS -> {} // View-only menus
        }
    }
    
    private void handleMainMenuClick(Player player, MenuSession session, int slot, boolean shift) {
        switch (slot) {
            case 20 -> openTrustMenu(player, session.base); // Trust Manager
            case 22 -> openSettingsMenu(player, session.base); // Settings
            case 24 -> { // Selector Tool
                player.closeInventory();
                plugin.getSelectorTool().giveSelectorTool(player);
            }
            case 29 -> { // Toggle particles
                boolean now = plugin.getParticleManager().toggleViewing(player.getUniqueId());
                player.sendMessage(plugin.getConfigManager().getMessagePrefix() +
                        (now ? hex("#55FF55") + "Boundary particles enabled!" : hex("#FF5555") + "Boundary particles disabled!"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                openMainMenu(player, session.base); // Refresh
            }
            case 31 -> openStatsMenu(player, session.base); // Stats
            case 33 -> { // Abandon base
                if (shift) {
                    plugin.getDatabaseManager().deleteBase(session.base.getId());
                    player.closeInventory();
                    player.sendMessage(plugin.getConfigManager().getMessagePrefix() + hex("#FF5555") + "Base abandoned!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessagePrefix() + hex("#FFFF55") + "Shift-click to abandon!");
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
                    openPermissionsMenu(player, session.base, entry, 1);
                }
            }
        }
        // Add player button
        if (slot == 40) {
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(hex("#55FFFF") + "  Type the player name in chat:");
            player.sendMessage(hex("#AAAAAA") + "  Or use: " + hex("#FFFF55") + "/trust <name>");
            player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
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
                    openPermissionsMenu(player, session.base, updated, session.page);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, current ? 0.8f : 1.2f);
            }
        }
    }
    
    private void handleNavbarClick(Player player, MenuSession session, int slot, boolean shift) {
        switch (slot) {
            case 45 -> { // Back
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                switch (session.type) {
                    case TRUST_LIST, STATS, SETTINGS -> openMainMenu(player, session.base);
                    case TRUST_PERMS -> openTrustMenu(player, session.base);
                    default -> player.closeInventory();
                }
            }
            case 48 -> { // Previous page
                if (session.type == MenuType.TRUST_PERMS && session.page > 1) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    openPermissionsMenu(player, session.base, session.trustEntry, session.page - 1);
                }
            }
            case 49 -> { // Remove trust (perms menu only)
                if (session.type == MenuType.TRUST_PERMS && session.trustEntry != null) {
                    plugin.getDatabaseManager().removeTrust(session.base.getId(), session.trustEntry.getTrustedUUID());
                    String name = plugin.getTrustManager().getPlayerName(session.trustEntry.getTrustedUUID());
                    player.sendMessage(plugin.getConfigManager().getMessage("trusted-removed").replace("{player}", name));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 0.8f);
                    openTrustMenu(player, session.base);
                }
            }
            case 50 -> { // Next page
                if (session.type == MenuType.TRUST_PERMS && session.page < 2) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    openPermissionsMenu(player, session.base, session.trustEntry, session.page + 1);
                }
            }
            case 53 -> { // Close
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
                player.closeInventory();
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
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
        // Home (slot 45)
        ItemStack home = createNavItem(Material.NETHER_STAR, hex("#55FFFF") + "⌂ Home", "Main base menu");
        inv.setItem(45, home);
        
        // Empty decorations
        ItemStack deco = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta decoMeta = deco.getItemMeta();
        decoMeta.setDisplayName(" ");
        deco.setItemMeta(decoMeta);
        inv.setItem(46, deco);
        inv.setItem(47, deco);
        inv.setItem(48, deco);
        inv.setItem(50, deco);
        inv.setItem(51, deco);
        inv.setItem(52, deco);
        
        // Info (center, slot 49)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(hex("#FFD700") + "WDP-BaseDet");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(hex("#AAAAAA") + "Automatic base detection");
        infoLore.add(hex("#AAAAAA") + "and protection system.");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);
        
        // Close (slot 53)
        ItemStack close = createNavItem(Material.BARRIER, hex("#FF5555") + "✗ Close", "Close this menu");
        inv.setItem(53, close);
    }
    
    private void addSubNavbar(Inventory inv, String current) {
        // Back (slot 45)
        ItemStack back = createNavItem(Material.ARROW, hex("#FFFF55") + "← Back", "Return to previous menu");
        inv.setItem(45, back);
        
        // Empty decorations
        ItemStack deco = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta decoMeta = deco.getItemMeta();
        decoMeta.setDisplayName(" ");
        deco.setItemMeta(decoMeta);
        for (int i = 46; i <= 52; i++) {
            inv.setItem(i, deco);
        }
        
        // Close (slot 53)
        ItemStack close = createNavItem(Material.BARRIER, hex("#FF5555") + "✗ Close", "Close this menu");
        inv.setItem(53, close);
    }
    
    private void addPermissionsNavbar(Inventory inv, int page, String playerName) {
        // Back (slot 45)
        ItemStack back = createNavItem(Material.ARROW, hex("#FFFF55") + "← Back to Trust List", "Return to trusted players");
        inv.setItem(45, back);
        
        // Decorations
        ItemStack deco = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta decoMeta = deco.getItemMeta();
        decoMeta.setDisplayName(" ");
        deco.setItemMeta(decoMeta);
        inv.setItem(46, deco);
        inv.setItem(47, deco);
        inv.setItem(51, deco);
        inv.setItem(52, deco);
        
        // Previous page (slot 48)
        if (page > 1) {
            inv.setItem(48, createNavItem(Material.SPECTRAL_ARROW, hex("#55FFFF") + "← Page 1", "Basic permissions"));
        } else {
            inv.setItem(48, deco);
        }
        
        // Remove button (slot 49)
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
        
        // Next page (slot 50)
        if (page < 2) {
            inv.setItem(50, createNavItem(Material.SPECTRAL_ARROW, hex("#55FFFF") + "Page 2 →", "Advanced permissions"));
        } else {
            inv.setItem(50, deco);
        }
        
        // Close (slot 53)
        ItemStack close = createNavItem(Material.BARRIER, hex("#FF5555") + "✗ Close", "Close this menu");
        inv.setItem(53, close);
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
    
    // ==================== INNER CLASSES ====================
    
    private enum MenuType {
        MAIN, TRUST_LIST, TRUST_PERMS, STATS, SETTINGS
    }
    
    private static class MenuSession {
        final MenuType type;
        final Base base;
        final TrustEntry trustEntry;
        final int page;
        final Map<String, Object> data;
        
        MenuSession(MenuType type, Base base, TrustEntry trustEntry, int page, Map<String, Object> data) {
            this.type = type;
            this.base = base;
            this.trustEntry = trustEntry;
            this.page = page;
            this.data = data;
        }
    }
}
