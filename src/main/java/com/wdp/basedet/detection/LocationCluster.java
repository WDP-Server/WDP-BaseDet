package com.wdp.basedet.detection;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a cluster of player activity at a specific location.
 * Used for multi-location base tracking - players can have up to 5 active clusters.
 * 
 * Features:
 * - Separate score tracking per location
 * - Mining detection to avoid false positives
 * - Auto-decay after 4 hours of inactivity
 * - Never remove highest score cluster
 */
public class LocationCluster {
    
    private final UUID playerId;
    private final String world;
    private int centerX;
    private int centerY;
    private int centerZ;
    
    private double score;
    private long lastActivity;
    private long createdAt;
    
    // Mining detection metrics
    private int blocksBroken;
    private int blocksPlaced;
    private int oresBroken;
    private int belowY60Count;
    private int linearPatternCount;
    
    // Track recent positions for pattern detection
    private final List<int[]> recentBreakPositions = new ArrayList<>();
    
    // Classification
    private ClusterType type;
    
    public enum ClusterType {
        UNKNOWN,   // Not yet classified
        BASE,      // High confidence this is a base
        MINING,    // High confidence this is mining activity
        HYBRID     // Mixed activity
    }
    
    public LocationCluster(UUID playerId, String world, int x, int y, int z) {
        this.playerId = playerId;
        this.world = world;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.score = 0;
        this.lastActivity = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.blocksBroken = 0;
        this.blocksPlaced = 0;
        this.oresBroken = 0;
        this.belowY60Count = 0;
        this.linearPatternCount = 0;
        this.type = ClusterType.UNKNOWN;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public String getWorld() {
        return world;
    }
    
    public int getCenterX() {
        return centerX;
    }
    
    public int getCenterY() {
        return centerY;
    }
    
    public int getCenterZ() {
        return centerZ;
    }
    
    public void updateCenter(int x, int y, int z) {
        // Weighted average towards new position
        this.centerX = (int) (this.centerX * 0.7 + x * 0.3);
        this.centerY = (int) (this.centerY * 0.7 + y * 0.3);
        this.centerZ = (int) (this.centerZ * 0.7 + z * 0.3);
    }
    
    public double getScore() {
        return score;
    }
    
    public void setScore(double score) {
        this.score = Math.max(0, score);
    }
    
    public void addScore(double amount) {
        this.score = Math.max(0, this.score + amount);
        this.lastActivity = System.currentTimeMillis();
    }
    
    public long getLastActivity() {
        return lastActivity;
    }
    
    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public ClusterType getType() {
        return type;
    }
    
    public void setType(ClusterType type) {
        this.type = type;
    }
    
    // ==================== Mining Detection ====================
    
    public void recordBlockBreak(int x, int y, int z, String blockType) {
        blocksBroken++;
        
        // Track if below Y=60 (typical mining level)
        if (y < 60) {
            belowY60Count++;
        }
        
        // Track if it's an ore
        if (isOre(blockType)) {
            oresBroken++;
        }
        
        // Track position for pattern detection
        recentBreakPositions.add(new int[]{x, y, z});
        if (recentBreakPositions.size() > 50) {
            recentBreakPositions.remove(0);
        }
        
        // Check for linear (tunnel) patterns
        if (recentBreakPositions.size() >= 5) {
            if (detectLinearPattern()) {
                linearPatternCount++;
            }
        }
        
        reclassify();
    }
    
    public void recordBlockPlace(String blockType) {
        blocksPlaced++;
        reclassify();
    }
    
    private boolean isOre(String blockType) {
        return blockType != null && (
            blockType.contains("_ORE") ||
            blockType.equals("ANCIENT_DEBRIS") ||
            blockType.equals("NETHER_GOLD_ORE") ||
            blockType.equals("NETHER_QUARTZ_ORE")
        );
    }
    
    /**
     * Detect if recent breaks form a linear (tunnel) pattern
     */
    private boolean detectLinearPattern() {
        if (recentBreakPositions.size() < 5) return false;
        
        // Get last 10 positions
        int count = Math.min(10, recentBreakPositions.size());
        List<int[]> recent = recentBreakPositions.subList(
            recentBreakPositions.size() - count, 
            recentBreakPositions.size()
        );
        
        // Calculate variance in each axis
        double avgX = 0, avgY = 0, avgZ = 0;
        for (int[] pos : recent) {
            avgX += pos[0];
            avgY += pos[1];
            avgZ += pos[2];
        }
        avgX /= count;
        avgY /= count;
        avgZ /= count;
        
        double varX = 0, varY = 0, varZ = 0;
        for (int[] pos : recent) {
            varX += Math.pow(pos[0] - avgX, 2);
            varY += Math.pow(pos[1] - avgY, 2);
            varZ += Math.pow(pos[2] - avgZ, 2);
        }
        varX /= count;
        varY /= count;
        varZ /= count;
        
        // If one axis has high variance and others have low = linear pattern
        // (player is mining in a straight line)
        boolean xLinear = varX > 20 && varY < 5 && varZ < 5;
        boolean zLinear = varZ > 20 && varY < 5 && varX < 5;
        boolean yLinear = varY > 10 && varX < 5 && varZ < 5; // Vertical shaft
        
        return xLinear || zLinear || yLinear;
    }
    
    /**
     * Reclassify the cluster based on accumulated metrics
     */
    private void reclassify() {
        int totalBlocks = blocksBroken + blocksPlaced;
        if (totalBlocks < 10) {
            type = ClusterType.UNKNOWN;
            return;
        }
        
        // Calculate mining confidence
        double miningScore = 0;
        
        // High break-to-place ratio = mining
        if (blocksPlaced > 0) {
            double breakRatio = (double) blocksBroken / blocksPlaced;
            if (breakRatio > 5) miningScore += 30;
            else if (breakRatio > 2) miningScore += 15;
        } else if (blocksBroken > 10) {
            miningScore += 40; // Only breaking, no placing
        }
        
        // Ores broken = definitely mining
        double oreRatio = (double) oresBroken / Math.max(1, blocksBroken);
        if (oreRatio > 0.1) miningScore += 25;
        else if (oreRatio > 0.05) miningScore += 15;
        
        // Below Y=60 = likely mining
        double depthRatio = (double) belowY60Count / Math.max(1, blocksBroken);
        if (depthRatio > 0.7) miningScore += 20;
        else if (depthRatio > 0.4) miningScore += 10;
        
        // Linear patterns = tunnel mining
        if (linearPatternCount > 5) miningScore += 25;
        else if (linearPatternCount > 2) miningScore += 10;
        
        // Classify
        if (miningScore >= 60) {
            type = ClusterType.MINING;
        } else if (miningScore >= 30) {
            type = ClusterType.HYBRID;
        } else if (blocksPlaced >= 10 || hasBaseIndicators()) {
            type = ClusterType.BASE;
        } else {
            type = ClusterType.UNKNOWN;
        }
    }
    
    /**
     * Check if cluster has strong base indicators (beds, doors, chests)
     * This would need to be called by ScoreManager when these are placed
     */
    private boolean hasBaseIndicators() {
        // Will be set externally when bed/door/chest placed
        return false;
    }
    
    // Track base indicators
    private boolean hasBed = false;
    private boolean hasDoor = false;
    private boolean hasChest = false;
    private boolean hasCraftingStation = false;
    
    public void recordBaseIndicator(String type) {
        switch (type) {
            case "BED" -> hasBed = true;
            case "DOOR" -> hasDoor = true;
            case "CHEST" -> hasChest = true;
            case "CRAFTING" -> hasCraftingStation = true;
        }
        // If has base indicators, likely a base
        if (hasBed || (hasDoor && hasChest)) {
            this.type = ClusterType.BASE;
        }
    }
    
    public boolean hasBed() {
        return hasBed;
    }
    
    public boolean hasDoor() {
        return hasDoor;
    }
    
    public boolean hasChest() {
        return hasChest;
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Calculate distance from cluster center to a location
     */
    public double distanceTo(int x, int y, int z) {
        return Math.sqrt(
            Math.pow(centerX - x, 2) + 
            Math.pow(centerY - y, 2) + 
            Math.pow(centerZ - z, 2)
        );
    }
    
    /**
     * Calculate 2D horizontal distance
     */
    public double horizontalDistanceTo(int x, int z) {
        return Math.sqrt(
            Math.pow(centerX - x, 2) + 
            Math.pow(centerZ - z, 2)
        );
    }
    
    /**
     * Check if this cluster has expired (4 hours of inactivity)
     */
    public boolean isExpired(long currentTime, long expiryMs) {
        return (currentTime - lastActivity) > expiryMs;
    }
    
    /**
     * Check if score meets threshold for base detection
     */
    public boolean meetsThreshold(double threshold) {
        return score >= threshold;
    }
    
    /**
     * Get a formatted location string
     */
    public String getLocationString() {
        return world + " at " + centerX + ", " + centerY + ", " + centerZ;
    }
    
    public int getBlocksBroken() {
        return blocksBroken;
    }
    
    public int getBlocksPlaced() {
        return blocksPlaced;
    }
    
    public int getOresBroken() {
        return oresBroken;
    }
    
    @Override
    public String toString() {
        return "LocationCluster{" +
                "world='" + world + '\'' +
                ", center=(" + centerX + "," + centerY + "," + centerZ + ")" +
                ", score=" + String.format("%.2f", score) +
                ", type=" + type +
                ", broken=" + blocksBroken +
                ", placed=" + blocksPlaced +
                '}';
    }
}
