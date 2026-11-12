// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.noopsnet.claimsmap.ClaimView;

final class ClaimDrawing {
    private ClaimDrawing() {}

    static double centerX(ClaimView c) {
        return ((double) c.minX() + c.maxX() + 1) / 2;
    }

    static double centerZ(ClaimView c) {
        return ((double) c.minZ() + c.maxZ() + 1) / 2;
    }

    static void draw(GuiGraphics graphics, ClaimView c, double scale, boolean label) {
        double halfX = ((double) c.maxX() + 1 - c.minX()) * scale / 2;
        double halfZ = ((double) c.maxZ() + 1 - c.minZ()) * scale / 2;
        // The map can be panned far from large claims. Bound raster coordinates.
        int left = bounded(-halfX), right = bounded(halfX);
        int top = bounded(-halfZ), bottom = bounded(halfZ);
        int rgb =
                c.id().contains("/")
                        ? 0x6EB7F0
                        : c.admin() ? 0xB27BE8 : c.owned() ? 0x61CC8A : 0xE9B86C;
        graphics.fill(left, top, right, bottom, 0x22000000 | rgb);
        graphics.fill(left, top, right, top + 1, 0xDD000000 | rgb);
        graphics.fill(left, bottom - 1, right, bottom, 0xDD000000 | rgb);
        graphics.fill(left, top, left + 1, bottom, 0xDD000000 | rgb);
        graphics.fill(right - 1, top, right, bottom, 0xDD000000 | rgb);
        if (!label) return;
        var font = Minecraft.getInstance().font;
        String text =
                (c.id().contains("/") ? "Subclaim: " : "")
                        + c.label()
                        + " · Y "
                        + c.minY()
                        + "–"
                        + c.maxY();
        int textWidth = font.width(text);
        if (right - left >= textWidth + 12 && bottom - top >= 20) {
            graphics.fill(left + 3, top + 3, left + textWidth + 9, top + 16, 0xAA241B2A);
            graphics.drawString(font, text, left + 6, top + 5, 0xFFFFFFFF, false);
        }
    }

    private static int bounded(double n) {
        return (int) Math.max(-1000000, Math.min(1000000, Math.round(n)));
    }
}
