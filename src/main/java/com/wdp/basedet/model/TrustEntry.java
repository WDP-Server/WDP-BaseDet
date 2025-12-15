package com.wdp.basedet.model;

import java.util.UUID;

/**
 * Represents a trust entry for a base
 */
public class TrustEntry {
    
    private final long id;
    private final long baseId;
    private final UUID trustedUUID;
    
    // Online permissions (when owner is online)
    private boolean breakOnline;
    private boolean placeOnline;
    private boolean containerOnline;
    private boolean doorOnline;
    private boolean redstoneOnline;
    private boolean entityDamageOnline;
    private boolean vehicleOnline;
    private boolean decorationOnline;
    
    // Offline permissions (when owner is offline)
    private boolean breakOffline;
    private boolean placeOffline;
    private boolean containerOffline;
    private boolean doorOffline;
    private boolean redstoneOffline;
    private boolean entityDamageOffline;
    private boolean vehicleOffline;
    private boolean decorationOffline;
    
    private final long createdAt;
    
    public TrustEntry(long id, long baseId, UUID trustedUUID,
                      boolean breakOnline, boolean placeOnline, boolean containerOnline, boolean doorOnline,
                      boolean redstoneOnline, boolean entityDamageOnline, boolean vehicleOnline, boolean decorationOnline,
                      boolean breakOffline, boolean placeOffline, boolean containerOffline, boolean doorOffline,
                      boolean redstoneOffline, boolean entityDamageOffline, boolean vehicleOffline, boolean decorationOffline,
                      long createdAt) {
        this.id = id;
        this.baseId = baseId;
        this.trustedUUID = trustedUUID;
        this.breakOnline = breakOnline;
        this.placeOnline = placeOnline;
        this.containerOnline = containerOnline;
        this.doorOnline = doorOnline;
        this.redstoneOnline = redstoneOnline;
        this.entityDamageOnline = entityDamageOnline;
        this.vehicleOnline = vehicleOnline;
        this.decorationOnline = decorationOnline;
        this.breakOffline = breakOffline;
        this.placeOffline = placeOffline;
        this.containerOffline = containerOffline;
        this.doorOffline = doorOffline;
        this.redstoneOffline = redstoneOffline;
        this.entityDamageOffline = entityDamageOffline;
        this.vehicleOffline = vehicleOffline;
        this.decorationOffline = decorationOffline;
        this.createdAt = createdAt;
    }
    
    // Getters
    public long getId() { return id; }
    public long getBaseId() { return baseId; }
    public UUID getTrustedUUID() { return trustedUUID; }
    
    public boolean canBreakOnline() { return breakOnline; }
    public boolean canPlaceOnline() { return placeOnline; }
    public boolean canContainerOnline() { return containerOnline; }
    public boolean canDoorOnline() { return doorOnline; }
    public boolean canRedstoneOnline() { return redstoneOnline; }
    public boolean canEntityDamageOnline() { return entityDamageOnline; }
    public boolean canVehicleOnline() { return vehicleOnline; }
    public boolean canDecorationOnline() { return decorationOnline; }
    
    public boolean canBreakOffline() { return breakOffline; }
    public boolean canPlaceOffline() { return placeOffline; }
    public boolean canContainerOffline() { return containerOffline; }
    public boolean canDoorOffline() { return doorOffline; }
    public boolean canRedstoneOffline() { return redstoneOffline; }
    public boolean canEntityDamageOffline() { return entityDamageOffline; }
    public boolean canVehicleOffline() { return vehicleOffline; }
    public boolean canDecorationOffline() { return decorationOffline; }
    
    public long getCreatedAt() { return createdAt; }
    
    // Setters
    public void setBreakOnline(boolean value) { this.breakOnline = value; }
    public void setPlaceOnline(boolean value) { this.placeOnline = value; }
    public void setContainerOnline(boolean value) { this.containerOnline = value; }
    public void setDoorOnline(boolean value) { this.doorOnline = value; }
    public void setRedstoneOnline(boolean value) { this.redstoneOnline = value; }
    public void setEntityDamageOnline(boolean value) { this.entityDamageOnline = value; }
    public void setVehicleOnline(boolean value) { this.vehicleOnline = value; }
    public void setDecorationOnline(boolean value) { this.decorationOnline = value; }
    
    public void setBreakOffline(boolean value) { this.breakOffline = value; }
    public void setPlaceOffline(boolean value) { this.placeOffline = value; }
    public void setContainerOffline(boolean value) { this.containerOffline = value; }
    public void setDoorOffline(boolean value) { this.doorOffline = value; }
    public void setRedstoneOffline(boolean value) { this.redstoneOffline = value; }
    public void setEntityDamageOffline(boolean value) { this.entityDamageOffline = value; }
    public void setVehicleOffline(boolean value) { this.vehicleOffline = value; }
    public void setDecorationOffline(boolean value) { this.decorationOffline = value; }
    
    /**
     * Check if this trust entry allows a specific action
     * @param action The action to check
     * @param ownerOnline Whether the base owner is online
     */
    public boolean canDoAction(String action, boolean ownerOnline) {
        if (ownerOnline) {
            return switch (action.toLowerCase()) {
                case "break" -> breakOnline;
                case "place" -> placeOnline;
                case "container", "chest" -> containerOnline;
                case "door" -> doorOnline;
                case "redstone" -> redstoneOnline;
                case "entity_damage" -> entityDamageOnline;
                case "vehicle" -> vehicleOnline;
                case "decoration" -> decorationOnline;
                default -> false;
            };
        } else {
            return switch (action.toLowerCase()) {
                case "break" -> breakOffline;
                case "place" -> placeOffline;
                case "container", "chest" -> containerOffline;
                case "door" -> doorOffline;
                case "redstone" -> redstoneOffline;
                case "entity_damage" -> entityDamageOffline;
                case "vehicle" -> vehicleOffline;
                case "decoration" -> decorationOffline;
                default -> false;
            };
        }
    }
    
    /**
     * Get permission column name for database
     */
    public static String getPermissionColumn(String action, boolean online) {
        String suffix = online ? "_online" : "_offline";
        return switch (action.toLowerCase()) {
            case "break" -> "break" + suffix;
            case "place" -> "place" + suffix;
            case "container" -> "container" + suffix;
            case "door" -> "door" + suffix;
            case "redstone" -> "redstone" + suffix;
            case "entity_damage" -> "entity_damage" + suffix;
            case "vehicle" -> "vehicle" + suffix;
            case "decoration" -> "decoration" + suffix;
            default -> null;
        };
    }
    
    @Override
    public String toString() {
        return "TrustEntry{" +
                "trustedUUID=" + trustedUUID +
                ", online=(break=" + breakOnline + ", place=" + placeOnline + 
                ", container=" + containerOnline + ", door=" + doorOnline +
                ", redstone=" + redstoneOnline + ", entity=" + entityDamageOnline +
                ", vehicle=" + vehicleOnline + ", decoration=" + decorationOnline + ")" +
                ", offline=(break=" + breakOffline + ", place=" + placeOffline + 
                ", container=" + containerOffline + ", door=" + doorOffline +
                ", redstone=" + redstoneOffline + ", entity=" + entityDamageOffline +
                ", vehicle=" + vehicleOffline + ", decoration=" + decorationOffline + ")" +
                '}';
    }
}
