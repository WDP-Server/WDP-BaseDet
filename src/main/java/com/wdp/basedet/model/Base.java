package com.wdp.basedet.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Represents a detected/claimed base
 */
public class Base {
    
    private final long id;
    private final UUID ownerUUID;
    private final String worldName;
    private BoundingBox bounds;
    private boolean confirmed;
    private final long createdAt;
    private long updatedAt;
    
    public Base(long id, UUID ownerUUID, String worldName, BoundingBox bounds, 
                boolean confirmed, long createdAt, long updatedAt) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.worldName = worldName;
        this.bounds = bounds;
        this.confirmed = confirmed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    public long getId() {
        return id;
    }
    
    public UUID getOwnerUUID() {
        return ownerUUID;
    }
    
    public String getWorldName() {
        return worldName;
    }
    
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }
    
    public BoundingBox getBounds() {
        return bounds;
    }
    
    public void setBounds(BoundingBox bounds) {
        this.bounds = bounds;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Check if a location is within this base
     */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equals(worldName)) return false;
        return bounds.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    /**
     * Get the center location of this base
     */
    public Location getCenter() {
        World world = getWorld();
        if (world == null) return null;
        
        return new Location(
                world,
                (bounds.getMinX() + bounds.getMaxX()) / 2.0,
                (bounds.getMinY() + bounds.getMaxY()) / 2.0,
                (bounds.getMinZ() + bounds.getMaxZ()) / 2.0
        );
    }
    
    /**
     * Get the volume of this base in blocks
     */
    public int getVolume() {
        return bounds.getWidth() * bounds.getHeight() * bounds.getLength();
    }
    
    /**
     * Get a formatted string of the base dimensions
     */
    public String getDimensionsString() {
        return bounds.getWidth() + "x" + bounds.getLength() + "x" + bounds.getHeight();
    }
    
    /**
     * Get a formatted location string
     */
    public String getLocationString() {
        return worldName + " at " + bounds.getMinX() + ", " + bounds.getMinY() + ", " + bounds.getMinZ();
    }
    
    @Override
    public String toString() {
        return "Base{" +
                "id=" + id +
                ", owner=" + ownerUUID +
                ", world='" + worldName + '\'' +
                ", bounds=" + bounds +
                ", confirmed=" + confirmed +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Base base = (Base) o;
        return id == base.id;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
