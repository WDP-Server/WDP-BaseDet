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
            case "debug" -> handleDebug(sender);
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
        
        if (args.length > 1 && player.hasPermission("basedet.admin.view")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messages.send(player, "commands.player-not-found", "player", args[1]);
                return;
            }
            targetUUID = target.getUniqueId();
            targetName = target.getName();
        } else {
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        }
        
        double score = plugin.getScoreManager().getScore(targetUUID);
        double threshold = config.getDetectionThreshold();
        double percentage = (score / threshold) * 100;
        
        messages.send(player, "commands.detection-score-for", "player", targetName);
        messages.sendRaw(player, "commands.score-display", 
                "score", String.format("%.2f", score),
                "threshold", String.valueOf((int) threshold));
        messages.sendRaw(player, "commands.progress-display", 
                "bar", getProgressBar(percentage),
                "percentage", String.format("%.1f", Math.min(percentage, 100)));
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

    private void handleDebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            // Console gets system info
            if (!sender.hasPermission("basedet.admin.debug")) {
                messages.send(sender, "commands.no-permission");
                return;
            }
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
        
        // Player toggles live debug mode
        if (!player.hasPermission("basedet.user.debug") && !player.hasPermission("basedet.admin.debug")) {
            messages.send(player, "commands.no-permission");
            return;
        }
        
        // Toggle debug mode for this player
        boolean enabled = plugin.getClusterManager().toggleDebug(player.getUniqueId());
        
        if (enabled) {
            player.sendMessage("§8[BaseDet] §aDebug mode enabled!");
            player.sendMessage("§7You will see live messages about:");
            player.sendMessage("§7 • Block placements and their effect on score");
            player.sendMessage("§7 • Mining detection status");
            player.sendMessage("§7 • Cluster creation and removal");
            player.sendMessage("§7 • Score changes with cluster type");
            player.sendMessage("§7Use §e/base debug §7again to disable.");
            
            // Show current cluster status
            var clusters = plugin.getClusterManager().getClusters(player.getUniqueId());
            if (!clusters.isEmpty()) {
                player.sendMessage("§7Current clusters (" + clusters.size() + "/5):");
                for (int i = 0; i < clusters.size(); i++) {
                    var cluster = clusters.get(i);
                    String typeColor = switch (cluster.getType()) {
                        case BASE -> "§a";
                        case MINING -> "§c";
                        case HYBRID -> "§e";
                        case UNKNOWN -> "§7";
                    };
                    player.sendMessage(String.format("§7 %d. %s[%s] §7at %s - Score: §f%.1f",
                            i + 1,
                            typeColor,
                            cluster.getType().name(),
                            cluster.getLocationString(),
                            cluster.getScore()
                    ));
                }
            } else {
                player.sendMessage("§7No active clusters yet. Start building!");
            }
        } else {
            player.sendMessage("§8[BaseDet] §7Debug mode disabled.");
        }
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
            if (sub.equals("force") || sub.equals("info") || sub.equals("delete") || sub.equals("score")) {
                return filterCompletions(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[1]
                );
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
