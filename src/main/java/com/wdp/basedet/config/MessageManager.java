package com.wdp.basedet.config;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified Message Manager for WDP plugins
 * Supports multi-language through separate language files
 * 
 * Features:
 * - Hex color support: &#RRGGBB
 * - Placeholder support: {key}
 * - List support for multi-line messages
 * - Default fallback for missing messages
 * - Language file selection (future: per-player language)
 */
public class MessageManager {
    
    private final WDPBaseDetPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;
    private String currentLanguage = "en";
    
    // Hex color pattern: &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    
    public MessageManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }
    
    /**
     * Load messages for the current language
     */
    private void loadMessages() {
        // Create lang folder if it doesn't exist
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        
        // Try language-specific file first
        String langFileName = "lang/messages_" + currentLanguage + ".yml";
        messagesFile = new File(plugin.getDataFolder(), langFileName);
        
        if (!messagesFile.exists()) {
            // Try to save from resources
            try {
                if (plugin.getResource(langFileName) != null) {
                    plugin.saveResource(langFileName, false);
                }
            } catch (Exception ignored) {}
        }
        
        // Fall back to messages.yml if lang file doesn't exist
        if (!messagesFile.exists()) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
            if (!messagesFile.exists()) {
                plugin.saveResource("messages.yml", false);
            }
        }
        
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load defaults from jar for missing keys
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
                messages.setDefaults(defaultConfig);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load default messages: " + e.getMessage());
            }
        }
    }
    
    /**
     * Reload messages (call after language change or file edit)
     */
    public void reload() {
        loadMessages();
    }
    
    /**
     * Set the current language
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
        loadMessages();
    }
    
    /**
     * Get current language code
     */
    public String getLanguage() {
        return currentLanguage;
    }
    
    /**
     * Get a message with color codes translated
     * @param path The message path in the YAML file
     * @return The translated message or a "missing message" placeholder
     */
    public String get(String path) {
        String message = messages.getString(path);
        if (message == null) {
            return "§cMissing message: " + path;
        }
        return translateColors(message);
    }
    
    /**
     * Get a message with placeholders replaced
     * @param path The message path
     * @param placeholders Key-value pairs: "key1", "value1", "key2", "value2"...
     * @return The translated message with placeholders
     */
    public String get(String path, String... placeholders) {
        String message = get(path);
        
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        
        return message;
    }
    
    /**
     * Get a list of messages
     */
    public List<String> getList(String path) {
        List<String> list = messages.getStringList(path);
        if (list.isEmpty()) {
            // Try to get as single string and wrap in list
            String single = messages.getString(path);
            if (single != null) {
                list = new ArrayList<>();
                list.add(single);
            }
        }
        return list.stream()
            .map(this::translateColors)
            .toList();
    }
    
    /**
     * Get a list with placeholders replaced
     */
    public List<String> getList(String path, String... placeholders) {
        List<String> list = getList(path);
        
        return list.stream().map(line -> {
            String result = line;
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
            return result;
        }).toList();
    }
    
    /**
     * Check if a message path exists
     */
    public boolean has(String path) {
        return messages.contains(path);
    }
    
    /**
     * Get an integer value from messages
     */
    public int getInt(String path, int defaultValue) {
        return messages.getInt(path, defaultValue);
    }
    
    /**
     * Get a boolean value from messages
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return messages.getBoolean(path, defaultValue);
    }
    
    // ==================== SEND METHODS ====================
    
    /**
     * Send a prefixed message to a command sender
     */
    public void send(CommandSender sender, String path) {
        sender.sendMessage(getPrefix() + get(path));
    }
    
    /**
     * Send a prefixed message with placeholders
     */
    public void send(CommandSender sender, String path, String... placeholders) {
        sender.sendMessage(getPrefix() + get(path, placeholders));
    }
    
    /**
     * Send a raw message (no prefix)
     */
    public void sendRaw(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }
    
    /**
     * Send a raw message with placeholders (no prefix)
     */
    public void sendRaw(CommandSender sender, String path, String... placeholders) {
        sender.sendMessage(get(path, placeholders));
    }
    
    /**
     * Send a list of messages (no prefix)
     */
    public void sendList(CommandSender sender, String path) {
        getList(path).forEach(sender::sendMessage);
    }
    
    /**
     * Send a list of messages with placeholders (no prefix)
     */
    public void sendList(CommandSender sender, String path, String... placeholders) {
        getList(path, placeholders).forEach(sender::sendMessage);
    }
    
    /**
     * Get the message prefix
     */
    public String getPrefix() {
        return get("prefix");
    }
    
    // ==================== COLOR TRANSLATION ====================
    
    /**
     * Translate hex colors and standard color codes
     * Supports: &#RRGGBB format and &c, &a, etc.
     */
    public String translateColors(String text) {
        if (text == null) return "";
        
        // First, translate hex colors
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            // Convert to Bukkit's hex format: §x§R§R§G§G§B§B
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        
        // Then translate standard color codes (&c, &a, etc.)
        return buffer.toString().replace("&", "§");
    }
    
    // ==================== MENU ITEM HELPERS ====================
    
    /**
     * Get a display name for a menu item
     */
    public String getItemName(String path) {
        return get(path + ".name");
    }
    
    /**
     * Get lore for a menu item
     */
    public List<String> getItemLore(String path) {
        return getList(path + ".lore");
    }
    
    /**
     * Get lore with placeholders
     */
    public List<String> getItemLore(String path, String... placeholders) {
        return getList(path + ".lore", placeholders);
    }
}
