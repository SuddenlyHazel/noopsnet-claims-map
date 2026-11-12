// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import java.util.Objects;

/** Public map data only. All six bounds are inclusive block coordinates. */
public record ClaimView(
        String id,
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        String label,
        boolean admin,
        boolean owned) {
    public ClaimView {
        Objects.requireNonNull(id);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(label);
        if (id.isEmpty() || dimension.isEmpty())
            throw new IllegalArgumentException("Missing claim identity/dimension");
        if (minX > maxX || minY > maxY || minZ > maxZ)
            throw new IllegalArgumentException("Inverted claim bounds");
    }
}
