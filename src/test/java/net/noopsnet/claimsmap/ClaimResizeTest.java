// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClaimResizeTest {
    /** Independently reconstruct what Flan's resize API does with the moved corner. */
    private ClaimResize.Bounds flanResult(ClaimResize.Bounds old, ClaimResize.Resize move) {
        int x = move.fromX() == old.minX() ? old.maxX() : old.minX();
        int y = move.fromY() == old.minY() ? old.maxY() : old.minY();
        int z = move.fromZ() == old.minZ() ? old.maxZ() : old.minZ();
        return new ClaimResize.Bounds(
                Math.min(x, move.toX()),
                Math.min(y, move.toY()),
                Math.min(z, move.toZ()),
                Math.max(x, move.toX()),
                Math.max(y, move.toY()),
                Math.max(z, move.toZ()));
    }

    @Test
    void horizontalExpansionPreservesFullHeightAndOtherFaces() {
        var original = new ClaimResize.Bounds(-32, -64, -16, -17, 319, -1);
        for (String direction : new String[] {"north", "south", "east", "west"}) {
            var move = ClaimResize.expand(original, direction, 16);
            var result = flanResult(original, move);
            assertEquals(move.bounds(), result);
            assertEquals(-64, result.minY());
            assertEquals(319, result.maxY());
            assertEquals(512, result.area());
            assertTrue(
                    result.minX() <= original.minX()
                            && result.maxX() >= original.maxX()
                            && result.minZ() <= original.minZ()
                            && result.maxZ() >= original.maxZ());
        }
    }

    @Test
    void verticalExpansionPreservesFootprintAndOppositeHeight() {
        var original = new ClaimResize.Bounds(100, 20, 200, 115, 40, 215);
        var up = flanResult(original, ClaimResize.expand(original, "up", 10));
        var down = flanResult(original, ClaimResize.expand(original, "down", 10));
        assertEquals(new ClaimResize.Bounds(100, 20, 200, 115, 50, 215), up);
        assertEquals(new ClaimResize.Bounds(100, 10, 200, 115, 40, 215), down);
        assertEquals(256, up.area());
        assertEquals(256, down.area());
    }

    @Test
    void invalidDistancesDirectionsAndOverflowFailClosed() {
        var b = new ClaimResize.Bounds(0, 0, 0, 15, 30, 15);
        for (int n : new int[] {-1, 0, 4097, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> ClaimResize.expand(b, "east", n));
        assertThrows(IllegalArgumentException.class, () -> ClaimResize.expand(b, "sideways", 1));
        assertThrows(
                ArithmeticException.class,
                () ->
                        ClaimResize.expand(
                                new ClaimResize.Bounds(0, 0, 0, Integer.MAX_VALUE, 30, 15),
                                "east",
                                1));
    }
}
