package com.wdp.basedet.command;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.config.MessageManager;
import com.wdp.basedet.detection.DetectionManager;
import com.wdp.basedet.model.Base;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main command handler for /basedet (/bd, /base)
 */
public class BaseDetCommand implements CommandExecutor, TabCompleter {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    
    private static final List<String> USER_SUBCOMMANDS = Arrays.asList(
            "confirm", "deny", "view", "detect", "score", "help", "tool", "expand", "menu"
    );
    
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "reload", "debug", "force", "info", "list", "delete"
    );
    
    public BaseDetCommand(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessages();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No args = open main menu
        if (args.length == 0) {
            handleMenu(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "confirm" -> handleConfirm(sender);
            case "deny" -> handleDeny(sender);
            case "view" -> handleView(sender);
            case "detect" -> handleDetect(sender);
            case "score" -> handleScore(sender, args);
            case "help" -> sendHelp(sender);
            case "tool" -> handleTool(sender);
            case "expand" -> handleExpand(sender, args);
            case "menu" -> handleMenu(sender);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "force" -> handleForce(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "delete" -> handleDelete(sender, args);
            default -> {
                messages.send(sender, "commands.unknown-command");
            }
        }
        
        return true;
    }
    
    private void handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        DetectionManager detection = plugin.getDetectionManager();
        
        if (!detection.hasActivePrompt(player.getUniqueId())) {
            messages.send(player, "detection.no-pending-detection");
            return;
        }
        
        detection.confirmBase(player, true);
    }
    
    private void handleDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        DetectionManager detection = plugin.getDetectionManager();
        
        if (!detection.hasActivePrompt(player.getUniqueId())) {
            messages.send(player, "detection.no-pending-detection");
            return;
        }
        
        detection.denyBase(player);
    }
    
    private void handleView(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        if (!player.hasPermission("basedet.user.view")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        // Toggle particle view
        boolean nowViewing = plugin.getParticleManager().toggleViewing(player.getUniqueId());
        
        if (nowViewing) {
            messages.send(player, "commands.view-enabled");
        } else {
            messages.send(player, "commands.view-disabled");
        }
        
        // Also show base info
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        if (bases.isEmpty()) {
            messages.send(player, "commands.no-bases-yet");
        } else {
            messages.send(player, "commands.your-bases", "count", String.valueOf(bases.size()));
            for (int i = 0; i < bases.size(); i++) {
                Base base = bases.get(i);
                messages.sendRaw(player, "commands.base-list-item", 
                        "number", String.valueOf(i + 1),
                        "location", base.getLocationString(),
                        "size", base.getDimensionsString());
            }
        }
    }
    
    private void handleDetect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        if (!player.hasPermission("basedet.user.detect")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        double score = plugin.getScoreManager().getScore(player.getUniqueId());
        double threshold = config.getDetectionThreshold();
        
        if (score < threshold * 0.5) {
            messages.send(player, "commands.not-enough-activity");
            messages.sendRaw(player, "commands.current-score", 
                    "score", String.format("%.1f", score),
                    "threshold", String.valueOf((int) threshold));
            return;
        }
        
        messages.send(player, "commands.analyzing-activity");
        
        plugin.getDetectionManager().triggerManualDetection(player);
    }
    
    private void handleScore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        UUID targetUUID;
        String targetName;
        boolean showAll = false;
        
        // Parse arguments: /base score [all] [player]
        int playerArgIndex = -1;
        for (int i = 1; i < args.length; i++) {
            if ("all".equalsIgnoreCase(args[i])) {
                showAll = true;
            } else if (playerArgIndex == -1) {
                playerArgIndex = i;
            }
        }
        
        if (playerArgIndex >= 0 && player.hasPermission("basedet.admin.view")) {
            Player target = Bukkit.getPlayer(args[playerArgIndex]);
            if (target == null) {
                messages.send(player, "commands.player-not-found", "player", args[playerArgIndex]);
                return;
            }
            targetUUID = target.getUniqueId();
            targetName = target.getName();
        } else {
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        }
        
        double threshold = config.getDetectionThreshold();
        
        messages.send(player, "commands.detection-score-for", "player", targetName);
        
        var clusters = plugin.getClusterManager().getClusters(targetUUID);
        
        if (clusters.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  No activity clusters detected yet.");
            player.sendMessage(ChatColor.GRAY + "  Start placing blocks to build detection score.");
            return;
        }
        
        if (showAll) {
            // Show ALL clusters
            player.sendMessage(ChatColor.YELLOW + "  All Clusters: " + ChatColor.WHITE + clusters.size());
            player.sendMessage("");
            
            int i = 1;
            for (var cluster : clusters) {
                double clusterScore = cluster.getScore();
                double percentage = (clusterScore / threshold) * 100;
                String typeColor = switch (cluster.getType()) {
                    case BASE -> ChatColor.GREEN.toString();
                    case MINING -> ChatColor.RED.toString();
                    case HYBRID -> ChatColor.YELLOW.toString();
                    case UNKNOWN -> ChatColor.GRAY.toString();
                };
                
                player.sendMessage(ChatColor.GRAY + "  Cluster " + i + " [" + typeColor + cluster.getType().name() + ChatColor.GRAY + "]:");
                player.sendMessage(ChatColor.GRAY + "    Location: " + ChatColor.WHITE + cluster.getLocationString());
                player.sendMessage(ChatColor.GRAY + "    Score: " + ChatColor.WHITE + String.format("%.1f", clusterScore) + 
                        ChatColor.GRAY + "/" + ChatColor.WHITE + (int) threshold);
                player.sendMessage(ChatColor.GRAY + "    " + getProgressBar(percentage) + ChatColor.GRAY + 
                        " " + String.format("%.1f", Math.min(percentage, 100)) + "%");
                i++;
            }
        } else {
            // Show ONLY the closest cluster (the one being detected)
            var closest = plugin.getClusterManager().getHighestScoringCluster(targetUUID);
            if (closest != null) {
                double clusterScore = closest.getScore();
                double percentage = (clusterScore / threshold) * 100;
                String typeColor = switch (closest.getType()) {
                    case BASE -> ChatColor.GREEN.toString();
                    case MINING -> ChatColor.RED.toString();
                    case HYBRID -> ChatColor.YELLOW.toString();
                    case UNKNOWN -> ChatColor.GRAY.toString();
                };
                
                player.sendMessage(ChatColor.GRAY + "  Active Detection [" + typeColor + closest.getType().name() + ChatColor.GRAY + "]:");
                player.sendMessage(ChatColor.GRAY + "    Location: " + ChatColor.WHITE + closest.getLocationString());
                player.sendMessage(ChatColor.GRAY + "    Score: " + ChatColor.WHITE + String.format("%.1f", clusterScore) + 
                        ChatColor.GRAY + "/" + ChatColor.WHITE + (int) threshold);
                player.sendMessage(ChatColor.GRAY + "    " + getProgressBar(percentage) + ChatColor.GRAY + 
                        " " + String.format("%.1f", Math.min(percentage, 100)) + "%");
                
                if (clusters.size() > 1) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.DARK_GRAY + "  Tip: Use " + ChatColor.GRAY + "/base score all" + 
                            ChatColor.DARK_GRAY + " to see all clusters");
                }
            }
        }
    }
    
    private String getProgressBar(double percentage) {
        int filled = (int) Math.min(percentage / 5, 20);
        StringBuilder bar = new StringBuilder(ChatColor.GREEN.toString());
        for (int i = 0; i < 20; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append(ChatColor.GRAY).append("░");
            }
        }
        return bar.toString();
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("basedet.admin.reload")) {
            messages.send(sender, "commands.no-permission");
            return;
        }
        
        plugin.reload();
        messages.send(sender, "commands.config-reloaded");
    }
    
    private void handleTool(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        if (!player.hasPermission("basedet.user.tool")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        // Check if selector is enabled
        if (!config.isSelectorEnabled()) {
            messages.send(player, "commands.selector-disabled");
            return;
        }
        
        // Check if player has a base
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        if (bases.isEmpty()) {
            messages.send(player, "commands.no-confirmed-base");
            return;
        }
        
        plugin.getSelectorTool().giveSelectorTool(player);
    }
    
    private void handleExpand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        if (args.length < 2) {
            messages.send(player, "commands.expand-usage");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "confirm" -> plugin.getExpansionManager().confirmExpansion(player);
            case "deny" -> plugin.getExpansionManager().denyExpansion(player);
            default -> messages.send(player, "commands.expand-usage");
        }
    }
    
    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return;
        }
        
        if (!player.hasPermission("basedet.user.menu")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        // Get player's confirmed bases
        List<Base> confirmedBases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId())
                .stream()
                .filter(Base::isConfirmed)
                .toList();
        
        if (confirmedBases.isEmpty()) {
            messages.send(player, "commands.no-confirmed-base");
            return;
        }
        
        // If player has multiple bases, open base selector
        if (confirmedBases.size() > 1) {
            plugin.getMenuManager().openBaseSelector(player, confirmedBases);
        } else {
            // Single base - open main menu directly
            plugin.getMenuManager().openMainMenu(player, confirmedBases.get(0));
        }
    }

    private void handleDebug(CommandSender sender, String[] args) {
        // /base debug [toggle|clusters|detail|clear]
        String subCmd = args.length > 1 ? args[1].toLowerCase() : "toggle";
        
        if (!(sender instanceof Player player)) {
            // Console gets system info
            if (!sender.hasPermission("basedet.admin.debug")) {
                messages.send(sender, "commands.no-permission");
                return;
            }
            
            if (subCmd.equals("clusters")) {
                // Show all players' clusters
                sender.sendMessage("§6=== All Active Clusters ===");
                var allClusters = plugin.getClusterManager().getAllPlayerClusters();
                if (allClusters.isEmpty()) {
                    sender.sendMessage("§7No active clusters.");
                } else {
                    for (var entry : allClusters.entrySet()) {
                        Player p = Bukkit.getPlayer(entry.getKey());
                        String name = p != null ? p.getName() : entry.getKey().toString();
                        sender.sendMessage("§e" + name + "§7: " + entry.getValue().size() + " clusters");
                        for (var cluster : entry.getValue()) {
                            sender.sendMessage(String.format("  §7[%s] %s - Score: %.1f",
                                    cluster.getType().name(),
                                    cluster.getLocationString(),
                                    cluster.getScore()
                            ));
                        }
                    }
                }
                return;
            }
            
            // Default console info
            messages.send(sender, "commands.debug-info");
            messages.sendRaw(sender, "commands.debug-database", "type", config.getDatabaseType());
            messages.sendRaw(sender, "commands.debug-economy", "status", 
                    String.valueOf(plugin.getEconomyIntegration() != null && plugin.getEconomyIntegration().isEnabled()));
            messages.sendRaw(sender, "commands.debug-discord", "status", 
                    String.valueOf(plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()));
            messages.sendRaw(sender, "commands.debug-cmi", "status", 
                    String.valueOf(plugin.getCmiIntegration() != null && plugin.getCmiIntegration().isEnabled()));
            return;
        }
        
        // Player commands
        if (!player.hasPermission("basedet.user.debug") && !player.hasPermission("basedet.admin.debug")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        switch (subCmd) {
            case "toggle" -> handleDebugToggle(player);
            case "clusters", "list" -> handleDebugClusters(player);
            case "detail", "details" -> handleDebugDetail(player, args);
            case "clear" -> handleDebugClear(player);
            default -> {
                player.sendMessage("§6=== Debug Commands ===");
                player.sendMessage("§e/base debug toggle §7- Toggle live debug messages");
                player.sendMessage("§e/base debug clusters §7- View all your clusters");
                player.sendMessage("§e/base debug detail <#> §7- Detailed cluster info");
                player.sendMessage("§e/base debug clear §7- Clear all your clusters");
            }
        }
    }
    
    private void handleDebugToggle(Player player) {
        boolean enabled = plugin.getClusterManager().toggleDebug(player.getUniqueId());
        
        if (enabled) {
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§8[BaseDet] §a✓ Debug Mode ENABLED");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§7Live messages will show:");
            player.sendMessage("  §a● §7Block interactions & score changes");
            player.sendMessage("  §a● §7Cluster type classification");
            player.sendMessage("  §a● §7Mining detection analysis");
            player.sendMessage("  §a● §7Cluster creation & removal");
            player.sendMessage("  §a● §7Score penalties & bonuses");
            player.sendMessage("");
            player.sendMessage("§7Commands:");
            player.sendMessage("  §e/base debug clusters §7- View all clusters");
            player.sendMessage("  §e/base debug detail <#> §7- Detailed info");
            player.sendMessage("  §e/base debug toggle §7- Disable");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // Show current cluster status
            handleDebugClusters(player);
        } else {
            player.sendMessage("§8[BaseDet] §7Debug mode disabled.");
        }
    }
    
    private void handleDebugClusters(Player player) {
        var clusters = plugin.getClusterManager().getClusters(player.getUniqueId());
        
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§6⬡ Your Active Clusters §7(" + clusters.size() + "/5)");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (clusters.isEmpty()) {
            player.sendMessage("§7No active clusters yet.");
            player.sendMessage("§7Start building to create a cluster!");
        } else {
            for (int i = 0; i < clusters.size(); i++) {
                var cluster = clusters.get(i);
                String typeIcon = switch (cluster.getType()) {
                    case BASE -> "§a⌂";
                    case MINING -> "§c⛏";
                    case HYBRID -> "§e⚒";
                    case UNKNOWN -> "§7?";
                };
                String typeColor = switch (cluster.getType()) {
                    case BASE -> "§a";
                    case MINING -> "§c";
                    case HYBRID -> "§e";
                    case UNKNOWN -> "§7";
                };
                
                player.sendMessage("");
                player.sendMessage(String.format("§f#%d %s %s%s", 
                        i + 1, typeIcon, typeColor, cluster.getType().name()));
                player.sendMessage(String.format("  §7Location: §f%s", cluster.getLocationString()));
                player.sendMessage(String.format("  §7Score: §f%.1f §7/ §f%.0f §7(threshold)", 
                        cluster.getScore(), config.getDetectionThreshold()));
                player.sendMessage(String.format("  §7Blocks: §f%d §7broken, §f%d §7placed",
                        cluster.getBlocksBroken(), cluster.getBlocksPlaced()));
                
                if (cluster.getType() != com.wdp.basedet.detection.LocationCluster.ClusterType.UNKNOWN) {
                    double ratio = cluster.getBlocksPlaced() > 0 
                            ? (double) cluster.getBlocksBroken() / cluster.getBlocksPlaced()
                            : cluster.getBlocksBroken();
                    player.sendMessage(String.format("  §7Break/Place Ratio: §f%.2f", ratio));
                }
                
                if (cluster.getOresBroken() > 0) {
                    player.sendMessage(String.format("  §7Ores: §f%d §c⛏", cluster.getOresBroken()));
                }
                
                // Age
                long ageMs = System.currentTimeMillis() - cluster.getCreatedAt();
                long ageMinutes = ageMs / 60000;
                if (ageMinutes < 60) {
                    player.sendMessage(String.format("  §7Age: §f%d §7minutes", ageMinutes));
                } else {
                    player.sendMessage(String.format("  §7Age: §f%.1f §7hours", ageMinutes / 60.0));
                }
            }
            player.sendMessage("");
            player.sendMessage("§7Use §e/base debug detail <#> §7for more info");
        }
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    private void handleDebugDetail(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /base debug detail <cluster#>");
            return;
        }
        
        int index;
        try {
            index = Integer.parseInt(args[2]) - 1;
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid cluster number!");
            return;
        }
        
        var clusters = plugin.getClusterManager().getClusters(player.getUniqueId());
        if (index < 0 || index >= clusters.size()) {
            player.sendMessage("§cCluster #" + (index + 1) + " does not exist!");
            return;
        }
        
        var cluster = clusters.get(index);
        
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(String.format("§6⬡ Cluster #%d Details", index + 1));
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Type
        String typeIcon = switch (cluster.getType()) {
            case BASE -> "§a⌂";
            case MINING -> "§c⛏";
            case HYBRID -> "§e⚒";
            case UNKNOWN -> "§7?";
        };
        player.sendMessage(String.format("§7Type: %s §f%s", typeIcon, cluster.getType().name()));
        
        // Location
        player.sendMessage(String.format("§7Center: §f%d, %d, %d §7in §f%s",
                cluster.getCenterX(), cluster.getCenterY(), cluster.getCenterZ(), cluster.getWorld()));
        
        // Score
        player.sendMessage(String.format("§7Score: §f%.2f §7/ §f%.0f", 
                cluster.getScore(), config.getDetectionThreshold()));
        double progress = (cluster.getScore() / config.getDetectionThreshold()) * 100;
        player.sendMessage(String.format("§7Progress: §f%.1f%%", progress));
        
        // Activity stats
        player.sendMessage("");
        player.sendMessage("§e⚡ Activity Stats:");
        player.sendMessage(String.format("  §7Blocks Broken: §f%d", cluster.getBlocksBroken()));
        player.sendMessage(String.format("  §7Blocks Placed: §f%d", cluster.getBlocksPlaced()));
        player.sendMessage(String.format("  §7Total Blocks: §f%d", 
                cluster.getBlocksBroken() + cluster.getBlocksPlaced()));
        
        if (cluster.getBlocksPlaced() > 0) {
            double ratio = (double) cluster.getBlocksBroken() / cluster.getBlocksPlaced();
            String ratioColor = ratio > 5 ? "§c" : ratio > 2 ? "§e" : "§a";
            player.sendMessage(String.format("  §7Break/Place Ratio: %s%.2f", ratioColor, ratio));
        }
        
        // Mining indicators
        if (cluster.getBlocksBroken() > 0) {
            player.sendMessage("");
            player.sendMessage("§c⛏ Mining Indicators:");
            player.sendMessage(String.format("  §7Ores Broken: §f%d", cluster.getOresBroken()));
            
            double orePercent = (double) cluster.getOresBroken() / cluster.getBlocksBroken() * 100;
            String oreColor = orePercent > 10 ? "§c" : orePercent > 5 ? "§e" : "§7";
            player.sendMessage(String.format("  §7Ore Percentage: %s%.1f%%", oreColor, orePercent));
        }
        
        // Base indicators
        player.sendMessage("");
        player.sendMessage("§a⌂ Base Indicators:");
        player.sendMessage("  §7Bed: " + (cluster.hasBed() ? "§a✓" : "§7✗"));
        player.sendMessage("  §7Door: " + (cluster.hasDoor() ? "§a✓" : "§7✗"));
        player.sendMessage("  §7Chest: " + (cluster.hasChest() ? "§a✓" : "§7✗"));
        
        // Time info
        player.sendMessage("");
        player.sendMessage("§b⏱ Time Info:");
        long ageMs = System.currentTimeMillis() - cluster.getCreatedAt();
        long ageMinutes = ageMs / 60000;
        long ageHours = ageMinutes / 60;
        if (ageHours > 0) {
            player.sendMessage(String.format("  §7Created: §f%d §7hours §f%d §7minutes ago", 
                    ageHours, ageMinutes % 60));
        } else {
            player.sendMessage(String.format("  §7Created: §f%d §7minutes ago", ageMinutes));
        }
        
        long lastMs = System.currentTimeMillis() - cluster.getLastActivity();
        long lastMinutes = lastMs / 60000;
        if (lastMinutes > 0) {
            player.sendMessage(String.format("  §7Last Activity: §f%d §7minutes ago", lastMinutes));
        } else {
            player.sendMessage("  §7Last Activity: §ajust now");
        }
        
        // Expiry warning
        int expiryHours = config.getClusterExpiryHours();
        long remainingHours = expiryHours - (lastMinutes / 60);
        if (remainingHours < 2) {
            player.sendMessage(String.format("  §c⚠ Expires in: §f%d §7hours §7(if inactive)", remainingHours));
        }
        
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    private void handleDebugClear(Player player) {
        if (!player.hasPermission("basedet.admin.debug")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        var clusters = plugin.getClusterManager().getClusters(player.getUniqueId());
        int count = clusters.size();
        
        plugin.getClusterManager().clearAllClusters(player.getUniqueId());
        
        player.sendMessage("§8[BaseDet] §7Cleared §f" + count + " §7clusters.");
    }
    
    private void handleForce(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.force")) {
            messages.send(sender, "commands.no-permission");
            return;
        }
        
        if (args.length < 2) {
            messages.send(sender, "commands.force-usage");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "commands.player-not-found", "player", args[1]);
            return;
        }
        
        plugin.getDetectionManager().triggerManualDetection(target);
        messages.send(sender, "commands.force-triggered", "player", target.getName());
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.view")) {
            messages.send(sender, "commands.no-permission");
            return;
        }
        
        if (args.length < 2) {
            messages.send(sender, "commands.info-usage");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "commands.player-not-found", "player", args[1]);
            return;
        }
        
        UUID uuid = target.getUniqueId();
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(uuid);
        double score = plugin.getScoreManager().getScore(uuid);
        
        messages.send(sender, "commands.player-info", "player", target.getName());
        sender.sendMessage(ChatColor.GRAY + "  Score: " + ChatColor.WHITE + 
                String.format("%.2f", score));
        sender.sendMessage(ChatColor.GRAY + "  Bases: " + ChatColor.WHITE + bases.size());
        
        for (int i = 0; i < bases.size(); i++) {
            Base base = bases.get(i);
            sender.sendMessage(ChatColor.GRAY + "    " + (i + 1) + ". " + 
                    base.getLocationString() + " (" + base.getDimensionsString() + ")");
        }
    }
    
    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.view")) {
            messages.send(sender, "commands.no-permission");
            return;
        }
        
        List<Base> allBases = plugin.getDatabaseManager().getAllConfirmedBases();
        
        messages.send(sender, "commands.all-bases-header", "count", String.valueOf(allBases.size()));
        
        for (Base base : allBases) {
            String ownerName = plugin.getTrustManager().getPlayerName(base.getOwnerUUID());
            messages.sendRaw(sender, "commands.all-bases-item", 
                    "owner", ownerName,
                    "location", base.getLocationString());
        }
    }
    
    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.bypass")) {
            messages.send(sender, "commands.no-permission");
            return;
        }
        
        if (args.length < 2) {
            messages.send(sender, "commands.delete-usage");
            return;
        }
        
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore()) {
            messages.send(sender, "commands.player-not-found", "player", args[1]);
            return;
        }
        
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(target.getUniqueId());
        for (Base base : bases) {
            plugin.getDatabaseManager().deleteBase(base.getId());
        }
        
        messages.send(sender, "commands.bases-deleted", 
                "count", String.valueOf(bases.size()),
                "player", target.getName());
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        messages.sendRaw(sender, "help.header");
        sender.sendMessage("");
        messages.sendRaw(sender, "help.user-commands-header");
        messages.sendRaw(sender, "help.menu");
        messages.sendRaw(sender, "help.confirm");
        messages.sendRaw(sender, "help.deny");
        messages.sendRaw(sender, "help.view");
        messages.sendRaw(sender, "help.detect");
        messages.sendRaw(sender, "help.score");
        messages.sendRaw(sender, "help.tool");
        messages.sendRaw(sender, "help.trust");
        
        if (sender.hasPermission("basedet.admin")) {
            sender.sendMessage("");
            messages.sendRaw(sender, "help.admin-commands-header");
            messages.sendRaw(sender, "help.reload");
            messages.sendRaw(sender, "help.debug");
            messages.sendRaw(sender, "help.force");
            messages.sendRaw(sender, "help.info");
            messages.sendRaw(sender, "help.list");
            messages.sendRaw(sender, "help.delete");
        }
        sender.sendMessage("");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(USER_SUBCOMMANDS);
            if (sender.hasPermission("basedet.admin")) {
                completions.addAll(ADMIN_SUBCOMMANDS);
            }
            return filterCompletions(completions, args[0]);
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            
            // Debug subcommands
            if (sub.equals("debug")) {
                completions.addAll(Arrays.asList("toggle", "clusters", "detail", "clear"));
                return filterCompletions(completions, args[1]);
            }
            
            // Player name completions
            if (sub.equals("force") || sub.equals("info") || sub.equals("delete") || sub.equals("score")) {
                return filterCompletions(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[1]
                );
            }
        }
        
        // Cluster number completion for /base debug detail
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("detail")) {
            if (sender instanceof Player player) {
                var clusters = plugin.getClusterManager().getClusters(player.getUniqueId());
                for (int i = 1; i <= clusters.size(); i++) {
                    completions.add(String.valueOf(i));
                }
                return filterCompletions(completions, args[2]);
            }
        }
        
        return completions;
    }
    
    private List<String> filterCompletions(List<String> completions, String input) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
