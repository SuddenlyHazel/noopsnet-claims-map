// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.server.level.ServerPlayer;

/** Loaded only by server events: clients do not need Flan installed. No Flan writes. */
final class FlanClaims {
    private FlanClaims() {}

    static List<ClaimView> snapshot(ServerPlayer recipient) {
        // Bounded retained working set; stable keys prevent hash iteration order from causing resends.
        TreeMap<String, Claim> selected = new TreeMap<>();
        for (var level : recipient.getServer().getAllLevels()) {
            String dimension = level.dimension().location().toString();
            // An overlong dimension cannot be represented faithfully, so omit it rather than rename it.
            if (dimension.length() > ClaimsSnapshotPayload.MAX_STRING_LENGTH) continue;
            for (var ownersClaims : ClaimStorage.get(level).getClaims().values()) {
                for (Claim claim : ownersClaims) {
                    retain(selected, dimension, claim);
                    for (Claim subclaim : claim.getAllSubclaims()) retain(selected, dimension, subclaim);
                }
            }
        }
        List<ClaimView> result = new ArrayList<>(selected.size());
        for (var entry : selected.entrySet()) {
            Claim claim = entry.getValue();
            var box = claim.getDimensions();
            String label;
            try {
                // Flan's public display name, including its normal owner-name substitution.
                label = claim.getClaimName();
            } catch (IllegalArgumentException exception) {
                // A malformed user format string must not break every recipient's refresh.
                label = claim.isAdminClaim() ? "Admin claim" : "Claim";
            }
            if (label == null || label.isBlank()) label = claim.isAdminClaim() ? "Admin claim" : "Claim";
            result.add(new ClaimView(entry.getKey().substring(entry.getKey().indexOf('|') + 1), claim.getLevel().dimension().location().toString(),
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
                    ClaimsSnapshotPayload.boundedLabel(label), claim.isAdminClaim(),
                    recipient.getUUID().equals(claim.getOwner())));
        }
        return List.copyOf(result);
    }

    private static void retain(TreeMap<String, Claim> selected, String dimension, Claim claim) {
        if (claim.isRemoved()) return;
        // Claim UUIDs are claim identities, never player UUIDs. Parent path disambiguates subclaim IDs.
        String id = claim.isSubclaim()
                ? claim.parentClaim().getClaimID() + "/" + claim.getClaimID() : claim.getClaimID().toString();
        String key = dimension + "|" + id;
        // Keep the wire id compact while sorting by dimension and claim identity.
        selected.put(key, claim);
        if (selected.size() > ClaimsSnapshotPayload.MAX_CLAIMS) selected.pollLastEntry();
    }
}
