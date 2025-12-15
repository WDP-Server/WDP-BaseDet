package com.wdp.basedet.integration;

import com.wdp.basedet.WDPBaseDetPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Integration with Vault/SkillCoins economy
 */
public class EconomyIntegration {
    
    private final WDPBaseDetPlugin plugin;
    private Economy economy;
    private boolean enabled;
    
    public EconomyIntegration(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }
    
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - economy features disabled");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy provider found");
            return false;
        }
        
        economy = rsp.getProvider();
        enabled = economy != null;
        
        if (enabled) {
            plugin.getLogger().info("Economy provider: " + economy.getName());
        }
        
        return enabled;
    }
    
    /**
     * Deposit money to a player
     */
    public boolean deposit(Player player, double amount) {
        if (!enabled || economy == null) {
            return false;
        }
        
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
    
    /**
     * Withdraw money from a player
     */
    public boolean withdraw(Player player, double amount) {
        if (!enabled || economy == null) {
            return false;
        }
        
        if (!hasBalance(player, amount)) {
            return false;
        }
        
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    
    /**
     * Check if player has enough balance
     */
    public boolean hasBalance(Player player, double amount) {
        if (!enabled || economy == null) {
            return true;
        }
        
        return economy.has(player, amount);
    }
    
    /**
     * Get player balance
     */
    public double getBalance(Player player) {
        if (!enabled || economy == null) {
            return 0;
        }
        
        return economy.getBalance(player);
    }
    
    /**
     * Format currency amount
     */
    public String format(double amount) {
        if (!enabled || economy == null) {
            return String.format("%.2f", amount);
        }
        
        return economy.format(amount);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public Economy getEconomy() {
        return economy;
    }
}
