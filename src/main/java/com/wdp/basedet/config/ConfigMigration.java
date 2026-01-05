package com.wdp.basedet.config;

import com.wdp.basedet.WDPBaseDetPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Handles configuration migration between versions
 * Preserves user settings while adding new config options
 */
public class ConfigMigration {
    
    private final WDPBaseDetPlugin plugin;
    private static final int CURRENT_VERSION = 2;
    
    public ConfigMigration(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check if config needs migration and migrate if necessary
     * @param configFile The config file to check
     * @param resourcePath Path to default config in JAR
     * @return true if migration occurred
     */
    public boolean migrateConfig(File configFile, String resourcePath) {
        if (!configFile.exists()) {
            return false; // New config, no migration needed
        }
        
        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);
        int userVersion = userConfig.getInt("config-version", 1);
        
        if (userVersion >= CURRENT_VERSION) {
            return false; // Up to date
        }
        
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Config version mismatch detected!");
        plugin.getLogger().info("User version: " + userVersion);
        plugin.getLogger().info("Current version: " + CURRENT_VERSION);
        plugin.getLogger().info("Starting automatic migration...");
        plugin.getLogger().info("========================================");
        
        try {
            // Backup old config
            backupConfig(configFile);
            
            // Load default config from JAR
            InputStream defaultStream = plugin.getResource(resourcePath);
            if (defaultStream == null) {
                plugin.getLogger().severe("Could not load default config from JAR!");
                return false;
            }
            
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream)
            );
            
            // Merge configs (user values take priority)
            FileConfiguration mergedConfig = mergeConfigs(userConfig, defaultConfig);
            
            // Update version
            mergedConfig.set("config-version", CURRENT_VERSION);
            
            // Save merged config
            mergedConfig.save(configFile);
            
            plugin.getLogger().info("========================================");
            plugin.getLogger().info("Config migration completed successfully!");
            plugin.getLogger().info("Old config backed up to: " + configFile.getName() + ".backup");
            plugin.getLogger().info("New options added, your settings preserved.");
            plugin.getLogger().info("========================================");
            
            return true;
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to migrate config: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Backup config file with timestamp
     */
    private void backupConfig(File configFile) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupFile = new File(configFile.getParent(), 
            configFile.getName() + ".backup." + timestamp);
        Files.copy(configFile.toPath(), backupFile.toPath(), 
            StandardCopyOption.REPLACE_EXISTING);
        
        // Also keep a simple .backup file (overwrite previous)
        File simpleBackup = new File(configFile.getParent(), 
            configFile.getName() + ".backup");
        Files.copy(configFile.toPath(), simpleBackup.toPath(), 
            StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Merge user config with default config
     * User values take priority, but new keys from default are added
     */
    private FileConfiguration mergeConfigs(FileConfiguration userConfig, 
                                          FileConfiguration defaultConfig) {
        FileConfiguration merged = new YamlConfiguration();
        
        // First, copy all default values (structure + new options)
        for (String key : defaultConfig.getKeys(true)) {
            if (!defaultConfig.isConfigurationSection(key)) {
                merged.set(key, defaultConfig.get(key));
            }
        }
        
        // Then override with user values (preserves user settings)
        for (String key : userConfig.getKeys(true)) {
            if (!userConfig.isConfigurationSection(key)) {
                merged.set(key, userConfig.get(key));
            }
        }
        
        return merged;
    }
    
    /**
     * Check language file version and warn if different
     * @param languageFile The language file to check
     * @param resourcePath Path to default language in JAR
     */
    public void checkLanguageVersion(File languageFile, String resourcePath) {
        if (!languageFile.exists()) {
            return; // New file, no check needed
        }
        
        FileConfiguration userLang = YamlConfiguration.loadConfiguration(languageFile);
        
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream == null) {
            return;
        }
        
        FileConfiguration defaultLang = YamlConfiguration.loadConfiguration(
            new InputStreamReader(defaultStream)
        );
        
        String userVersion = userLang.getString("language-version", "unknown");
        String defaultVersion = defaultLang.getString("language-version", "unknown");
        
        if (!userVersion.equals(defaultVersion)) {
            plugin.getLogger().warning("========================================");
            plugin.getLogger().warning("LANGUAGE FILE VERSION MISMATCH");
            plugin.getLogger().warning("Your " + languageFile.getName() + " version: " + userVersion);
            plugin.getLogger().warning("Current version: " + defaultVersion);
            plugin.getLogger().warning("");
            plugin.getLogger().warning("This may result in missing or outdated messages.");
            plugin.getLogger().warning("You can:");
            plugin.getLogger().warning("  1. Delete " + languageFile.getName() + " to generate new one");
            plugin.getLogger().warning("  2. Manually update your translations");
            plugin.getLogger().warning("  3. Ignore this warning if intentional");
            plugin.getLogger().warning("========================================");
        }
    }
}
