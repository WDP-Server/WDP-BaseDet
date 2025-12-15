package com.wdp.basedet.ui;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.TrustEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages GUI menus for WDP-BaseDet
 * Uses SkillCoins-style navbar pattern with 2-page permissions menu
 */
public class MenuManager implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    
    // Track open menus
    private final Map<UUID, MenuSession> openMenus = new ConcurrentHashMap<>();
    
    // Menu title identifier
    private static final String TRUST_MENU_TITLE = ChatColor.GOLD + "✦ " + ChatColor.WHITE + "Trust Manager";
    private static final String PERMISSIONS_MENU_TITLE = ChatColor.GOLD + "✦ " + ChatColor.WHITE + "Permissions ";
    
    public MenuManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Open the trust management menu
     */
    public void openTrustMenu(Player player, Base base) {
        Inventory inv = Bukkit.createInventory(null, 54, TRUST_MENU_TITLE);
        
        // Fill border
        fillBorder(inv);
        
        // Add header
        inv.setItem(4, createHeader(player, base));
        
        // Add trusted players (rows 2-4, columns 1-7)
        List<TrustEntry> trusted = plugin.getDatabaseManager().getBaseTrusted(base.getId());
        int slot = 10;
        int count = 0;
        
        for (TrustEntry entry : trusted) {
            if (count >= 21) break; // Max 21 players shown
            
            inv.setItem(slot, createTrustedPlayerItem(entry));
            
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2; // Skip to next row
            }
            count++;
        }
        
        // Add "Add Player" button
        inv.setItem(49, createAddPlayerItem());
        
        // Navbar (bottom row)
        addNavbar(inv, "trust");
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.TRUST_MAIN, base, null, 1));
        
        player.openInventory(inv);
    }
    
    /**
     * Open permissions editing menu for a trusted player (2 pages)
     */
    public void openPermissionsMenu(Player player, Base base, TrustEntry trustEntry, int page) {
        String title = PERMISSIONS_MENU_TITLE + ChatColor.GRAY + "(" + page + "/2)";
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        // Fill border
        fillBorder(inv);
        
        String trustedName = plugin.getTrustManager().getPlayerName(trustEntry.getTrustedUUID());
        
        // Header
        ItemStack header = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) header.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(trustEntry.getTrustedUUID()));
        skullMeta.setDisplayName(ChatColor.AQUA + "Permissions for " + trustedName);
        List<String> headerLore = new ArrayList<>();
        headerLore.add("");
        headerLore.add(ChatColor.GRAY + "Configure what this player can do");
        headerLore.add(ChatColor.GRAY + "in your base.");
        headerLore.add("");
        headerLore.add(ChatColor.YELLOW + "Page " + page + " of 2");
        skullMeta.setLore(headerLore);
        header.setItemMeta(skullMeta);
        inv.setItem(4, header);
        
        if (page == 1) {
            // ========== PAGE 1: BASIC PERMISSIONS ==========
            
            // Online permissions section
            inv.setItem(10, createSectionLabel("When You're ONLINE", ChatColor.GREEN, Material.LIME_STAINED_GLASS_PANE));
            
            // Row 2: Building permissions
            inv.setItem(19, createPermissionToggle(
                    "Break Blocks", 
                    Material.DIAMOND_PICKAXE,
                    trustEntry.canBreakOnline(), 
                    "break_online",
                    "Break and remove blocks", 
                    "Prevents grief/theft"));
            
            inv.setItem(20, createPermissionToggle(
                    "Place Blocks", 
                    Material.COBBLESTONE,
                    trustEntry.canPlaceOnline(), 
                    "place_online",
                    "Place new blocks", 
                    "Allows building"));
            
            inv.setItem(21, createPermissionToggle(
                    "Containers", 
                    Material.CHEST,
                    trustEntry.canContainerOnline(), 
                    "container_online",
                    "Open chests, barrels", 
                    "shulker boxes, hoppers"));
            
            inv.setItem(22, createPermissionToggle(
                    "Doors & Gates", 
                    Material.OAK_DOOR,
                    trustEntry.canDoorOnline(), 
                    "door_online",
                    "Use doors, trapdoors", 
                    "fence gates"));
            
            // Offline permissions section
            inv.setItem(28, createSectionLabel("When You're OFFLINE", ChatColor.YELLOW, Material.YELLOW_STAINED_GLASS_PANE));
            
            // Row 4: Offline permissions
            inv.setItem(37, createPermissionToggle(
                    "Break Blocks", 
                    Material.IRON_PICKAXE,
                    trustEntry.canBreakOffline(), 
                    "break_offline",
                    "Break blocks when you're", 
                    "offline (higher trust)"));
            
            inv.setItem(38, createPermissionToggle(
                    "Place Blocks", 
                    Material.BRICKS,
                    trustEntry.canPlaceOffline(), 
                    "place_offline",
                    "Place blocks when you're", 
                    "offline (higher trust)"));
            
            inv.setItem(39, createPermissionToggle(
                    "Containers", 
                    Material.BARREL,
                    trustEntry.canContainerOffline(), 
                    "container_offline",
                    "Access storage when", 
                    "you're offline"));
            
            inv.setItem(40, createPermissionToggle(
                    "Doors & Gates", 
                    Material.IRON_DOOR,
                    trustEntry.canDoorOffline(), 
                    "door_offline",
                    "Use doors when", 
                    "you're offline"));
            
        } else {
            // ========== PAGE 2: ADVANCED PERMISSIONS ==========
            
            // Online permissions section
            inv.setItem(10, createSectionLabel("When You're ONLINE", ChatColor.GREEN, Material.LIME_STAINED_GLASS_PANE));
            
            // Row 2: Advanced online permissions
            inv.setItem(19, createPermissionToggle(
                    "Redstone", 
                    Material.REDSTONE,
                    trustEntry.canRedstoneOnline(), 
                    "redstone_online",
                    "Use buttons, levers", 
                    "pressure plates"));
            
            inv.setItem(20, createPermissionToggle(
                    "Entity Damage", 
                    Material.DIAMOND_SWORD,
                    trustEntry.canEntityDamageOnline(), 
                    "entity_damage_online",
                    "Attack mobs & animals", 
                    "inside your base"));
            
            inv.setItem(21, createPermissionToggle(
                    "Vehicles", 
                    Material.MINECART,
                    trustEntry.canVehicleOnline(), 
                    "vehicle_online",
                    "Break/place minecarts", 
                    "boats, armor stands"));
            
            inv.setItem(22, createPermissionToggle(
                    "Decorations", 
                    Material.ITEM_FRAME,
                    trustEntry.canDecorationOnline(), 
                    "decoration_online",
                    "Item frames, paintings", 
                    "leads, name tags"));
            
            // Offline permissions section
            inv.setItem(28, createSectionLabel("When You're OFFLINE", ChatColor.YELLOW, Material.YELLOW_STAINED_GLASS_PANE));
            
            // Row 4: Advanced offline permissions
            inv.setItem(37, createPermissionToggle(
                    "Redstone", 
                    Material.LEVER,
                    trustEntry.canRedstoneOffline(), 
                    "redstone_offline",
                    "Use redstone devices", 
                    "when you're offline"));
            
            inv.setItem(38, createPermissionToggle(
                    "Entity Damage", 
                    Material.IRON_SWORD,
                    trustEntry.canEntityDamageOffline(), 
                    "entity_damage_offline",
                    "Kill entities when", 
                    "you're offline"));
            
            inv.setItem(39, createPermissionToggle(
                    "Vehicles", 
                    Material.OAK_BOAT,
                    trustEntry.canVehicleOffline(), 
                    "vehicle_offline",
                    "Vehicle interaction", 
                    "when you're offline"));
            
            inv.setItem(40, createPermissionToggle(
                    "Decorations", 
                    Material.PAINTING,
                    trustEntry.canDecorationOffline(), 
                    "decoration_offline",
                    "Decoration interaction", 
                    "when you're offline"));
        }
        
        // Remove trust button (both pages)
        inv.setItem(49, createRemoveButton(trustedName));
        
        // Navbar with pagination
        addPermissionsNavbar(inv, page);
        
        // Track menu
        openMenus.put(player.getUniqueId(), new MenuSession(MenuType.PERMISSIONS, base, trustEntry, page));
        
        player.openInventory(inv);
    }
    
    /**
     * Handle inventory clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        if (!title.startsWith(TRUST_MENU_TITLE.substring(0, 10)) && 
            !title.startsWith(PERMISSIONS_MENU_TITLE.substring(0, 10))) {
            return;
        }
        
        event.setCancelled(true);
        
        MenuSession session = openMenus.get(player.getUniqueId());
        if (session == null) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        int slot = event.getRawSlot();
        
        // Handle navbar clicks (bottom row)
        if (slot >= 45 && slot <= 53) {
            handleNavbarClick(player, session, slot);
            return;
        }
        
        // Handle menu-specific clicks
        if (session.type == MenuType.TRUST_MAIN) {
            handleTrustMenuClick(player, session, clicked, slot);
        } else if (session.type == MenuType.PERMISSIONS) {
            handlePermissionsMenuClick(player, session, clicked, slot);
        }
    }
    
    private void handleTrustMenuClick(Player player, MenuSession session, ItemStack clicked, int slot) {
        // Check if clicked on a player head
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
    }
    
    private void handlePermissionsMenuClick(Player player, MenuSession session, ItemStack clicked, int slot) {
        if (session.trustEntry == null) return;
        
        String permission = getPermissionFromSlot(slot, session.page);
        if (permission != null) {
            // Toggle permission
            boolean currentValue = getPermissionValue(session.trustEntry, permission);
            String dbColumn = TrustEntry.getPermissionColumn(
                    permission.replace("_online", "").replace("_offline", ""),
                    permission.contains("_online")
            );
            
            if (dbColumn != null) {
                plugin.getDatabaseManager().updateTrustPermission(session.trustEntry.getId(), dbColumn, !currentValue);
                
                // Refresh menu
                TrustEntry updated = plugin.getDatabaseManager().getTrust(session.base.getId(), session.trustEntry.getTrustedUUID());
                if (updated != null) {
                    openPermissionsMenu(player, session.base, updated, session.page);
                }
                
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        }
        
        // Remove button
        if (slot == 49) {
            plugin.getDatabaseManager().removeTrust(session.base.getId(), session.trustEntry.getTrustedUUID());
            String name = plugin.getTrustManager().getPlayerName(session.trustEntry.getTrustedUUID());
            player.sendMessage(plugin.getConfigManager().getMessage("trusted-removed").replace("{player}", name));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.7f, 0.8f);
            openTrustMenu(player, session.base);
        }
    }
    
    private void handleNavbarClick(Player player, MenuSession session, int slot) {
        // Back button (slot 45)
        if (slot == 45) {
            if (session.type == MenuType.PERMISSIONS) {
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                openTrustMenu(player, session.base);
            } else {
                player.closeInventory();
            }
        }
        // Previous page (slot 48)
        else if (slot == 48 && session.type == MenuType.PERMISSIONS && session.page > 1) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            openPermissionsMenu(player, session.base, session.trustEntry, session.page - 1);
        }
        // Next page (slot 50)
        else if (slot == 50 && session.type == MenuType.PERMISSIONS && session.page < 2) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            openPermissionsMenu(player, session.base, session.trustEntry, session.page + 1);
        }
        // Close button (slot 53)
        else if (slot == 53) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
            player.closeInventory();
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private void fillBorder(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);
        
        // Top row
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }
        // Bottom row (except navbar)
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }
        // Sides
        for (int i = 9; i < 45; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }
    }
    
    private ItemStack createHeader(Player player, Base base) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "✦ Trust Manager");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Base: " + ChatColor.WHITE + base.getLocationString());
        lore.add(ChatColor.GRAY + "Size: " + ChatColor.WHITE + base.getDimensionsString());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click a player to edit their permissions");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createTrustedPlayerItem(TrustEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getTrustedUUID()));
        
        String name = plugin.getTrustManager().getPlayerName(entry.getTrustedUUID());
        meta.setDisplayName(ChatColor.AQUA + name);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GREEN + "Online: " + formatPermissions(entry, true));
        lore.add(ChatColor.YELLOW + "Offline: " + formatPermissions(entry, false));
        lore.add("");
        lore.add(ChatColor.GRAY + "Click to edit permissions");
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private String formatPermissions(TrustEntry entry, boolean online) {
        StringBuilder sb = new StringBuilder();
        if (online) {
            if (entry.canBreakOnline()) sb.append("B");
            if (entry.canPlaceOnline()) sb.append("P");
            if (entry.canContainerOnline()) sb.append("C");
            if (entry.canDoorOnline()) sb.append("D");
            if (entry.canRedstoneOnline()) sb.append("R");
            if (entry.canEntityDamageOnline()) sb.append("E");
            if (entry.canVehicleOnline()) sb.append("V");
            if (entry.canDecorationOnline()) sb.append("A");
        } else {
            if (entry.canBreakOffline()) sb.append("B");
            if (entry.canPlaceOffline()) sb.append("P");
            if (entry.canContainerOffline()) sb.append("C");
            if (entry.canDoorOffline()) sb.append("D");
            if (entry.canRedstoneOffline()) sb.append("R");
            if (entry.canEntityDamageOffline()) sb.append("E");
            if (entry.canVehicleOffline()) sb.append("V");
            if (entry.canDecorationOffline()) sb.append("A");
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }
    
    private ItemStack createAddPlayerItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "➕ Add Trusted Player");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Click to close and type a name");
        lore.add(ChatColor.GRAY + "or use " + ChatColor.WHITE + "/trust <name>");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createSectionLabel(String name, ChatColor color, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color + "━━━ " + name + " ━━━");
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createPermissionToggle(String name, Material icon, boolean enabled, 
                                            String permissionKey, String desc1, String desc2) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ") + ChatColor.WHITE + name);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + desc1);
        lore.add(ChatColor.GRAY + desc2);
        lore.add("");
        lore.add(ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle");
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createRemoveButton(String playerName) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "✗ Remove " + playerName);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Click to remove this player");
        lore.add(ChatColor.GRAY + "from your trusted list.");
        lore.add("");
        lore.add(ChatColor.RED + ChatColor.BOLD.toString() + "This cannot be undone!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private void addNavbar(Inventory inv, String currentMenu) {
        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Back");
        back.setItemMeta(backMeta);
        inv.setItem(45, back);
        
        // Info
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Trust System");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(ChatColor.GRAY + "Manage who can interact");
        infoLore.add(ChatColor.GRAY + "with your protected base.");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);
        
        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✗ Close");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);
    }
    
    private void addPermissionsNavbar(Inventory inv, int currentPage) {
        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Back to Trust List");
        back.setItemMeta(backMeta);
        inv.setItem(45, back);
        
        // Previous page
        if (currentPage > 1) {
            ItemStack prev = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(ChatColor.AQUA + "← Previous Page");
            prev.setItemMeta(prevMeta);
            inv.setItem(48, prev);
        }
        
        // Next page
        if (currentPage < 2) {
            ItemStack next = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(ChatColor.AQUA + "Next Page →");
            next.setItemMeta(nextMeta);
            inv.setItem(50, next);
        }
        
        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✗ Close");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);
    }
    
    private String getPermissionFromSlot(int slot, int page) {
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
    
    private boolean getPermissionValue(TrustEntry entry, String permission) {
        return switch (permission) {
            case "break_online" -> entry.canBreakOnline();
            case "place_online" -> entry.canPlaceOnline();
            case "container_online" -> entry.canContainerOnline();
            case "door_online" -> entry.canDoorOnline();
            case "redstone_online" -> entry.canRedstoneOnline();
            case "entity_damage_online" -> entry.canEntityDamageOnline();
            case "vehicle_online" -> entry.canVehicleOnline();
            case "decoration_online" -> entry.canDecorationOnline();
            case "break_offline" -> entry.canBreakOffline();
            case "place_offline" -> entry.canPlaceOffline();
            case "container_offline" -> entry.canContainerOffline();
            case "door_offline" -> entry.canDoorOffline();
            case "redstone_offline" -> entry.canRedstoneOffline();
            case "entity_damage_offline" -> entry.canEntityDamageOffline();
            case "vehicle_offline" -> entry.canVehicleOffline();
            case "decoration_offline" -> entry.canDecorationOffline();
            default -> false;
        };
    }
    
    // ==================== INNER CLASSES ====================
    
    private enum MenuType {
        TRUST_MAIN,
        PERMISSIONS
    }
    
    private static class MenuSession {
        final MenuType type;
        final Base base;
        final TrustEntry trustEntry;
        final int page;
        
        MenuSession(MenuType type, Base base, TrustEntry trustEntry, int page) {
            this.type = type;
            this.base = base;
            this.trustEntry = trustEntry;
            this.page = page;
        }
    }
}
