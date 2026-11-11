// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ClaimsSnapshotPayloadTest {
    private static ClaimView claim(String label) {
        return new ClaimView("parent/child", "minecraft:overworld", -31, -64, -1, 0, 319, 27, label, true, false);
    }

    @Test void roundtripPreservesInclusiveBoundsLabelsAndRecipientFlags() {
        var original = new ClaimsSnapshotPayload(List.of(claim("Admin 🌈"),
                new ClaimView("mine", "minecraft:the_nether", 1, 2, 3, 1, 2, 3, "Home", false, true)));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClaimsSnapshotPayload.CODEC.encode(buf, original);
            assertEquals(original, ClaimsSnapshotPayload.CODEC.decode(buf));
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
    }

    @Test void emptySnapshotClearsAndCacheIsImmutable() {
        var input = new ArrayList<>(List.of(claim("A")));
        ClientClaimState.replace(input);
        input.clear();
        assertEquals(1, ClientClaimState.snapshot().size());
        assertThrows(UnsupportedOperationException.class, () -> ClientClaimState.snapshot().clear());
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClaimsSnapshotPayload.CODEC.encode(buf, new ClaimsSnapshotPayload(List.of()));
            ClientClaimState.replace(ClaimsSnapshotPayload.CODEC.decode(buf).claims());
            assertTrue(ClientClaimState.snapshot().isEmpty());
        } finally { ClientClaimState.clear(); buf.release(); }
    }

    @Test void maximumSnapshotStaysBelowVanillaOneMiBPayloadLimit() {
        var value = new ClaimView("界".repeat(128), "界".repeat(128), Integer.MIN_VALUE, -64, -1,
                Integer.MAX_VALUE, 319, 0, "界".repeat(128), false, true);
        var payload = new ClaimsSnapshotPayload(Collections.nCopies(ClaimsSnapshotPayload.MAX_CLAIMS, value));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClaimsSnapshotPayload.CODEC.encode(buf, payload);
            assertTrue(buf.readableBytes() < 1_048_576);
            assertEquals(payload, ClaimsSnapshotPayload.CODEC.decode(buf));
        } finally { buf.release(); }
    }

    @Test void rejectsExcessiveCountsBeforeReadingRecords() {
        for (int count : new int[]{-1, ClaimsSnapshotPayload.MAX_CLAIMS + 1, Integer.MAX_VALUE}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(count);
                assertThrows(DecoderException.class, () -> ClaimsSnapshotPayload.CODEC.decode(buf));
            } finally { buf.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new ClaimsSnapshotPayload(
                Collections.nCopies(ClaimsSnapshotPayload.MAX_CLAIMS + 1, claim("A"))));
    }

    @Test void rejectsTruncatedPayloadWithoutReplacingCache() {
        ClientClaimState.replace(List.of(claim("Existing")));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClaimsSnapshotPayload.CODEC.encode(buf, new ClaimsSnapshotPayload(List.of(claim("New"))));
            buf.writerIndex(buf.writerIndex() - 1);
            assertThrows(IndexOutOfBoundsException.class, () -> {
                var decoded = ClaimsSnapshotPayload.CODEC.decode(buf);
                ClientClaimState.replace(decoded.claims());
            });
            assertEquals("Existing", ClientClaimState.snapshot().getFirst().label());
        } finally { ClientClaimState.clear(); buf.release(); }
    }

    @Test void rejectsOversizedWireStringAndInvertedBounds() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeUtf("x".repeat(129));
            assertThrows(DecoderException.class, () -> ClaimsSnapshotPayload.CODEC.decode(buf));
            buf.clear();
            buf.writeVarInt(1);
            buf.writeUtf("a"); buf.writeUtf("minecraft:overworld");
            buf.writeInt(10); buf.writeInt(0); buf.writeInt(0);
            buf.writeInt(9); buf.writeInt(0); buf.writeInt(0);
            buf.writeUtf("bad"); buf.writeBoolean(false); buf.writeBoolean(false);
            assertThrows(DecoderException.class, () -> ClaimsSnapshotPayload.CODEC.decode(buf));
            assertThrows(IllegalArgumentException.class, () -> new ClaimsSnapshotPayload(List.of(claim("x".repeat(129)))));
        } finally { buf.release(); }
    }

    @Test void labelTruncationPreservesUnicodeBoundary() {
        assertEquals("x".repeat(127), ClaimsSnapshotPayload.boundedLabel("x".repeat(127) + "🌈"));
        assertEquals("x".repeat(128), ClaimsSnapshotPayload.boundedLabel("x".repeat(130)));
        assertEquals("Home", ClaimsSnapshotPayload.boundedLabel("Home"));
    }

    @Test void queuedPacketFromDisconnectedServerCannotRepopulateCache() {
        long previousConnection = ClientClaimState.generation();
        ClientClaimState.clear();
        ClientClaimState.replaceIfCurrent(List.of(claim("Old server")), previousConnection);
        assertTrue(ClientClaimState.snapshot().isEmpty());
        ClientClaimState.replaceIfCurrent(List.of(claim("Current server")), ClientClaimState.generation());
        assertEquals("Current server", ClientClaimState.snapshot().getFirst().label());
        ClientClaimState.clear();
    }
}
