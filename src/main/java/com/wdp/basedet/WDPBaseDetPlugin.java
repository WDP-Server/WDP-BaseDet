package com.wdp.basedet;

import com.wdp.basedet.combat.CombatManager;
import com.wdp.basedet.command.BaseDetCommand;
import com.wdp.basedet.command.TrustCommand;
import com.wdp.basedet.config.ConfigManager;
import com.wdp.basedet.config.MessageManager;
import com.wdp.basedet.database.DatabaseManager;
import com.wdp.basedet.detection.DetectionManager;
import com.wdp.basedet.detection.ExpansionManager;
import com.wdp.basedet.detection.ScoreManager;
import com.wdp.basedet.integration.CMIIntegration;
import com.wdp.basedet.integration.DiscordIntegration;
import com.wdp.basedet.integration.EconomyIntegration;
import com.wdp.basedet.listener.BlockListener;
import com.wdp.basedet.listener.PlayerListener;
import com.wdp.basedet.listener.ProtectionListener;
import com.wdp.basedet.protection.ProtectionManager;
import com.wdp.basedet.trust.TrustManager;
import com.wdp.basedet.ui.MenuManager;
import com.wdp.basedet.util.ParticleManager;
import com.wdp.basedet.util.SelectorTool;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * WDP-BaseDet - Automatic Base Detection and Protection Plugin
 * 
 * Features:
 * - Automatic base detection via player activity scoring
 * - Offline base protection
 * - Trust system with GUI
 * - Discord notifications via DiscordSRV
 * - Expansion detection
 * - Smart combat system via CMI or custom
 */
public class WDPBaseDetPlugin extends JavaPlugin {
    
    private static WDPBaseDetPlugin instance;
    
    // Managers
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private DetectionManager detectionManager;
    private ExpansionManager expansionManager;
    private ScoreManager scoreManager;
    private ProtectionManager protectionManager;
    private TrustManager trustManager;
    private MenuManager menuManager;
    private ParticleManager particleManager;
    private CombatManager combatManager;
    private SelectorTool selectorTool;
    
    // Integrations
    private EconomyIntegration economyIntegration;
    private DiscordIntegration discordIntegration;
    private CMIIntegration cmiIntegration;
    
    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();
        
        getLogger().info("========================================");
        getLogger().info("  WDP-BaseDet v" + getDescription().getVersion());
        getLogger().info("  Automatic Base Detection System");
        getLogger().info("========================================");
        
        // Load configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        // Load messages
        messageManager = new MessageManager(this);
        
        // Initialize database
        getLogger().info("Initializing database...");
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("✓ Database initialized (" + configManager.getDatabaseType() + ")");
        
        // Initialize managers
        getLogger().info("Initializing managers...");
        scoreManager = new ScoreManager(this);
        detectionManager = new DetectionManager(this);
        expansionManager = new ExpansionManager(this);
        protectionManager = new ProtectionManager(this);
        trustManager = new TrustManager(this);
        menuManager = new MenuManager(this);
        particleManager = new ParticleManager(this);
        selectorTool = new SelectorTool(this);
        getLogger().info("✓ Managers initialized");
        
        // Setup integrations
        setupIntegrations();
        
        // Register listeners
        registerListeners();
        
        // Register commands
        registerCommands();
        
        // Start tasks
        startTasks();
        
        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("========================================");
        getLogger().info("  WDP-BaseDet enabled in " + loadTime + "ms");
        getLogger().info("========================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Disabling WDP-BaseDet...");
        
        // Save any pending data
        if (scoreManager != null) {
            scoreManager.saveAllScores();
        }
        
        // Close database connections
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        
        // Cancel tasks
        Bukkit.getScheduler().cancelTasks(this);
        
        getLogger().info("WDP-BaseDet disabled.");
    }
    
    private void setupIntegrations() {
        getLogger().info("Setting up integrations...");
        
        // Economy (Vault/SkillCoins)
        economyIntegration = new EconomyIntegration(this);
        if (economyIntegration.setup()) {
            getLogger().info("✓ Economy integration enabled (Vault/SkillCoins)");
        } else {
            getLogger().warning("✗ Economy integration not available - rewards disabled");
        }
        
        // DiscordSRV
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
            discordIntegration = new DiscordIntegration(this);
            if (discordIntegration.setup()) {
                getLogger().info("✓ DiscordSRV integration enabled");
            } else {
                getLogger().warning("✗ DiscordSRV found but integration failed");
            }
        } else {
            getLogger().info("○ DiscordSRV not found - Discord features disabled");
        }
        
        // CMI (Combat)
        if (Bukkit.getPluginManager().getPlugin("CMI") != null) {
            cmiIntegration = new CMIIntegration(this);
            if (cmiIntegration.setup()) {
                getLogger().info("✓ CMI integration enabled (Smart Combat)");
            } else {
                getLogger().warning("✗ CMI found but integration failed");
            }
        } else {
            getLogger().info("○ CMI not found - using custom combat detection");
        }
        
        // Combat Manager (uses CMI if available, otherwise custom)
        combatManager = new CombatManager(this, cmiIntegration);
        if (combatManager.isEnabled()) {
            Bukkit.getPluginManager().registerEvents(combatManager, this);
            if (combatManager.isUsingCustom()) {
                getLogger().info("✓ Custom combat detection enabled");
            } else {
                getLogger().info("✓ Combat manager using CMI");
            }
        }
        
        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.wdp.basedet.integration.BaseDetPlaceholders(this).register();
            getLogger().info("✓ PlaceholderAPI integration enabled");
        } else {
            getLogger().info("○ PlaceholderAPI not found - placeholders disabled");
        }
    }
    
    private void registerListeners() {
        getLogger().info("Registering listeners...");
        
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(menuManager, this);
        Bukkit.getPluginManager().registerEvents(selectorTool, this);
        
        getLogger().info("✓ Listeners registered");
    }
    
    private void registerCommands() {
        getLogger().info("Registering commands...");
        
        BaseDetCommand baseDetCommand = new BaseDetCommand(this);
        getCommand("basedet").setExecutor(baseDetCommand);
        getCommand("basedet").setTabCompleter(baseDetCommand);
        
        TrustCommand trustCommand = new TrustCommand(this);
        getCommand("trust").setExecutor(trustCommand);
        getCommand("trust").setTabCompleter(trustCommand);
        
        getLogger().info("✓ Commands registered");
    }
    
    private void startTasks() {
        getLogger().info("Starting scheduled tasks...");
        
        // Score decay task (runs every minute)
        if (configManager.isScoreDecayEnabled()) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                scoreManager.decayScores();
            }, 20L * 60, 20L * 60); // Every minute
            getLogger().info("✓ Score decay task started");
        }
        
        // Auto-save task (runs every 5 minutes)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            scoreManager.saveAllScores();
        }, 20L * 300, 20L * 300); // Every 5 minutes
        getLogger().info("✓ Auto-save task started");
        
        // Particle visualization task
        if (configManager.areParticlesEnabled()) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                particleManager.showParticles();
            }, 20L, 10L); // Every 0.5 seconds
            getLogger().info("✓ Particle task started");
        }
    }
    
    public void reload() {
        getLogger().info("Reloading WDP-BaseDet...");
        reloadConfig();
        configManager.loadConfig();
        if (messageManager != null) {
            messageManager.reload();
        }
        getLogger().info("✓ Configuration reloaded");
    }
    
    // Getters
    public static WDPBaseDetPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MessageManager getMessages() {
        return messageManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public DetectionManager getDetectionManager() {
        return detectionManager;
    }
    
    public ScoreManager getScoreManager() {
        return scoreManager;
    }
    
    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }
    
    public TrustManager getTrustManager() {
        return trustManager;
    }
    
    public MenuManager getMenuManager() {
        return menuManager;
    }
    
    public ParticleManager getParticleManager() {
        return particleManager;
    }
    
    public EconomyIntegration getEconomyIntegration() {
        return economyIntegration;
    }
    
    public DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }
    
    public CMIIntegration getCmiIntegration() {
        return cmiIntegration;
    }
    
    public CombatManager getCombatManager() {
        return combatManager;
    }
    
    public ExpansionManager getExpansionManager() {
        return expansionManager;
    }
    
    public SelectorTool getSelectorTool() {
        return selectorTool;
    }

    public void debug(String message) {
        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
    
    public void debug(String message, Throwable t) {
        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().log(Level.INFO, "[DEBUG] " + message, t);
        }
    }
}
