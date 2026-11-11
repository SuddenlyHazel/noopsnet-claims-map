// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkAreaTest {
    @Test void inclusiveNegativeChunkEdgesAndCost() {
        var area = new ChunkArea(-2, -1, 0, 1);
        assertEquals(-32, area.left()); assertEquals(15, area.right());
        assertEquals(-16, area.top()); assertEquals(31, area.bottom());
        assertEquals(9, area.chunks()); assertEquals(2304, area.blocks());
        assertTrue(area.contains(-32, -16, 15, 31));
        assertFalse(area.contains(-33, -16, 15, 31));
        assertFalse(area.contains(-32, -16, 16, 31));
    }
    @Test void selectionsAreBoundedBeforeServerLoops() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkArea(2, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChunkArea(0, 0, 10000, 10000));
        assertThrows(IllegalArgumentException.class, () -> new ChunkArea(Integer.MIN_VALUE, 0, 0, 0));
        assertEquals(4096, new ChunkArea(0, 0, 63, 63).chunks());
    }
    @Test void everyCornerMustBeLocal() {
        assertTrue(new ChunkArea(-8, -8, 8, 8).nearby(0, 0));
        assertFalse(new ChunkArea(-9, -8, 8, 8).nearby(0, 0));
    }
}
