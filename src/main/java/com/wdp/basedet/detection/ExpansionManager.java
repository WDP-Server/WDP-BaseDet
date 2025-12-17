package com.wdp.basedet.detection;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages base expansion detection
 * Monitors player activity outside their existing base and prompts for expansion
 */
public class ExpansionManager {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    
    // Track expansion activity per base
    private final Map<Long, ExpansionData> expansionTracking = new ConcurrentHashMap<>();
    
    // Active expansion prompts
    private final Map<UUID, ExpansionPrompt> activePrompts = new ConcurrentHashMap<>();
    
    public ExpansionManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    /**
     * Track a block interaction for potential expansion
     */
    public void trackInteraction(UUID playerUUID, PlayerInteraction interaction) {
        if (!config.isExpansionEnabled()) {
            return;
        }
        
        // Get player's confirmed bases
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(playerUUID);
        if (bases.isEmpty()) {
            return;
        }
        
        // Find the nearest base
        Base nearestBase = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Base base : bases) {
            if (!base.isConfirmed()) continue;
            if (!base.getWorldName().equals(interaction.getWorld())) continue;
            
            double distance = base.getBounds().getDistanceToEdge(
                    interaction.getX(), interaction.getY(), interaction.getZ());
            
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestBase = base;
            }
        }
        
        if (nearestBase == null) {
            return;
        }
        
        // Check if interaction is outside base but within threshold distance
        if (nearestDistance > 0 && nearestDistance <= config.getExpansionDistanceThreshold()) {
            trackExpansionActivity(nearestBase, interaction);
        }
    }
    
    /**
     * Track expansion activity for a base
     */
    private void trackExpansionActivity(Base base, PlayerInteraction interaction) {
        ExpansionData data = expansionTracking.computeIfAbsent(base.getId(), 
                k -> new ExpansionData(base.getId(), base.getOwnerUUID()));
        
        data.addInteraction(interaction);
        
        // Check if we've reached the threshold
        if (data.getBlockCount() >= config.getExpansionMinBlocks()) {
            // Calculate new bounds
            BoundingBox newBounds = calculateExpandedBounds(base, data.getInteractions());
            
            if (newBounds != null && !newBounds.equals(base.getBounds())) {
                // Trigger expansion prompt
                triggerExpansionPrompt(base, newBounds, data.getBlockCount());
            }
        }
    }
    
    /**
     * Calculate expanded bounds based on new interactions
     */
    private BoundingBox calculateExpandedBounds(Base base, List<PlayerInteraction> newInteractions) {
        BoundingBox current = base.getBounds().copy();
        
        for (PlayerInteraction interaction : newInteractions) {
            current.expand(interaction.getX(), interaction.getY(), interaction.getZ());
        }
        
        // Apply offsets
        current.expandBy(config.getHorizontalOffset(), config.getVerticalOffset());
        
        // Clamp to max size
        current.clampToMax(config.getMaxBaseWidth(), config.getMaxBaseLength(), config.getMaxBaseHeight());
        
        return current;
    }
    
    /**
     * Trigger an expansion prompt for a player
     */
    private void triggerExpansionPrompt(Base base, BoundingBox newBounds, int blockCount) {
        UUID ownerUUID = base.getOwnerUUID();
        
        // Check if already has active prompt
        if (activePrompts.containsKey(ownerUUID)) {
            return;
        }
        
        Player player = Bukkit.getPlayer(ownerUUID);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // Create prompt
        ExpansionPrompt prompt = new ExpansionPrompt(player, base, newBounds, blockCount);
        activePrompts.put(ownerUUID, prompt);
        
        // Show particles for new bounds
        plugin.getParticleManager().startShowingBounds(ownerUUID, newBounds, base.getWorldName());
        
        // Send message
        player.sendMessage("");
        player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + "⬢ Base Expansion Detected!");
        player.sendMessage(ChatColor.GRAY + "  Your base has grown by " + ChatColor.WHITE + blockCount + " blocks");
        player.sendMessage(ChatColor.GRAY + "  New size: " + ChatColor.WHITE + newBounds.getWidth() + "x" + 
                newBounds.getLength() + "x" + newBounds.getHeight());
        player.sendMessage("");
        
        // Create clickable buttons
        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text("  ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text("[✓ Expand]", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/basedet expand confirm"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Click to confirm expansion", net.kyori.adventure.text.format.NamedTextColor.GREEN))))
                .append(net.kyori.adventure.text.Component.text("  ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text("[✗ Keep Current]", net.kyori.adventure.text.format.NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/basedet expand deny"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Click to keep current size", net.kyori.adventure.text.format.NamedTextColor.RED))))
                .build();
        
        player.sendMessage(message);
        player.sendMessage("");
        
        // Start auto-deny timer (1 minute)
        prompt.startTimer();
    }
    
    /**
     * Confirm a base expansion
     */
    public boolean confirmExpansion(Player player) {
        UUID uuid = player.getUniqueId();
        ExpansionPrompt prompt = activePrompts.get(uuid);
        
        if (prompt == null) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + "No pending expansion to confirm.");
            return false;
        }
        
        Base base = prompt.getBase();
        BoundingBox newBounds = prompt.getNewBounds();
        
        // Update base bounds in database
        plugin.getDatabaseManager().updateBaseBounds(base.getId(), newBounds);
        base.setBounds(newBounds);
        
        // Clear expansion tracking for this base
        expansionTracking.remove(base.getId());
        
        // Give reward (usually 0 for expansion)
        double reward = config.getExpansionReward();
        if (reward > 0 && plugin.getEconomyIntegration() != null) {
            plugin.getEconomyIntegration().deposit(player, reward);
            player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                    "You received " + ChatColor.GOLD + reward + " SkillCoins" + 
                    ChatColor.GREEN + " for confirming the expansion!");
        }
        
        player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                "Base expanded! New size: " + newBounds.getWidth() + "x" + 
                newBounds.getLength() + "x" + newBounds.getHeight());
        
        cleanupPrompt(uuid);
        return true;
    }
    
    /**
     * Deny a base expansion
     */
    public boolean denyExpansion(Player player) {
        UUID uuid = player.getUniqueId();
        ExpansionPrompt prompt = activePrompts.get(uuid);
        
        if (prompt == null) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + "No pending expansion to deny.");
            return false;
        }
        
        // Clear expansion tracking but keep the base
        expansionTracking.remove(prompt.getBase().getId());
        
        player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                "Expansion cancelled. Your base size remains unchanged.");
        
        cleanupPrompt(uuid);
        return true;
    }
    
    /**
     * Check if player has active expansion prompt
     */
    public boolean hasActivePrompt(UUID uuid) {
        return activePrompts.containsKey(uuid);
    }
    
    /**
     * Cleanup prompt resources
     */
    private void cleanupPrompt(UUID uuid) {
        ExpansionPrompt prompt = activePrompts.remove(uuid);
        if (prompt != null) {
            prompt.cancelTimer();
        }
        plugin.getParticleManager().stopShowingBounds(uuid);
    }
    
    /**
     * Called when player quits
     */
    public void onPlayerQuit(Player player) {
        cleanupPrompt(player.getUniqueId());
    }
    
    // ==================== INNER CLASSES ====================
    
    /**
     * Tracks expansion activity for a base
     */
    private static class ExpansionData {
        private final long baseId;
        private final UUID ownerUUID;
        private final List<PlayerInteraction> interactions = new ArrayList<>();
        
        ExpansionData(long baseId, UUID ownerUUID) {
            this.baseId = baseId;
            this.ownerUUID = ownerUUID;
        }
        
        void addInteraction(PlayerInteraction interaction) {
            interactions.add(interaction);
        }
        
        int getBlockCount() {
            return interactions.size();
        }
        
        List<PlayerInteraction> getInteractions() {
            return interactions;
        }
    }
    
    /**
     * Active expansion prompt
     */
    private class ExpansionPrompt {
        private final Player player;
        private final Base base;
        private final BoundingBox newBounds;
        private final int blockCount;
        private BukkitTask timerTask;
        
        ExpansionPrompt(Player player, Base base, BoundingBox newBounds, int blockCount) {
            this.player = player;
            this.base = base;
            this.newBounds = newBounds;
            this.blockCount = blockCount;
        }
        
        void startTimer() {
            // Auto-deny after 60 seconds
            timerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activePrompts.containsKey(player.getUniqueId())) {
                    denyExpansion(player);
                    player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                            "Expansion prompt expired. Keeping current base size.");
                }
            }, 60 * 20L);
        }
        
        void cancelTimer() {
            if (timerTask != null) {
                timerTask.cancel();
            }
        }
        
        Base getBase() { return base; }
        BoundingBox getNewBounds() { return newBounds; }
    }
}
