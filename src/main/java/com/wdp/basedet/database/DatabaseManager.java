package com.wdp.basedet.database;

import com.wdp.basedet.WDPBaseDetPlugin;
import com.wdp.basedet.detection.PlayerInteraction;
import com.wdp.basedet.model.Base;
import com.wdp.basedet.model.BoundingBox;
import com.wdp.basedet.model.TrustEntry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages database connections and operations for WDP-BaseDet
 * Supports both SQLite and MySQL
 */
public class DatabaseManager {
    
    private final WDPBaseDetPlugin plugin;
    private HikariDataSource dataSource;
    private boolean usingSQLite;
    
    public DatabaseManager(WDPBaseDetPlugin plugin) {
        this.plugin = plugin;
    }
    
    public boolean initialize() {
        String type = plugin.getConfigManager().getDatabaseType();
        usingSQLite = type.equalsIgnoreCase("sqlite");
        
        try {
            if (usingSQLite) {
                setupSQLite();
            } else {
                setupMySQL();
            }
            
            createTables();
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }
    
    private void setupSQLite() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        File dbFile = new File(dataFolder, "basedet.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setPoolName("WDP-BaseDet-SQLite");
        config.setMaximumPoolSize(1); // SQLite only supports one connection
        config.setConnectionTestQuery("SELECT 1");
        
        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("SQLite database initialized: " + dbFile.getAbsolutePath());
    }
    
    private void setupMySQL() {
        var configManager = plugin.getConfigManager();
        
        String host = configManager.getMySQLHost();
        int port = configManager.getMySQLPort();
        String database = configManager.getMySQLDatabase();
        String username = configManager.getMySQLUsername();
        String password = configManager.getMySQLPassword();
        
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("WDP-BaseDet-MySQL");
        config.setMaximumPoolSize(configManager.getPoolMaxSize());
        config.setMinimumIdle(configManager.getPoolMinIdle());
        config.setConnectionTimeout(configManager.getPoolConnectionTimeout());
        config.setIdleTimeout(configManager.getPoolIdleTimeout());
        config.setMaxLifetime(configManager.getPoolMaxLifetime());
        
        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("MySQL database initialized: " + host + ":" + port + "/" + database);
    }
    
    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            // Player scores table
            String createScores = usingSQLite ?
                    "CREATE TABLE IF NOT EXISTS player_scores (" +
                    "uuid TEXT PRIMARY KEY," +
                    "score REAL DEFAULT 0," +
                    "last_update INTEGER," +
                    "last_online INTEGER" +
                    ")" :
                    "CREATE TABLE IF NOT EXISTS player_scores (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "score DOUBLE DEFAULT 0," +
                    "last_update BIGINT," +
                    "last_online BIGINT" +
                    ")";
            
            // Interactions table
            String createInteractions = usingSQLite ?
                    "CREATE TABLE IF NOT EXISTS interactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "type TEXT NOT NULL," +
                    "block_type TEXT," +
                    "score REAL," +
                    "timestamp INTEGER" +
                    ")" :
                    "CREATE TABLE IF NOT EXISTS interactions (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "world VARCHAR(64) NOT NULL," +
                    "x INT NOT NULL," +
                    "y INT NOT NULL," +
                    "z INT NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "block_type VARCHAR(64)," +
                    "score DOUBLE," +
                    "timestamp BIGINT," +
                    "INDEX idx_uuid (uuid)," +
                    "INDEX idx_location (world, x, y, z)" +
                    ")";
            
            // Bases table
            String createBases = usingSQLite ?
                    "CREATE TABLE IF NOT EXISTS bases (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "min_x INTEGER NOT NULL," +
                    "min_y INTEGER NOT NULL," +
                    "min_z INTEGER NOT NULL," +
                    "max_x INTEGER NOT NULL," +
                    "max_y INTEGER NOT NULL," +
                    "max_z INTEGER NOT NULL," +
                    "confirmed INTEGER DEFAULT 0," +
                    "created_at INTEGER," +
                    "updated_at INTEGER" +
                    ")" :
                    "CREATE TABLE IF NOT EXISTS bases (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "world VARCHAR(64) NOT NULL," +
                    "min_x INT NOT NULL," +
                    "min_y INT NOT NULL," +
                    "min_z INT NOT NULL," +
                    "max_x INT NOT NULL," +
                    "max_y INT NOT NULL," +
                    "max_z INT NOT NULL," +
                    "confirmed TINYINT DEFAULT 0," +
                    "created_at BIGINT," +
                    "updated_at BIGINT," +
                    "INDEX idx_owner (owner_uuid)," +
                    "INDEX idx_world (world)" +
                    ")";
            
            // Trust table
            String createTrust = usingSQLite ?
                    "CREATE TABLE IF NOT EXISTS trust (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "base_id INTEGER NOT NULL," +
                    "trusted_uuid TEXT NOT NULL," +
                    "break_online INTEGER DEFAULT 1," +
                    "place_online INTEGER DEFAULT 1," +
                    "container_online INTEGER DEFAULT 1," +
                    "door_online INTEGER DEFAULT 1," +
                    "redstone_online INTEGER DEFAULT 1," +
                    "entity_damage_online INTEGER DEFAULT 1," +
                    "vehicle_online INTEGER DEFAULT 1," +
                    "decoration_online INTEGER DEFAULT 1," +
                    "break_offline INTEGER DEFAULT 0," +
                    "place_offline INTEGER DEFAULT 0," +
                    "container_offline INTEGER DEFAULT 1," +
                    "door_offline INTEGER DEFAULT 1," +
                    "redstone_offline INTEGER DEFAULT 1," +
                    "entity_damage_offline INTEGER DEFAULT 0," +
                    "vehicle_offline INTEGER DEFAULT 0," +
                    "decoration_offline INTEGER DEFAULT 0," +
                    "created_at INTEGER," +
                    "FOREIGN KEY (base_id) REFERENCES bases(id) ON DELETE CASCADE," +
                    "UNIQUE(base_id, trusted_uuid)" +
                    ")" :
                    "CREATE TABLE IF NOT EXISTS trust (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "base_id BIGINT NOT NULL," +
                    "trusted_uuid VARCHAR(36) NOT NULL," +
                    "break_online TINYINT DEFAULT 1," +
                    "place_online TINYINT DEFAULT 1," +
                    "container_online TINYINT DEFAULT 1," +
                    "door_online TINYINT DEFAULT 1," +
                    "redstone_online TINYINT DEFAULT 1," +
                    "entity_damage_online TINYINT DEFAULT 1," +
                    "vehicle_online TINYINT DEFAULT 1," +
                    "decoration_online TINYINT DEFAULT 1," +
                    "break_offline TINYINT DEFAULT 0," +
                    "place_offline TINYINT DEFAULT 0," +
                    "container_offline TINYINT DEFAULT 1," +
                    "door_offline TINYINT DEFAULT 1," +
                    "redstone_offline TINYINT DEFAULT 1," +
                    "entity_damage_offline TINYINT DEFAULT 0," +
                    "vehicle_offline TINYINT DEFAULT 0," +
                    "decoration_offline TINYINT DEFAULT 0," +
                    "created_at BIGINT," +
                    "FOREIGN KEY (base_id) REFERENCES bases(id) ON DELETE CASCADE," +
                    "UNIQUE KEY unique_trust (base_id, trusted_uuid)," +
                    "INDEX idx_trusted (trusted_uuid)" +
                    ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createScores);
                stmt.execute(createInteractions);
                stmt.execute(createBases);
                stmt.execute(createTrust);
            }
            
            // Migrate old trust table schema to new one
            migrateTrustTable(conn);
            
            plugin.getLogger().info("Database tables created/verified");
        }
    }
    
    private void migrateTrustTable(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check if old columns exist and add new columns if needed
            String[] newColumns = {
                "container_online", "door_online", "redstone_online", 
                "entity_damage_online", "vehicle_online", "decoration_online",
                "container_offline", "door_offline", "redstone_offline",
                "entity_damage_offline", "vehicle_offline", "decoration_offline"
            };
            
            for (String column : newColumns) {
                try {
                    String type = usingSQLite ? "INTEGER DEFAULT 1" : "TINYINT DEFAULT 1";
                    if (column.contains("entity_damage_offline") || column.contains("vehicle_offline") || column.contains("decoration_offline")) {
                        type = usingSQLite ? "INTEGER DEFAULT 0" : "TINYINT DEFAULT 0";
                    }
                    stmt.execute("ALTER TABLE trust ADD COLUMN " + column + " " + type);
                    plugin.getLogger().info("Added column " + column + " to trust table");
                } catch (SQLException e) {
                    // Column already exists, skip
                }
            }
            
            // Migrate old chest_* and interact_* columns to new container_* and door_*
            try {
                stmt.execute("UPDATE trust SET container_online = chest_online, door_online = interact_online WHERE container_online IS NULL");
                stmt.execute("UPDATE trust SET container_offline = chest_offline, door_offline = interact_offline WHERE container_offline IS NULL");
                plugin.getLogger().info("Migrated old permission columns to new schema");
            } catch (SQLException e) {
                // Already migrated or columns don't exist
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error during trust table migration", e);
        }
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }
    
    // ==================== SCORE OPERATIONS ====================
    
    public double getPlayerScore(UUID uuid) {
        String sql = "SELECT score FROM player_scores WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("score");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get player score", e);
        }
        return 0;
    }
    
    public void setPlayerScore(UUID uuid, double score) {
        String sql = usingSQLite ?
                "INSERT OR REPLACE INTO player_scores (uuid, score, last_update, last_online) VALUES (?, ?, ?, ?)" :
                "INSERT INTO player_scores (uuid, score, last_update, last_online) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE score = VALUES(score), last_update = VALUES(last_update), last_online = VALUES(last_online)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, score);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to set player score", e);
        }
    }
    
    public void updateLastOnline(UUID uuid) {
        String sql = "UPDATE player_scores SET last_online = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update last online", e);
        }
    }
    
    public Map<UUID, Double> getAllOnlineScores() {
        Map<UUID, Double> scores = new HashMap<>();
        String sql = "SELECT uuid, score FROM player_scores";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                // Only include online players
                if (Bukkit.getPlayer(uuid) != null) {
                    scores.put(uuid, rs.getDouble("score"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get all scores", e);
        }
        return scores;
    }
    
    // ==================== INTERACTION OPERATIONS ====================
    
    public void saveInteraction(PlayerInteraction interaction) {
        String sql = "INSERT INTO interactions (uuid, world, x, y, z, type, block_type, score, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, interaction.getPlayerUUID().toString());
            stmt.setString(2, interaction.getWorld());
            stmt.setInt(3, interaction.getX());
            stmt.setInt(4, interaction.getY());
            stmt.setInt(5, interaction.getZ());
            stmt.setString(6, interaction.getType().name());
            stmt.setString(7, interaction.getBlockType());
            stmt.setDouble(8, interaction.getScore());
            stmt.setLong(9, interaction.getTimestamp());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save interaction", e);
        }
    }
    
    public List<PlayerInteraction> getPlayerInteractions(UUID uuid) {
        List<PlayerInteraction> interactions = new ArrayList<>();
        String sql = "SELECT * FROM interactions WHERE uuid = ? ORDER BY timestamp DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                interactions.add(interactionFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get player interactions", e);
        }
        return interactions;
    }
    
    public List<PlayerInteraction> getRecentInteractions(UUID uuid, long since) {
        List<PlayerInteraction> interactions = new ArrayList<>();
        String sql = "SELECT * FROM interactions WHERE uuid = ? AND timestamp > ? ORDER BY timestamp DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, since);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                interactions.add(interactionFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get recent interactions", e);
        }
        return interactions;
    }
    
    public void clearOldInteractions(UUID uuid, long before) {
        String sql = "DELETE FROM interactions WHERE uuid = ? AND timestamp < ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, before);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to clear old interactions", e);
        }
    }
    
    private PlayerInteraction interactionFromResultSet(ResultSet rs) throws SQLException {
        return new PlayerInteraction(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                PlayerInteraction.InteractionType.valueOf(rs.getString("type")),
                rs.getString("block_type"),
                rs.getDouble("score"),
                rs.getLong("timestamp")
        );
    }
    
    // ==================== BASE OPERATIONS ====================
    
    public Base createBase(UUID ownerUUID, String world, BoundingBox bounds) {
        String sql = "INSERT INTO bases (owner_uuid, world, min_x, min_y, min_z, max_x, max_y, max_z, confirmed, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, ownerUUID.toString());
            stmt.setString(2, world);
            stmt.setInt(3, bounds.getMinX());
            stmt.setInt(4, bounds.getMinY());
            stmt.setInt(5, bounds.getMinZ());
            stmt.setInt(6, bounds.getMaxX());
            stmt.setInt(7, bounds.getMaxY());
            stmt.setInt(8, bounds.getMaxZ());
            stmt.setLong(9, now);
            stmt.setLong(10, now);
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                long id = keys.getLong(1);
                return new Base(id, ownerUUID, world, bounds, false, now, now);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create base", e);
        }
        return null;
    }
    
    public Base getBase(long id) {
        String sql = "SELECT * FROM bases WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return baseFromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get base", e);
        }
        return null;
    }
    
    public List<Base> getPlayerBases(UUID ownerUUID) {
        List<Base> bases = new ArrayList<>();
        String sql = "SELECT * FROM bases WHERE owner_uuid = ? AND confirmed = 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                bases.add(baseFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get player bases", e);
        }
        return bases;
    }
    
    public Base getPendingBase(UUID ownerUUID) {
        String sql = "SELECT * FROM bases WHERE owner_uuid = ? AND confirmed = 0 ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return baseFromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get pending base", e);
        }
        return null;
    }
    
    public void confirmBase(long baseId) {
        String sql = "UPDATE bases SET confirmed = 1, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setLong(2, baseId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to confirm base", e);
        }
    }
    
    public void deleteBase(long baseId) {
        String sql = "DELETE FROM bases WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, baseId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete base", e);
        }
    }
    
    public void updateBaseBounds(long baseId, BoundingBox bounds) {
        String sql = "UPDATE bases SET min_x = ?, min_y = ?, min_z = ?, max_x = ?, max_y = ?, max_z = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, bounds.getMinX());
            stmt.setInt(2, bounds.getMinY());
            stmt.setInt(3, bounds.getMinZ());
            stmt.setInt(4, bounds.getMaxX());
            stmt.setInt(5, bounds.getMaxY());
            stmt.setInt(6, bounds.getMaxZ());
            stmt.setLong(7, System.currentTimeMillis());
            stmt.setLong(8, baseId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update base bounds", e);
        }
    }
    
    public Base getBaseAtLocation(Location location) {
        String sql = "SELECT * FROM bases WHERE world = ? AND confirmed = 1 " +
                "AND min_x <= ? AND max_x >= ? " +
                "AND min_y <= ? AND max_y >= ? " +
                "AND min_z <= ? AND max_z >= ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, location.getWorld().getName());
            stmt.setInt(2, location.getBlockX());
            stmt.setInt(3, location.getBlockX());
            stmt.setInt(4, location.getBlockY());
            stmt.setInt(5, location.getBlockY());
            stmt.setInt(6, location.getBlockZ());
            stmt.setInt(7, location.getBlockZ());
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return baseFromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get base at location", e);
        }
        return null;
    }
    
    public List<Base> getAllConfirmedBases() {
        List<Base> bases = new ArrayList<>();
        String sql = "SELECT * FROM bases WHERE confirmed = 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bases.add(baseFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get all bases", e);
        }
        return bases;
    }
    
    /**
     * Get all bases (confirmed and pending)
     * 
     * @return List of all bases
     */
    public List<Base> getAllBases() {
        List<Base> bases = new ArrayList<>();
        String sql = "SELECT * FROM bases";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bases.add(baseFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get all bases", e);
        }
        return bases;
    }
    
    /**
     * Get all bases in a specific world (confirmed and pending)
     * 
     * @param worldName The world name
     * @return List of bases in that world
     */
    public List<Base> getAllBases(String worldName) {
        List<Base> bases = new ArrayList<>();
        String sql = "SELECT * FROM bases WHERE world = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, worldName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bases.add(baseFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get bases in world " + worldName, e);
        }
        return bases;
    }
    
    private Base baseFromResultSet(ResultSet rs) throws SQLException {
        BoundingBox bounds = new BoundingBox(
                rs.getInt("min_x"),
                rs.getInt("min_y"),
                rs.getInt("min_z"),
                rs.getInt("max_x"),
                rs.getInt("max_y"),
                rs.getInt("max_z")
        );
        
        return new Base(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("world"),
                bounds,
                rs.getInt("confirmed") == 1,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
    
    // ==================== TRUST OPERATIONS ====================
    
    public void addTrust(long baseId, UUID trustedUUID) {
        var config = plugin.getConfigManager();
        
        String sql = usingSQLite ?
                "INSERT OR REPLACE INTO trust (base_id, trusted_uuid, " +
                "break_online, place_online, container_online, door_online, redstone_online, entity_damage_online, vehicle_online, decoration_online, " +
                "break_offline, place_offline, container_offline, door_offline, redstone_offline, entity_damage_offline, vehicle_offline, decoration_offline, " +
                "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" :
                "INSERT INTO trust (base_id, trusted_uuid, " +
                "break_online, place_online, container_online, door_online, redstone_online, entity_damage_online, vehicle_online, decoration_online, " +
                "break_offline, place_offline, container_offline, door_offline, redstone_offline, entity_damage_offline, vehicle_offline, decoration_offline, " +
                "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE break_online = VALUES(break_online), place_online = VALUES(place_online)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, baseId);
            stmt.setString(2, trustedUUID.toString());
            stmt.setInt(3, config.getTrustDefaultOnline("block-break") ? 1 : 0);
            stmt.setInt(4, config.getTrustDefaultOnline("block-place") ? 1 : 0);
            stmt.setInt(5, config.getTrustDefaultOnline("chest-access") ? 1 : 0);
            stmt.setInt(6, config.getTrustDefaultOnline("door-interact") ? 1 : 0);
            stmt.setInt(7, config.getTrustDefaultOnline("button-interact") ? 1 : 0);
            stmt.setInt(8, config.getTrustDefaultOnline("entity-damage") ? 1 : 0);
            stmt.setInt(9, 1); // vehicle_online - default true
            stmt.setInt(10, 1); // decoration_online - default true
            stmt.setInt(11, config.getTrustDefaultOffline("block-break") ? 1 : 0);
            stmt.setInt(12, config.getTrustDefaultOffline("block-place") ? 1 : 0);
            stmt.setInt(13, config.getTrustDefaultOffline("chest-access") ? 1 : 0);
            stmt.setInt(14, config.getTrustDefaultOffline("door-interact") ? 1 : 0);
            stmt.setInt(15, config.getTrustDefaultOffline("button-interact") ? 1 : 0);
            stmt.setInt(16, config.getTrustDefaultOffline("entity-damage") ? 1 : 0);
            stmt.setInt(17, 0); // vehicle_offline - default false
            stmt.setInt(18, 0); // decoration_offline - default false
            stmt.setLong(19, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add trust", e);
        }
    }
    
    public void removeTrust(long baseId, UUID trustedUUID) {
        String sql = "DELETE FROM trust WHERE base_id = ? AND trusted_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, baseId);
            stmt.setString(2, trustedUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove trust", e);
        }
    }
    
    public TrustEntry getTrust(long baseId, UUID trustedUUID) {
        String sql = "SELECT * FROM trust WHERE base_id = ? AND trusted_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, baseId);
            stmt.setString(2, trustedUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return trustFromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get trust", e);
        }
        return null;
    }
    
    public List<TrustEntry> getBaseTrusted(long baseId) {
        List<TrustEntry> trusted = new ArrayList<>();
        String sql = "SELECT * FROM trust WHERE base_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, baseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                trusted.add(trustFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get base trusted", e);
        }
        return trusted;
    }
    
    public void updateTrustPermission(long trustId, String permission, boolean value) {
        String sql = "UPDATE trust SET " + permission + " = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, value ? 1 : 0);
            stmt.setLong(2, trustId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update trust permission", e);
        }
    }
    
    private TrustEntry trustFromResultSet(ResultSet rs) throws SQLException {
        // Get old columns with fallback for backward compatibility
        boolean containerOnline = getColumnOrDefault(rs, "container_online", "chest_online");
        boolean doorOnline = getColumnOrDefault(rs, "door_online", "interact_online");
        boolean redstoneOnline = getColumnOrDefault(rs, "redstone_online", "interact_online");
        boolean entityDamageOnline = getColumnOrDefault(rs, "entity_damage_online", "interact_online");
        boolean vehicleOnline = getColumnOrDefault(rs, "vehicle_online", "interact_online");
        boolean decorationOnline = getColumnOrDefault(rs, "decoration_online", "interact_online");
        
        boolean containerOffline = getColumnOrDefault(rs, "container_offline", "chest_offline");
        boolean doorOffline = getColumnOrDefault(rs, "door_offline", "interact_offline");
        boolean redstoneOffline = getColumnOrDefault(rs, "redstone_offline", "interact_offline");
        boolean entityDamageOffline = getColumnOrDefault(rs, "entity_damage_offline", "interact_offline");
        boolean vehicleOffline = getColumnOrDefault(rs, "vehicle_offline", "interact_offline");
        boolean decorationOffline = getColumnOrDefault(rs, "decoration_offline", "interact_offline");
        
        return new TrustEntry(
                rs.getLong("id"),
                rs.getLong("base_id"),
                UUID.fromString(rs.getString("trusted_uuid")),
                rs.getInt("break_online") == 1,
                rs.getInt("place_online") == 1,
                containerOnline,
                doorOnline,
                redstoneOnline,
                entityDamageOnline,
                vehicleOnline,
                decorationOnline,
                rs.getInt("break_offline") == 1,
                rs.getInt("place_offline") == 1,
                containerOffline,
                doorOffline,
                redstoneOffline,
                entityDamageOffline,
                vehicleOffline,
                decorationOffline,
                rs.getLong("created_at")
        );
    }
    
    private boolean getColumnOrDefault(ResultSet rs, String columnName, String fallbackColumn) throws SQLException {
        try {
            return rs.getInt(columnName) == 1;
        } catch (SQLException e) {
            // Column doesn't exist, try fallback
            try {
                return rs.getInt(fallbackColumn) == 1;
            } catch (SQLException e2) {
                // Fallback also doesn't exist, return default
                return columnName.contains("offline") && 
                       (columnName.contains("entity") || columnName.contains("vehicle") || columnName.contains("decoration")) ? false : true;
            }
        }
    }
}
