package com.wdp.basedet.model;

/**
 * Represents an axis-aligned bounding box for base boundaries
 */
public class BoundingBox {
    
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    
    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Ensure min is actually min and max is actually max
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }
    
    /**
     * Create a bounding box from a center point and radius
     */
    public static BoundingBox fromCenter(int centerX, int centerY, int centerZ, 
                                         int radiusX, int radiusY, int radiusZ) {
        return new BoundingBox(
                centerX - radiusX, centerY - radiusY, centerZ - radiusZ,
                centerX + radiusX, centerY + radiusY, centerZ + radiusZ
        );
    }
    
    /**
     * Check if a point is within this bounding box
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Check if another bounding box overlaps with this one
     */
    public boolean intersects(BoundingBox other) {
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY &&
               this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }
    
    /**
     * Expand this bounding box to include a point
     */
    public void expand(int x, int y, int z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }
    
    /**
     * Expand this bounding box by an offset in all directions
     */
    public void expandBy(int horizontalOffset, int verticalOffset) {
        minX -= horizontalOffset;
        minZ -= horizontalOffset;
        maxX += horizontalOffset;
        maxZ += horizontalOffset;
        minY -= verticalOffset;
        maxY += verticalOffset;
    }
    
    /**
     * Create a copy of this bounding box expanded by offsets
     */
    public BoundingBox expandedBy(int horizontalOffset, int verticalOffset) {
        return new BoundingBox(
                minX - horizontalOffset,
                minY - verticalOffset,
                minZ - horizontalOffset,
                maxX + horizontalOffset,
                maxY + verticalOffset,
                maxZ + horizontalOffset
        );
    }
    
    /**
     * Clamp the bounding box to maximum dimensions
     */
    public void clampToMax(int maxWidth, int maxLength, int maxHeight) {
        int width = getWidth();
        int length = getLength();
        int height = getHeight();
        
        if (width > maxWidth) {
            int excess = width - maxWidth;
            minX += excess / 2;
            maxX -= (excess + 1) / 2;
        }
        
        if (length > maxLength) {
            int excess = length - maxLength;
            minZ += excess / 2;
            maxZ -= (excess + 1) / 2;
        }
        
        if (height > maxHeight) {
            int excess = height - maxHeight;
            minY += excess / 2;
            maxY -= (excess + 1) / 2;
        }
    }
    
    /**
     * Ensure the bounding box meets minimum dimensions
     */
    public void ensureMinimum(int minWidth, int minLength, int minHeight) {
        int width = getWidth();
        int length = getLength();
        int height = getHeight();
        
        if (width < minWidth) {
            int needed = minWidth - width;
            minX -= needed / 2;
            maxX += (needed + 1) / 2;
        }
        
        if (length < minLength) {
            int needed = minLength - length;
            minZ -= needed / 2;
            maxZ += (needed + 1) / 2;
        }
        
        if (height < minHeight) {
            int needed = minHeight - height;
            minY -= needed / 2;
            maxY += (needed + 1) / 2;
        }
    }
    
    /**
     * Get the distance from a point to the nearest edge of this box
     */
    public double getDistanceToEdge(int x, int y, int z) {
        double dx = Math.max(Math.max(minX - x, x - maxX), 0);
        double dy = Math.max(Math.max(minY - y, y - maxY), 0);
        double dz = Math.max(Math.max(minZ - z, z - maxZ), 0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    // Getters
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    
    public int getWidth() { return maxX - minX + 1; }
    public int getHeight() { return maxY - minY + 1; }
    public int getLength() { return maxZ - minZ + 1; }
    
    public int getCenterX() { return (minX + maxX) / 2; }
    public int getCenterY() { return (minY + maxY) / 2; }
    public int getCenterZ() { return (minZ + maxZ) / 2; }
    
    // Setters
    public void setMinX(int minX) { this.minX = minX; }
    public void setMinY(int minY) { this.minY = minY; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }
    
    @Override
    public String toString() {
        return "BoundingBox{" +
                "min=(" + minX + "," + minY + "," + minZ + "), " +
                "max=(" + maxX + "," + maxY + "," + maxZ + "), " +
                "size=" + getWidth() + "x" + getLength() + "x" + getHeight() +
                '}';
    }
    
    /**
     * Create a copy of this bounding box
     */
    public BoundingBox copy() {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
