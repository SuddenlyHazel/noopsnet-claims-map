// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

/** Flan expects a moved corner, not a pair of bounding corners. */
public final class ClaimResize {
    private ClaimResize() {}
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public long area() { return ((long)maxX - minX + 1) * ((long)maxZ - minZ + 1); }
    }
    public record Resize(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, Bounds bounds) {}
    public static Resize expand(Bounds b, String direction, int amount) {
        if (amount < 1 || amount > 4096) throw new IllegalArgumentException("Invalid expansion distance");
        int x = b.minX, y = b.minY, z = b.minZ;
        int dx = 0, dy = 0, dz = 0;
        switch (direction) {
            case "west" -> dx = -amount;
            case "east" -> { x = b.maxX; dx = amount; }
            case "north" -> dz = -amount;
            case "south" -> { z = b.maxZ; dz = amount; }
            case "down" -> dy = -amount;
            case "up" -> { y = b.maxY; dy = amount; }
            default -> throw new IllegalArgumentException("Invalid direction");
        }
        int tx = Math.addExact(x, dx), ty = Math.addExact(y, dy), tz = Math.addExact(z, dz);
        return new Resize(x, y, z, tx, ty, tz, new Bounds(
                Math.min(b.minX, tx), Math.min(b.minY, ty), Math.min(b.minZ, tz),
                Math.max(b.maxX, tx), Math.max(b.maxY, ty), Math.max(b.maxZ, tz)));
    }
}
