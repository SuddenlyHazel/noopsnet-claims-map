// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import io.netty.handler.codec.DecoderException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Protocol v1: a full replacement, including an empty replacement after the last deletion. */
public record ClaimsSnapshotPayload(List<ClaimView> claims) implements CustomPacketPayload {
    public static final int MAX_CLAIMS = 512;
    public static final int MAX_STRING_LENGTH = 128;
    public static final Type<ClaimsSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("noopsnet_claims_map", "snapshot_v1"));
    public static final StreamCodec<FriendlyByteBuf, ClaimsSnapshotPayload> CODEC =
            StreamCodec.ofMember(ClaimsSnapshotPayload::encode, ClaimsSnapshotPayload::decode);

    public ClaimsSnapshotPayload {
        claims = List.copyOf(claims);
        if (claims.size() > MAX_CLAIMS) throw new IllegalArgumentException("Too many claims");
        for (ClaimView claim : claims) {
            checkString(claim.id());
            checkString(claim.dimension());
            checkString(claim.label());
        }
    }

    private static void checkString(String value) {
        if (value.length() > MAX_STRING_LENGTH)
            throw new IllegalArgumentException("Claim string too long");
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(claims.size());
        for (ClaimView claim : claims) {
            buf.writeUtf(claim.id(), MAX_STRING_LENGTH);
            buf.writeUtf(claim.dimension(), MAX_STRING_LENGTH);
            buf.writeInt(claim.minX());
            buf.writeInt(claim.minY());
            buf.writeInt(claim.minZ());
            buf.writeInt(claim.maxX());
            buf.writeInt(claim.maxY());
            buf.writeInt(claim.maxZ());
            buf.writeUtf(claim.label(), MAX_STRING_LENGTH);
            buf.writeBoolean(claim.admin());
            buf.writeBoolean(claim.owned());
        }
    }

    private static ClaimsSnapshotPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CLAIMS)
            throw new DecoderException("Invalid claim count: " + count);
        List<ClaimView> claims = new ArrayList<>(count);
        try {
            for (int i = 0; i < count; i++) {
                claims.add(
                        new ClaimView(
                                buf.readUtf(MAX_STRING_LENGTH),
                                buf.readUtf(MAX_STRING_LENGTH),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readUtf(MAX_STRING_LENGTH),
                                buf.readBoolean(),
                                buf.readBoolean()));
            }
            return new ClaimsSnapshotPayload(claims);
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid claim snapshot", exception);
        }
    }

    /** Truncates public labels without splitting a UTF-16 surrogate pair. */
    static String boundedLabel(String value) {
        if (value.length() <= MAX_STRING_LENGTH) return value;
        int end = MAX_STRING_LENGTH;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    @Override
    public Type<ClaimsSnapshotPayload> type() {
        return TYPE;
    }
}
