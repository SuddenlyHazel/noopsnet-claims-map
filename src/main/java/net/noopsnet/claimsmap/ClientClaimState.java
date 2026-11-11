// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import java.util.List;

/** Immutable, atomically replaced common cache; deliberately has no client dependencies. */
public final class ClientClaimState {
    private static volatile List<ClaimView> claims = List.of();
    private static volatile long generation;

    private ClientClaimState() {}

    public static List<ClaimView> snapshot() { return claims; }
    public static void replace(List<ClaimView> replacement) { claims = List.copyOf(replacement); }
    public static long generation() { return generation; }
    public static synchronized void replaceIfCurrent(List<ClaimView> replacement, long expectedGeneration) {
        if (generation == expectedGeneration) claims = List.copyOf(replacement);
    }
    public static synchronized void clear() { generation++; claims = List.of(); }
}
