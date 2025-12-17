package com.wdp.basedet.command;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
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
    
    private static final List<String> USER_SUBCOMMANDS = Arrays.asList(
            "confirm", "deny", "view", "detect", "score", "help", "tool", "expand", "menu"
    );
    
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "reload", "debug", "force", "info", "list", "delete"
    );
    
    public BaseDetCommand(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
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
                sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + "Unknown command. Use /base help");
            }
        }
        
        return true;
    }
    
    private void handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        DetectionManager detection = plugin.getDetectionManager();
        
        if (!detection.hasActivePrompt(player.getUniqueId())) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "You don't have a pending base detection!");
            return;
        }
        
        detection.confirmBase(player, true);
    }
    
    private void handleDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        DetectionManager detection = plugin.getDetectionManager();
        
        if (!detection.hasActivePrompt(player.getUniqueId())) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "You don't have a pending base detection!");
            return;
        }
        
        detection.denyBase(player);
    }
    
    private void handleView(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        if (!player.hasPermission("basedet.user.view")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        // Toggle particle view
        boolean nowViewing = plugin.getParticleManager().toggleViewing(player.getUniqueId());
        
        if (nowViewing) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                    "Base visualization enabled. You can now see your base boundaries.");
        } else {
            player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                    "Base visualization disabled.");
        }
        
        // Also show base info
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        if (bases.isEmpty()) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.GRAY + 
                    "You don't have any confirmed bases yet.");
        } else {
            player.sendMessage(config.getMessagePrefix() + ChatColor.AQUA + 
                    "Your bases (" + bases.size() + "):");
            for (int i = 0; i < bases.size(); i++) {
                Base base = bases.get(i);
                player.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " + 
                        ChatColor.WHITE + base.getLocationString() + 
                        ChatColor.GRAY + " (" + base.getDimensionsString() + ")");
            }
        }
    }
    
    private void handleDetect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        if (!player.hasPermission("basedet.user.detect")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        double score = plugin.getScoreManager().getScore(player.getUniqueId());
        double threshold = config.getDetectionThreshold();
        
        if (score < threshold * 0.5) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                    "Not enough activity detected yet. Keep building!");
            player.sendMessage(ChatColor.GRAY + "  Current score: " + ChatColor.WHITE + 
                    String.format("%.1f", score) + ChatColor.GRAY + " / " + threshold);
            return;
        }
        
        player.sendMessage(config.getMessagePrefix() + ChatColor.AQUA + 
                "Analyzing your activity...");
        
        plugin.getDetectionManager().triggerManualDetection(player);
    }
    
    private void handleScore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        UUID targetUUID;
        String targetName;
        
        if (args.length > 1 && player.hasPermission("basedet.admin.view")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                        "Player not found: " + args[1]);
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
        
        player.sendMessage(config.getMessagePrefix() + ChatColor.AQUA + 
                "Detection Score for " + targetName + ":");
        player.sendMessage(ChatColor.GRAY + "  Score: " + ChatColor.WHITE + 
                String.format("%.2f", score) + ChatColor.GRAY + " / " + threshold);
        player.sendMessage(ChatColor.GRAY + "  Progress: " + getProgressBar(percentage) + 
                ChatColor.WHITE + " " + String.format("%.1f%%", Math.min(percentage, 100)));
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
            sender.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        plugin.reload();
        sender.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                "Configuration reloaded!");
    }
    
    private void handleTool(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        if (!player.hasPermission("basedet.user.tool")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        // Check if player has a base
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        if (bases.isEmpty()) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "You don't have a confirmed base yet!");
            return;
        }
        
        plugin.getSelectorTool().giveSelectorTool(player);
    }
    
    private void handleExpand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Usage: /base expand <confirm|deny>");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "confirm" -> plugin.getExpansionManager().confirmExpansion(player);
            case "deny" -> plugin.getExpansionManager().denyExpansion(player);
            default -> player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Usage: /base expand <confirm|deny>");
        }
    }
    
    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }
        
        if (!player.hasPermission("basedet.user.menu")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        // Get player's base in current world
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        Base currentBase = bases.stream()
                .filter(Base::isConfirmed)
                .filter(b -> b.getWorldName().equals(player.getWorld().getName()))
                .findFirst()
                .orElse(null);
        
        if (currentBase == null) {
            // Try any confirmed base
            currentBase = bases.stream()
                    .filter(Base::isConfirmed)
                    .findFirst()
                    .orElse(null);
        }
        
        if (currentBase == null) {
            player.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "You don't have a confirmed base yet!");
            return;
        }
        
        plugin.getMenuManager().openMainMenu(player, currentBase);
    }

    private void handleDebug(CommandSender sender) {
        if (!sender.hasPermission("basedet.admin.debug")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        // Toggle debug mode (would need to implement in config)
        sender.sendMessage(config.getMessagePrefix() + ChatColor.YELLOW + 
                "Debug info:");
        sender.sendMessage(ChatColor.GRAY + "  Database: " + config.getDatabaseType());
        sender.sendMessage(ChatColor.GRAY + "  Economy: " + 
                (plugin.getEconomyIntegration() != null && plugin.getEconomyIntegration().isEnabled()));
        sender.sendMessage(ChatColor.GRAY + "  DiscordSRV: " + 
                (plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()));
        sender.sendMessage(ChatColor.GRAY + "  CMI: " + 
                (plugin.getCmiIntegration() != null && plugin.getCmiIntegration().isEnabled()));
    }
    
    private void handleForce(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.force")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Usage: /base force <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Player not found: " + args[1]);
            return;
        }
        
        plugin.getDetectionManager().triggerManualDetection(target);
        sender.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                "Forced detection for " + target.getName());
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.view")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Usage: /base info <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Player not found: " + args[1]);
            return;
        }
        
        UUID uuid = target.getUniqueId();
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(uuid);
        double score = plugin.getScoreManager().getScore(uuid);
        
        sender.sendMessage(config.getMessagePrefix() + ChatColor.AQUA + 
                "Player Info: " + target.getName());
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
            sender.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        List<Base> allBases = plugin.getDatabaseManager().getAllConfirmedBases();
        
        sender.sendMessage(config.getMessagePrefix() + ChatColor.AQUA + 
                "All Protected Bases (" + allBases.size() + "):");
        
        for (Base base : allBases) {
            String ownerName = plugin.getTrustManager().getPlayerName(base.getOwnerUUID());
            sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + ownerName + 
                    ChatColor.GRAY + ": " + base.getLocationString());
        }
    }
    
    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("basedet.admin.bypass")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Usage: /base delete <player>");
            return;
        }
        
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore()) {
            sender.sendMessage(config.getMessagePrefix() + ChatColor.RED + 
                    "Player not found: " + args[1]);
            return;
        }
        
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(target.getUniqueId());
        for (Base base : bases) {
            plugin.getDatabaseManager().deleteBase(base.getId());
        }
        
        sender.sendMessage(config.getMessagePrefix() + ChatColor.GREEN + 
                "Deleted " + bases.size() + " bases for " + target.getName());
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "━━━ " + ChatColor.AQUA + "WDP-BaseDet Help" + 
                ChatColor.GOLD + " ━━━");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "User Commands:");
        sender.sendMessage(ChatColor.WHITE + "  /base menu" + ChatColor.GRAY + 
                " - Open base management menu");
        sender.sendMessage(ChatColor.WHITE + "  /base confirm" + ChatColor.GRAY + 
                " - Confirm a detected base");
        sender.sendMessage(ChatColor.WHITE + "  /base deny" + ChatColor.GRAY + 
                " - Deny a detected base");
        sender.sendMessage(ChatColor.WHITE + "  /base view" + ChatColor.GRAY + 
                " - Toggle base visualization");
        sender.sendMessage(ChatColor.WHITE + "  /base detect" + ChatColor.GRAY + 
                " - Manually trigger detection");
        sender.sendMessage(ChatColor.WHITE + "  /base score" + ChatColor.GRAY + 
                " - View your detection score");
        sender.sendMessage(ChatColor.WHITE + "  /base tool" + ChatColor.GRAY + 
                " - Get base selector tool");
        sender.sendMessage(ChatColor.WHITE + "  /trust" + ChatColor.GRAY + 
                " - Manage trusted players");
        
        if (sender.hasPermission("basedet.admin")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "Admin Commands:");
            sender.sendMessage(ChatColor.WHITE + "  /base reload" + ChatColor.GRAY + 
                    " - Reload configuration");
            sender.sendMessage(ChatColor.WHITE + "  /base debug" + ChatColor.GRAY + 
                    " - View debug info");
            sender.sendMessage(ChatColor.WHITE + "  /base force <player>" + ChatColor.GRAY + 
                    " - Force detection");
            sender.sendMessage(ChatColor.WHITE + "  /base info <player>" + ChatColor.GRAY + 
                    " - View player info");
            sender.sendMessage(ChatColor.WHITE + "  /base list" + ChatColor.GRAY + 
                    " - List all bases");
            sender.sendMessage(ChatColor.WHITE + "  /base delete <player>" + ChatColor.GRAY + 
                    " - Delete player's bases");
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
