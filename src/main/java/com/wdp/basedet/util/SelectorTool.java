package com.wdp.basedet.util;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Base Selector Tool - Lands-style implementation
 * Features:
 * - Permanent particle visualization while selecting
 * - Corner beacons with distinct colors
 * - Real-time selection preview
 * - Volume and cost calculation
 * - Chunk boundary visualization
 * - Smooth edge rendering
 */
public class SelectorTool implements Listener {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    private final NamespacedKey selectorKey;
    
    // Selection states per player
    private final Map<UUID, SelectionState> selectionStates = new ConcurrentHashMap<>();
    
    // Visualization tasks
    private final Map<UUID, BukkitTask> visualizationTasks = new ConcurrentHashMap<>();
    
    // Colors for visualization
    private static final Color CORNER_1_COLOR = Color.fromRGB(0, 255, 128);    // Bright green
    private static final Color CORNER_2_COLOR = Color.fromRGB(255, 85, 85);    // Bright red
    private static final Color SELECTION_COLOR = Color.fromRGB(255, 170, 0);   // Orange (like Lands)
    private static final Color CURRENT_BASE_COLOR = Color.fromRGB(85, 255, 255); // Cyan
    private static final Color INVALID_COLOR = Color.fromRGB(255, 0, 0);       // Red
    
    public SelectorTool(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.selectorKey = new NamespacedKey(plugin, "selector_tool");
    }
    
    /**
     * Give a player the selector tool
     */
    public void giveSelectorTool(Player player) {
        ItemStack tool = createSelectorTool();
        
        // Check if player already has one
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSelectorTool(item)) {
                player.sendMessage(formatMessage("&eYou already have a &6Base Selector Tool&e!"));
                return;
            }
        }
        
        player.getInventory().addItem(tool);
        sendToolInstructions(player);
    }
    
    /**
     * Create the selector tool item - Professional look
     */
    private ItemStack createSelectorTool() {
        Material material = config.getSelectorMaterial();
        ItemStack tool = new ItemStack(material);
        ItemMeta meta = tool.getItemMeta();
        
        if (meta != null) {
            // Hex color support for name
            meta.setDisplayName(hex("#FFD700") + "✦ " + hex("#FFFFFF") + "Base Selector Tool");
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(hex("#AAAAAA") + "Modify your base boundaries with precision.");
            lore.add("");
            lore.add(hex("#55FF55") + "▸ " + hex("#FFFF55") + "Left Click" + hex("#AAAAAA") + " - Set first corner");
            lore.add(hex("#FF5555") + "▸ " + hex("#FFFF55") + "Right Click" + hex("#AAAAAA") + " - Set second corner");
            lore.add("");
            lore.add(hex("#55FFFF") + "▸ " + hex("#FFFF55") + "Sneak + Left" + hex("#AAAAAA") + " - Confirm changes");
            lore.add(hex("#FFAAAA") + "▸ " + hex("#FFFF55") + "Sneak + Right" + hex("#AAAAAA") + " - Cancel selection");
            lore.add("");
            lore.add(hex("#AAAAAA") + "━━━━━━━━━━━━━━━━━━━━━━━━");
            lore.add(hex("#FFD700") + "Cost: " + hex("#FFFFFF") + config.getSelectorCostPerBlock() + " SkillCoins/block");
            if (config.isShrinkFree()) {
                lore.add(hex("#55FF55") + "✓ Shrinking is FREE!");
            }
            lore.add("");
            lore.add(hex("#666666") + "WDP-BaseDet Selection Tool");
            
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte) 1);
            tool.setItemMeta(meta);
        }
        
        return tool;
    }
    
    /**
     * Check if an item is the selector tool
     */
    public boolean isSelectorTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (!isSelectorTool(item)) return;
        
        event.setCancelled(true);
        
        Action action = event.getAction();
        boolean sneaking = player.isSneaking();
        
        // Get player's base
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        Base currentBase = bases.stream()
                .filter(Base::isConfirmed)
                .filter(b -> b.getWorldName().equals(player.getWorld().getName()))
                .findFirst()
                .orElse(null);
        
        if (currentBase == null) {
            player.sendMessage(formatMessage("&cYou don't have a confirmed base in this world!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        SelectionState state = selectionStates.computeIfAbsent(player.getUniqueId(),
                k -> new SelectionState(currentBase, player.getWorld().getName()));
        
        if (sneaking) {
            handleSneakAction(player, action, state, currentBase);
        } else {
            handleNormalAction(player, event, action, state, currentBase);
        }
    }
    
    private void handleSneakAction(Player player, Action action, SelectionState state, Base currentBase) {
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // Confirm changes
            applyChanges(player, state, currentBase);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // Cancel selection
            cancelSelection(player);
            player.sendMessage(formatMessage("&eSelection cancelled."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }
    
    private void handleNormalAction(Player player, PlayerInteractEvent event, Action action, 
                                    SelectionState state, Base currentBase) {
        Block clicked = event.getClickedBlock();
        
        if (action == Action.LEFT_CLICK_BLOCK && clicked != null) {
            // Set corner 1
            state.setCorner1(clicked.getX(), clicked.getY(), clicked.getZ());
            
            player.sendMessage("");
            player.sendMessage(hex("#55FF55") + "▸ " + hex("#FFFFFF") + "Corner 1 set: " + 
                    hex("#55FF55") + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
            
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            
            // Start/update visualization
            startVisualization(player, state, currentBase);
            
            if (state.isComplete()) {
                showSelectionPreview(player, state, currentBase);
            }
            
        } else if (action == Action.RIGHT_CLICK_BLOCK && clicked != null) {
            // Set corner 2
            state.setCorner2(clicked.getX(), clicked.getY(), clicked.getZ());
            
            player.sendMessage("");
            player.sendMessage(hex("#FF5555") + "▸ " + hex("#FFFFFF") + "Corner 2 set: " +
                    hex("#FF5555") + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
            
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            
            // Start/update visualization
            startVisualization(player, state, currentBase);
            
            if (state.isComplete()) {
                showSelectionPreview(player, state, currentBase);
            }
            
        } else if (action == Action.LEFT_CLICK_AIR) {
            // Show current base bounds info
            showCurrentBaseInfo(player, currentBase);
        } else if (action == Action.RIGHT_CLICK_AIR) {
            // Show selection info
            if (state.isComplete()) {
                showSelectionPreview(player, state, currentBase);
            } else {
                sendToolInstructions(player);
            }
        }
    }
    
    /**
     * Start continuous particle visualization
     */
    private void startVisualization(Player player, SelectionState state, Base currentBase) {
        // Cancel existing task
        BukkitTask existing = visualizationTasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }
        
        // Start new visualization task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelVisualization(player.getUniqueId());
                return;
            }
            
            // Always show current base bounds (cyan)
            showBoundsParticles(player, currentBase.getBounds(), CURRENT_BASE_COLOR, 1.0f);
            
            // Show corner 1 beacon if set
            if (state.x1 != null) {
                showCornerBeacon(player, state.x1, state.y1, state.z1, CORNER_1_COLOR);
            }
            
            // Show corner 2 beacon if set
            if (state.x2 != null) {
                showCornerBeacon(player, state.x2, state.y2, state.z2, CORNER_2_COLOR);
            }
            
            // Show selection preview if complete
            if (state.isComplete()) {
                BoundingBox newBounds = state.toBoundingBox();
                boolean isValid = validateSelection(newBounds);
                Color color = isValid ? SELECTION_COLOR : INVALID_COLOR;
                showBoundsParticles(player, newBounds, color, 1.5f);
                
                // Show face fill for better visibility
                showSelectionFaces(player, newBounds, color);
            }
            
        }, 0L, 5L); // Every 5 ticks (0.25 seconds)
        
        visualizationTasks.put(player.getUniqueId(), task);
    }
    
    /**
     * Show particles for bounding box edges
     */
    private void showBoundsParticles(Player player, BoundingBox bounds, Color color, float size) {
        World world = player.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX() + 1;
        int minY = bounds.getMinY();
        int maxY = bounds.getMaxY() + 1;
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ() + 1;
        
        double spacing = 0.5;
        
        // Draw all 12 edges
        // Bottom face
        drawLine(player, world, dust, spacing, minX, minY, minZ, maxX, minY, minZ);
        drawLine(player, world, dust, spacing, minX, minY, minZ, minX, minY, maxZ);
        drawLine(player, world, dust, spacing, maxX, minY, minZ, maxX, minY, maxZ);
        drawLine(player, world, dust, spacing, minX, minY, maxZ, maxX, minY, maxZ);
        
        // Top face
        drawLine(player, world, dust, spacing, minX, maxY, minZ, maxX, maxY, minZ);
        drawLine(player, world, dust, spacing, minX, maxY, minZ, minX, maxY, maxZ);
        drawLine(player, world, dust, spacing, maxX, maxY, minZ, maxX, maxY, maxZ);
        drawLine(player, world, dust, spacing, minX, maxY, maxZ, maxX, maxY, maxZ);
        
        // Vertical edges
        drawLine(player, world, dust, spacing, minX, minY, minZ, minX, maxY, minZ);
        drawLine(player, world, dust, spacing, maxX, minY, minZ, maxX, maxY, minZ);
        drawLine(player, world, dust, spacing, minX, minY, maxZ, minX, maxY, maxZ);
        drawLine(player, world, dust, spacing, maxX, minY, maxZ, maxX, maxY, maxZ);
        
        // Draw horizontal slice at player eye level
        int playerY = player.getLocation().getBlockY() + 1;
        if (playerY > minY && playerY < maxY) {
            Particle.DustOptions sliceDust = new Particle.DustOptions(color, size * 0.7f);
            drawLine(player, world, sliceDust, spacing * 1.5, minX, playerY, minZ, maxX, playerY, minZ);
            drawLine(player, world, sliceDust, spacing * 1.5, minX, playerY, minZ, minX, playerY, maxZ);
            drawLine(player, world, sliceDust, spacing * 1.5, maxX, playerY, minZ, maxX, playerY, maxZ);
            drawLine(player, world, sliceDust, spacing * 1.5, minX, playerY, maxZ, maxX, playerY, maxZ);
        }
    }
    
    /**
     * Show corner beacon effect
     */
    private void showCornerBeacon(Player player, int x, int y, int z, Color color) {
        World world = player.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, 2.0f);
        
        // Vertical beam at corner
        for (double dy = 0; dy < 5; dy += 0.3) {
            Location loc = new Location(world, x + 0.5, y + 0.5 + dy, z + 0.5);
            if (player.getLocation().distanceSquared(loc) < 4096) {
                player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
            }
        }
        
        // Cross pattern at base
        Particle.DustOptions baseDust = new Particle.DustOptions(color, 1.5f);
        for (double d = -0.5; d <= 0.5; d += 0.2) {
            Location loc1 = new Location(world, x + 0.5 + d, y + 0.5, z + 0.5);
            Location loc2 = new Location(world, x + 0.5, y + 0.5, z + 0.5 + d);
            if (player.getLocation().distanceSquared(loc1) < 4096) {
                player.spawnParticle(Particle.DUST, loc1, 1, 0, 0, 0, 0, baseDust);
                player.spawnParticle(Particle.DUST, loc2, 1, 0, 0, 0, 0, baseDust);
            }
        }
    }
    
    /**
     * Show selection face fill for better visibility
     */
    private void showSelectionFaces(Player player, BoundingBox bounds, Color color) {
        World world = player.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.8f);
        
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX() + 1;
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ() + 1;
        int playerY = player.getLocation().getBlockY();
        
        // Only show face at player Y level for visibility without spam
        double spacing = 2.0;
        for (double x = minX; x <= maxX; x += spacing) {
            for (double z = minZ; z <= maxZ; z += spacing) {
                // Only draw edges of the face
                if (Math.abs(x - minX) < spacing || Math.abs(x - maxX) < spacing ||
                    Math.abs(z - minZ) < spacing || Math.abs(z - maxZ) < spacing) {
                    continue; // Skip edges, already drawn
                }
                
                // Sparse interior dots
                if ((int)(x + z) % 4 == 0) {
                    Location loc = new Location(world, x, playerY + 0.1, z);
                    if (player.getLocation().distanceSquared(loc) < 2500) {
                        player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }
    }
    
    /**
     * Draw a line of particles
     */
    private void drawLine(Player player, World world, Particle.DustOptions dust,
                          double spacing, double x1, double y1, double z1, double x2, double y2, double z2) {
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        if (distance < 0.1) return;
        
        int count = (int) Math.ceil(distance / spacing);
        double dx = (x2 - x1) / count;
        double dy = (y2 - y1) / count;
        double dz = (z2 - z1) / count;
        
        for (int i = 0; i <= count; i++) {
            Location loc = new Location(world, x1 + dx * i, y1 + dy * i, z1 + dz * i);
            if (player.getLocation().distanceSquared(loc) < 4096) {
                player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
            }
        }
    }
    
    /**
     * Validate selection against limits
     */
    private boolean validateSelection(BoundingBox bounds) {
        return bounds.getWidth() <= config.getMaxBaseWidth() &&
               bounds.getLength() <= config.getMaxBaseLength() &&
               bounds.getHeight() <= config.getMaxBaseHeight() &&
               bounds.getWidth() >= config.getMinBaseWidth() &&
               bounds.getLength() >= config.getMinBaseLength() &&
               bounds.getHeight() >= config.getMinBaseHeight();
    }
    
    /**
     * Show current base information
     */
    private void showCurrentBaseInfo(Player player, Base base) {
        BoundingBox bounds = base.getBounds();
        int volume = bounds.getWidth() * bounds.getLength() * bounds.getHeight();
        
        player.sendMessage("");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(hex("#FFD700") + "  ✦ " + hex("#FFFFFF") + "Current Base Information");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(hex("#AAAAAA") + "  World: " + hex("#FFFFFF") + base.getWorldName());
        player.sendMessage(hex("#AAAAAA") + "  Location: " + hex("#55FFFF") + 
                bounds.getMinX() + ", " + bounds.getMinY() + ", " + bounds.getMinZ() + 
                hex("#AAAAAA") + " → " + hex("#55FFFF") +
                bounds.getMaxX() + ", " + bounds.getMaxY() + ", " + bounds.getMaxZ());
        player.sendMessage("");
        player.sendMessage(hex("#AAAAAA") + "  Size: " + hex("#55FF55") + 
                bounds.getWidth() + hex("#AAAAAA") + " × " + 
                hex("#55FF55") + bounds.getLength() + hex("#AAAAAA") + " × " + 
                hex("#55FF55") + bounds.getHeight());
        player.sendMessage(hex("#AAAAAA") + "  Volume: " + hex("#FFD700") + String.format("%,d", volume) + " blocks");
        player.sendMessage("");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }
    
    /**
     * Show selection preview with cost calculation
     */
    private void showSelectionPreview(Player player, SelectionState state, Base currentBase) {
        BoundingBox oldBounds = currentBase.getBounds();
        BoundingBox newBounds = state.toBoundingBox();
        
        int oldVolume = oldBounds.getWidth() * oldBounds.getLength() * oldBounds.getHeight();
        int newVolume = newBounds.getWidth() * newBounds.getLength() * newBounds.getHeight();
        int difference = newVolume - oldVolume;
        
        // Check validity
        boolean isValid = validateSelection(newBounds);
        
        double cost = 0;
        if (difference > 0) {
            cost = difference * config.getSelectorCostPerBlock();
        } else if (difference < 0 && !config.isShrinkFree()) {
            cost = Math.abs(difference) * config.getSelectorCostPerBlock();
        }
        
        player.sendMessage("");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(hex("#FFD700") + "  ✦ " + hex("#FFFFFF") + "Selection Preview");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        
        // Old size
        player.sendMessage(hex("#55FFFF") + "  Current: " + hex("#AAAAAA") + 
                oldBounds.getWidth() + "×" + oldBounds.getLength() + "×" + oldBounds.getHeight() +
                hex("#666666") + " (" + String.format("%,d", oldVolume) + " blocks)");
        
        // New size
        String sizeColor = isValid ? "#FFAA00" : "#FF5555";
        player.sendMessage(hex(sizeColor) + "  New: " + hex("#AAAAAA") + 
                newBounds.getWidth() + "×" + newBounds.getLength() + "×" + newBounds.getHeight() +
                hex("#666666") + " (" + String.format("%,d", newVolume) + " blocks)");
        player.sendMessage("");
        
        // Change indicator
        if (difference > 0) {
            player.sendMessage(hex("#55FF55") + "  ▲ Expanding: +" + String.format("%,d", difference) + " blocks");
            player.sendMessage(hex("#FFD700") + "  ⬤ Cost: " + hex("#FFFFFF") + String.format("%,.0f", cost) + " SkillCoins");
        } else if (difference < 0) {
            player.sendMessage(hex("#FF5555") + "  ▼ Shrinking: " + String.format("%,d", difference) + " blocks");
            if (config.isShrinkFree()) {
                player.sendMessage(hex("#55FF55") + "  ⬤ Cost: FREE");
            } else {
                player.sendMessage(hex("#FFD700") + "  ⬤ Cost: " + hex("#FFFFFF") + String.format("%,.0f", cost) + " SkillCoins");
            }
        } else {
            player.sendMessage(hex("#FFFF55") + "  ◆ Same size - location change only");
            player.sendMessage(hex("#55FF55") + "  ⬤ Cost: FREE");
        }
        
        player.sendMessage("");
        
        // Validation messages
        if (!isValid) {
            player.sendMessage(hex("#FF5555") + "  ⚠ INVALID SELECTION:");
            if (newBounds.getWidth() > config.getMaxBaseWidth() ||
                newBounds.getLength() > config.getMaxBaseLength() ||
                newBounds.getHeight() > config.getMaxBaseHeight()) {
                player.sendMessage(hex("#FF5555") + "    • Exceeds maximum size limits!");
            }
            if (newBounds.getWidth() < config.getMinBaseWidth() ||
                newBounds.getLength() < config.getMinBaseLength() ||
                newBounds.getHeight() < config.getMinBaseHeight()) {
                player.sendMessage(hex("#FF5555") + "    • Below minimum size limits!");
            }
            player.sendMessage("");
        }
        
        player.sendMessage(hex("#AAAAAA") + "  " + hex("#FFFF55") + "Sneak + Left Click" + hex("#AAAAAA") + " to confirm");
        player.sendMessage(hex("#AAAAAA") + "  " + hex("#FFFF55") + "Sneak + Right Click" + hex("#AAAAAA") + " to cancel");
        player.sendMessage("");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.0f);
    }
    
    /**
     * Apply changes to base
     */
    private void applyChanges(Player player, SelectionState state, Base currentBase) {
        if (!state.isComplete()) {
            player.sendMessage(formatMessage("&cYou must set both corners first!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        BoundingBox oldBounds = currentBase.getBounds();
        BoundingBox newBounds = state.toBoundingBox();
        
        // Validate
        if (!validateSelection(newBounds)) {
            player.sendMessage(formatMessage("&cSelection exceeds size limits! Check the preview."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }
        
        // Calculate cost
        int oldVolume = oldBounds.getWidth() * oldBounds.getLength() * oldBounds.getHeight();
        int newVolume = newBounds.getWidth() * newBounds.getLength() * newBounds.getHeight();
        int difference = newVolume - oldVolume;
        
        double cost = 0;
        if (difference > 0) {
            cost = difference * config.getSelectorCostPerBlock();
        } else if (difference < 0 && !config.isShrinkFree()) {
            cost = Math.abs(difference) * config.getSelectorCostPerBlock();
        }
        
        // Check if player can afford
        if (cost > 0 && plugin.getEconomyIntegration() != null) {
            if (!plugin.getEconomyIntegration().hasBalance(player, cost)) {
                player.sendMessage(formatMessage("&cYou don't have enough SkillCoins!"));
                player.sendMessage(hex("#AAAAAA") + "  Required: " + hex("#FFD700") + String.format("%,.0f", cost) + " SkillCoins");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
                return;
            }
            
            // Withdraw
            plugin.getEconomyIntegration().withdraw(player, cost);
        }
        
        // Update database
        plugin.getDatabaseManager().updateBaseBounds(currentBase.getId(), newBounds);
        currentBase.setBounds(newBounds);
        
        // Success message
        player.sendMessage("");
        player.sendMessage(hex("#55FF55") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(hex("#55FF55") + "  ✓ " + hex("#FFFFFF") + "Base boundaries updated!");
        player.sendMessage(hex("#55FF55") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(hex("#AAAAAA") + "  New size: " + hex("#FFFFFF") + 
                newBounds.getWidth() + " × " + newBounds.getLength() + " × " + newBounds.getHeight());
        if (cost > 0) {
            player.sendMessage(hex("#AAAAAA") + "  Charged: " + hex("#FFD700") + String.format("%,.0f", cost) + " SkillCoins");
        }
        player.sendMessage("");
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
        
        // Clean up
        cancelSelection(player);
    }
    
    /**
     * Cancel selection
     */
    public void cancelSelection(Player player) {
        selectionStates.remove(player.getUniqueId());
        cancelVisualization(player.getUniqueId());
    }
    
    /**
     * Cancel visualization task
     */
    private void cancelVisualization(UUID uuid) {
        BukkitTask task = visualizationTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Send tool instructions to player
     */
    private void sendToolInstructions(Player player) {
        player.sendMessage("");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(hex("#FFD700") + "  ✦ " + hex("#FFFFFF") + "Base Selector Tool");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(hex("#55FF55") + "  ▸ " + hex("#FFFF55") + "Left Click Block" + hex("#AAAAAA") + " - Set corner 1 " + hex("#55FF55") + "(green)");
        player.sendMessage(hex("#FF5555") + "  ▸ " + hex("#FFFF55") + "Right Click Block" + hex("#AAAAAA") + " - Set corner 2 " + hex("#FF5555") + "(red)");
        player.sendMessage("");
        player.sendMessage(hex("#AAAAAA") + "  ▸ " + hex("#FFFF55") + "Left Click Air" + hex("#AAAAAA") + " - View current base");
        player.sendMessage(hex("#AAAAAA") + "  ▸ " + hex("#FFFF55") + "Right Click Air" + hex("#AAAAAA") + " - View selection");
        player.sendMessage("");
        player.sendMessage(hex("#55FFFF") + "  ▸ " + hex("#FFFF55") + "Sneak + Left" + hex("#AAAAAA") + " - Confirm changes");
        player.sendMessage(hex("#FFAAAA") + "  ▸ " + hex("#FFFF55") + "Sneak + Right" + hex("#AAAAAA") + " - Cancel");
        player.sendMessage("");
        player.sendMessage(hex("#FFD700") + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        selectionStates.remove(uuid);
        cancelVisualization(uuid);
    }
    
    /**
     * Helper: Format message with prefix
     */
    private String formatMessage(String message) {
        return config.getMessagePrefix() + org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Helper: Create hex color string
     */
    private String hex(String hexCode) {
        return ChatColor.of(hexCode).toString();
    }
    
    // ==================== INNER CLASS ====================
    
    private static class SelectionState {
        private final Base originalBase;
        private final String worldName;
        Integer x1, y1, z1;
        Integer x2, y2, z2;
        
        SelectionState(Base originalBase, String worldName) {
            this.originalBase = originalBase;
            this.worldName = worldName;
        }
        
        void setCorner1(int x, int y, int z) {
            this.x1 = x;
            this.y1 = y;
            this.z1 = z;
        }
        
        void setCorner2(int x, int y, int z) {
            this.x2 = x;
            this.y2 = y;
            this.z2 = z;
        }
        
        boolean isComplete() {
            return x1 != null && x2 != null;
        }
        
        BoundingBox toBoundingBox() {
            if (!isComplete()) return null;
            return new BoundingBox(x1, y1, z1, x2, y2, z2);
        }
    }
}
