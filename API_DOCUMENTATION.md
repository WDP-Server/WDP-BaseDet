# WDP-BaseDet API Documentation

## Overview

WDP-BaseDet provides a public API for external plugins to query base detection data. This is useful for plugins that need to:
- Avoid teleporting players near existing bases (RTP)
- Check if a location is within a protected base
- Get information about nearby bases

## Getting the Plugin Instance

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("WDP-BaseDet");
if (plugin != null && plugin.isEnabled()) {
    WDPBaseDetPlugin baseDet = (WDPBaseDetPlugin) plugin;
    DetectionManager detectionManager = baseDet.getDetectionManager();
    // Use API methods...
}
```

## API Methods

### DetectionManager API

#### `isLocationNearBase(World world, int x, int z, int minDistance)`
Check if a 2D location is within a specified distance of any detected base.

**Parameters:**
- `world` - The world to check in
- `x` - X coordinate
- `z` - Z coordinate  
- `minDistance` - Minimum distance from any base (in blocks)

**Returns:** `boolean` - true if location is within minDistance of any base

**Example:**
```java
boolean nearBase = detectionManager.isLocationNearBase(world, 100, 200, 200);
if (nearBase) {
    // Location is too close to a base, find another
}
```

---

#### `getAllBases()`
Get a list of all detected bases across all worlds.

**Returns:** `List<Base>` - All detected bases

**Example:**
```java
List<Base> allBases = detectionManager.getAllBases();
for (Base base : allBases) {
    System.out.println("Base owned by: " + base.getOwnerUUID());
}
```

---

#### `getBasesInWorld(String worldName)`
Get all bases in a specific world.

**Parameters:**
- `worldName` - The name of the world

**Returns:** `List<Base>` - Bases in the specified world

**Example:**
```java
List<Base> overworldBases = detectionManager.getBasesInWorld("world");
```

---

#### `getNearbyBases(Location location, int radius)`
Get all bases within a radius of a location.

**Parameters:**
- `location` - Center location
- `radius` - Search radius in blocks

**Returns:** `List<Base>` - Bases within the radius

**Example:**
```java
List<Base> nearby = detectionManager.getNearbyBases(player.getLocation(), 500);
if (!nearby.isEmpty()) {
    player.sendMessage("There are " + nearby.size() + " bases nearby!");
}
```

---

#### `getClosestBase(Location location)`
Get the closest base to a location.

**Parameters:**
- `location` - The location to check from

**Returns:** `Base` - The closest base, or null if no bases exist

**Example:**
```java
Base closest = detectionManager.getClosestBase(player.getLocation());
if (closest != null) {
    player.sendMessage("Nearest base is owned by " + 
        Bukkit.getOfflinePlayer(closest.getOwnerUUID()).getName());
}
```

---

#### `getDistanceToNearestBase(Location location)`
Get the distance to the nearest base from a location.

**Parameters:**
- `location` - The location to check from

**Returns:** `double` - Distance in blocks, or -1 if no bases exist

**Example:**
```java
double distance = detectionManager.getDistanceToNearestBase(player.getLocation());
if (distance > 0 && distance < 100) {
    player.sendMessage("You're " + Math.round(distance) + " blocks from the nearest base!");
}
```

## Base Model

The `Base` class provides information about a detected base:

### Properties
- `getId()` - Unique base ID
- `getOwnerUUID()` - UUID of the base owner
- `getWorldName()` - Name of the world
- `getWorld()` - Bukkit World object
- `getBounds()` - BoundingBox of the base
- `isConfirmed()` - Whether the base was manually confirmed
- `getCreatedAt()` - Creation timestamp
- `getUpdatedAt()` - Last update timestamp

### Methods
- `contains(Location)` - Check if a location is inside the base
- `getCenter()` - Get the center Location of the base

## BoundingBox Model

The `BoundingBox` class defines the 3D region of a base:

### Properties
- `getMinX()`, `getMaxX()` - X bounds
- `getMinY()`, `getMaxY()` - Y bounds
- `getMinZ()`, `getMaxZ()` - Z bounds
- `getWidth()` - X-axis size
- `getLength()` - Z-axis size
- `getHeight()` - Y-axis size

### Methods
- `contains(int x, int y, int z)` - Check if coordinates are inside
- `getVolume()` - Total block volume

## Usage Example: RTP Safety Check

```java
public Location findSafeRTPLocation(World world, int centerX, int centerZ, int radius) {
    Plugin plugin = Bukkit.getPluginManager().getPlugin("WDP-BaseDet");
    DetectionManager detManager = null;
    
    if (plugin != null && plugin.isEnabled()) {
        WDPBaseDetPlugin baseDet = (WDPBaseDetPlugin) plugin;
        detManager = baseDet.getDetectionManager();
    }
    
    Random random = new Random();
    int minDistanceFromBase = 200; // Blocks
    
    for (int attempt = 0; attempt < 50; attempt++) {
        double angle = random.nextDouble() * 2 * Math.PI;
        int distance = random.nextInt(radius);
        
        int x = centerX + (int)(Math.cos(angle) * distance);
        int z = centerZ + (int)(Math.sin(angle) * distance);
        
        // Check if near a base
        if (detManager != null && detManager.isLocationNearBase(world, x, z, minDistanceFromBase)) {
            continue; // Too close to a base, try again
        }
        
        // Found safe location
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }
    
    return null; // Could not find safe location
}
```

## Maven Dependency

To use WDP-BaseDet as a dependency in your plugin:

```xml
<dependency>
    <groupId>com.wdp</groupId>
    <artifactId>WDP-BaseDet</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

Add to your `plugin.yml`:
```yaml
softdepend: [WDP-BaseDet]
```

## Version History

### 1.0.0
- Initial API release
- Added `isLocationNearBase()` method
- Added `getAllBases()` method
- Added `getBasesInWorld()` method
- Added `getNearbyBases()` method
- Added `getClosestBase()` method
- Added `getDistanceToNearestBase()` method
