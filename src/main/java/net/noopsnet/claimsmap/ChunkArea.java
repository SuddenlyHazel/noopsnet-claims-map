// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

/** Inclusive chunk rectangle. No terrain access is required. */
public record ChunkArea(int minX, int minZ, int maxX, int maxZ) {
    public ChunkArea {
        if (minX > maxX
                || minZ > maxZ
                || minX < -1874999
                || minZ < -1874999
                || maxX > 1874999
                || maxZ > 1874999) throw new IllegalArgumentException("Invalid chunk bounds");
        if (((long) maxX - minX + 1) * ((long) maxZ - minZ + 1) > 4096)
            throw new IllegalArgumentException("Select at most 4096 chunks");
    }

    public int chunks() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public int blocks() {
        return chunks() * 256;
    }

    public int left() {
        return minX * 16;
    }

    public int right() {
        return maxX * 16 + 15;
    }

    public int top() {
        return minZ * 16;
    }

    public int bottom() {
        return maxZ * 16 + 15;
    }

    public boolean contains(int x, int z, int X, int Z) {
        return left() <= x && right() >= X && top() <= z && bottom() >= Z;
    }

    public boolean nearby(int x, int z) {
        return Math.abs((long) minX - x) <= 8
                && Math.abs((long) maxX - x) <= 8
                && Math.abs((long) minZ - z) <= 8
                && Math.abs((long) maxZ - z) <= 8;
    }
}
