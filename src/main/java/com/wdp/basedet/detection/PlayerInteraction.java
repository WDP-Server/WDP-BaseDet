package com.wdp.basedet.detection;

import java.util.UUID;

/**
 * Represents a player interaction for base detection
 */
public class PlayerInteraction {
    
    private final UUID playerUUID;
    private final String world;
    private final int x, y, z;
    private final InteractionType type;
    private final String blockType;
    private final double score;
    private final long timestamp;
    
    public PlayerInteraction(UUID playerUUID, String world, int x, int y, int z,
                            InteractionType type, String blockType, double score, long timestamp) {
        this.playerUUID = playerUUID;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.blockType = blockType;
        this.score = score;
        this.timestamp = timestamp;
    }
    
    public UUID getPlayerUUID() { return playerUUID; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public InteractionType getType() { return type; }
    public String getBlockType() { return blockType; }
    public double getScore() { return score; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Calculate distance to another interaction
     */
    public double distanceTo(PlayerInteraction other) {
        if (!this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Check if this interaction is within range of another
     */
    public boolean isWithinRange(PlayerInteraction other, double range) {
        return distanceTo(other) <= range;
    }
    
    public enum InteractionType {
        BLOCK_PLACE,
        BLOCK_BREAK,
        CHEST_PLACE,
        WORKBENCH_PLACE,
        DOOR_PLACE,
        BED_PLACE,
        WALKING
    }
    
    @Override
    public String toString() {
        return "Interaction{" +
                "type=" + type +
                ", block=" + blockType +
                ", pos=(" + x + "," + y + "," + z + ")" +
                ", score=" + score +
                '}';
    }
}
