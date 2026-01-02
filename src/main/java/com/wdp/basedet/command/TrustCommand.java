package com.wdp.basedet.command;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.config.MessageManager;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.TrustEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command handler for /trust
 */
public class TrustCommand implements CommandExecutor, TabCompleter {
    
    private final WDPBaseDetPlugin plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    
    public TrustCommand(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessages();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.players-only");
            return true;
        }
        
        if (!player.hasPermission("basedet.user.trust")) {
            messages.send(player, "commands.no-permission");
            return true;
        }
        
        // Get player's bases
        List<Base> bases = plugin.getDatabaseManager().getPlayerBases(player.getUniqueId());
        if (bases.isEmpty()) {
            messages.send(player, "commands.no-base");
            return true;
        }
        
        if (args.length == 0) {
            // Open trust menu
            plugin.getMenuManager().openTrustMenu(player, bases.get(0));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "add" -> handleAdd(player, args, bases);
            case "remove" -> handleRemove(player, args, bases);
            case "list" -> handleList(player, bases);
            default -> {
                // Try to add player by name directly
                handleQuickAdd(player, args[0], bases);
            }
        }
        
        return true;
    }
    
    private void handleAdd(Player player, String[] args, List<Base> bases) {
        if (args.length < 2) {
            messages.send(player, "trust.add-usage");
            return;
        }
        
        handleQuickAdd(player, args[1], bases);
    }
    
    private void handleQuickAdd(Player player, String targetName, List<Base> bases) {
        UUID targetUUID = plugin.getTrustManager().getPlayerUUID(targetName);
        if (targetUUID == null) {
            messages.send(player, "commands.player-not-found", "player", targetName);
            return;
        }
        
        if (targetUUID.equals(player.getUniqueId())) {
            messages.send(player, "trust.cant-trust-yourself");
            return;
        }
        
        // Add to all bases
        for (Base base : bases) {
            plugin.getDatabaseManager().addTrust(base.getId(), targetUUID);
        }
        
        String resolvedName = plugin.getTrustManager().getPlayerName(targetUUID);
        messages.send(player, "trust.trusted-added", "player", resolvedName);
        
        // Notify the trusted player if online
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null && target.isOnline()) {
            messages.send(target, "trust.trusted-by-notification", "owner", player.getName());
        }
    }
    
    private void handleRemove(Player player, String[] args, List<Base> bases) {
        if (args.length < 2) {
            messages.send(player, "trust.remove-usage");
            return;
        }
        
        UUID targetUUID = plugin.getTrustManager().getPlayerUUID(args[1]);
        if (targetUUID == null) {
            messages.send(player, "commands.player-not-found", "player", args[1]);
            return;
        }
        
        // Remove from all bases
        for (Base base : bases) {
            plugin.getDatabaseManager().removeTrust(base.getId(), targetUUID);
        }
        
        String resolvedName = plugin.getTrustManager().getPlayerName(targetUUID);
        messages.send(player, "trust.trusted-removed", "player", resolvedName);
    }
    
    private void handleList(Player player, List<Base> bases) {
        messages.send(player, "trust.trusted-players-header");
        
        boolean anyTrusted = false;
        for (Base base : bases) {
            List<TrustEntry> trusted = plugin.getDatabaseManager().getBaseTrusted(base.getId());
            
            if (!trusted.isEmpty()) {
                anyTrusted = true;
                messages.sendRaw(player, "trust.base-at-location", "location", base.getLocationString());
                
                for (TrustEntry entry : trusted) {
                    String name = plugin.getTrustManager().getPlayerName(entry.getTrustedUUID());
                    StringBuilder perms = new StringBuilder();
                    
                    // Online permissions
                    perms.append(messages.get("trust.online-label"));
                    if (entry.canBreakOnline()) perms.append("B");
                    if (entry.canPlaceOnline()) perms.append("P");
                    if (entry.canContainerOnline()) perms.append("C");
                    if (entry.canDoorOnline()) perms.append("D");
                    if (entry.canRedstoneOnline()) perms.append("R");
                    if (entry.canEntityDamageOnline()) perms.append("E");
                    if (entry.canVehicleOnline()) perms.append("V");
                    if (entry.canDecorationOnline()) perms.append("A");
                    
                    // Offline permissions
                    perms.append(messages.get("trust.offline-label"));
                    if (entry.canBreakOffline()) perms.append("B");
                    if (entry.canPlaceOffline()) perms.append("P");
                    if (entry.canContainerOffline()) perms.append("C");
                    if (entry.canDoorOffline()) perms.append("D");
                    if (entry.canRedstoneOffline()) perms.append("R");
                    if (entry.canEntityDamageOffline()) perms.append("E");
                    if (entry.canVehicleOffline()) perms.append("V");
                    if (entry.canDecorationOffline()) perms.append("A");
                    
                    messages.sendRaw(player, "trust.player-entry", "name", name);
                    messages.sendRaw(player, "trust.permissions-display", "permissions", perms.toString());
                }
            }
        }
        
        if (!anyTrusted) {
            messages.sendRaw(player, "trust.no-trusted-players");
            messages.sendRaw(player, "trust.use-trust-command");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("add");
            completions.add("remove");
            completions.add("list");
            // Also suggest online players
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(sender))
                    .map(Player::getName)
                    .toList());
            return filterCompletions(completions, args[0]);
        }
        
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            return filterCompletions(
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.equals(sender))
                            .map(Player::getName)
                            .collect(Collectors.toList()),
                    args[1]
            );
        }
        
        return completions;
    }
    
    private List<String> filterCompletions(List<String> completions, String input) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
