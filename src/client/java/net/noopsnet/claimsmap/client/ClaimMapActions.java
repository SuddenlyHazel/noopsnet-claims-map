// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.noopsnet.claimsmap.ChunkArea;
import net.noopsnet.claimsmap.ClaimView;
import net.noopsnet.claimsmap.ClientClaimState;

import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.List;

/** Extends Xaero's native menu; never consumes its drag-selection mouse events. */
public final class ClaimMapActions {
    private static boolean allowed(String... path) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        var node = connection.getCommands().getRoot().getChild("claimtools");
        for (String part : path) {
            if (node == null) return false;
            node = node.getChild(part);
        }
        return node != null;
    }

    public static void append(
            GuiMap map,
            ArrayList<RightClickOption> options,
            MapTileSelection selection,
            int blockX,
            int blockZ,
            ResourceKey<Level> dimension) {
        if (!allowed("area") || dimension == null) return;
        ChunkArea area;
        try {
            area =
                    selection == null
                            ? new ChunkArea(blockX >> 4, blockZ >> 4, blockX >> 4, blockZ >> 4)
                            : new ChunkArea(
                                    selection.getLeft(),
                                    selection.getTop(),
                                    selection.getRight(),
                                    selection.getBottom());
        } catch (IllegalArgumentException e) {
            return;
        }
        String dim = dimension.location().toString();
        if (allowed("area", "claim"))
            add(options, map, "Claim selected chunks…", () -> confirm(map, area, dim, "claim"));
        if (allowed("area", "unclaim"))
            add(options, map, "Unclaim selected claims…", () -> confirm(map, area, dim, "unclaim"));
        add(
                options,
                map,
                "Select claim to expand…",
                () -> Minecraft.getInstance().setScreen(new Picker(map, area, dim, false)));
        if (allowed("permissions"))
            add(
                    options,
                    map,
                    "Flan permissions…",
                    () -> Minecraft.getInstance().setScreen(new Picker(map, area, dim, true)));
        // The server-provided command tree is only a visibility hint. Every request
        // rechecks the actual player's OP level and Flan permission on the server.
        if (allowed("area", "admin_claim"))
            add(
                    options,
                    map,
                    "[OP] Create admin claim…",
                    () -> confirm(map, area, dim, "admin_claim"));
        if (allowed("area", "admin_delete"))
            add(
                    options,
                    map,
                    "[OP] Delete selected claims…",
                    () -> confirm(map, area, dim, "admin_delete"));
    }

    private static void add(
            ArrayList<RightClickOption> options, GuiMap map, String label, Runnable action) {
        options.add(
                new RightClickOption(label, options.size(), map) {
                    @Override
                    public Component getDisplayName() {
                        return Component.literal(label);
                    }

                    @Override
                    public void onAction(Screen screen) {
                        action.run();
                    }
                });
    }

    private static void confirm(GuiMap map, ChunkArea area, String dimension, String action) {
        boolean delete = action.equals("unclaim") || action.equals("admin_delete");
        String title =
                switch (action) {
                    case "admin_claim" -> "Create admin claim?";
                    case "admin_delete" -> "Delete claims, including other owners' claims?";
                    case "unclaim" -> "Unclaim your selected claims?";
                    default -> "Claim selected chunks?";
                };
        String description =
                area.chunks()
                        + " chunks: X "
                        + area.minX()
                        + "–"
                        + area.maxX()
                        + ", Z "
                        + area.minZ()
                        + "–"
                        + area.maxZ()
                        + ". "
                        + (delete
                                ? "Removes entire claims and their subclaims at all heights."
                                        + " Partial overlaps are refused; nothing outside this"
                                        + " rectangle is removed."
                                : "Creates one full-height rectangular claim. "
                                        + (action.equals("claim")
                                                ? "Cost: " + area.blocks() + " claim blocks."
                                                : "Server-owned protection; no personal block"
                                                        + " cost."));
        Minecraft.getInstance()
                .setScreen(
                        new ConfirmScreen(
                                confirmed -> {
                                    if (confirmed) {
                                        Minecraft.getInstance().setScreen(null);
                                        send(
                                                "claimtools area "
                                                        + action
                                                        + " "
                                                        + area.minX()
                                                        + " "
                                                        + area.minZ()
                                                        + " "
                                                        + area.maxX()
                                                        + " "
                                                        + area.maxZ()
                                                        + " "
                                                        + dimension
                                                        + " confirm");
                                    } else Minecraft.getInstance().setScreen(map);
                                },
                                Component.literal(title),
                                Component.literal(description)));
    }

    private static void send(String command) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }

    static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof GuiMap) || !ClaimsMapClient.visible || !allowed("area"))
            return;
        var g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        String text = "Drag-select chunks, then right-click for claim tools";
        int left = Math.max(4, (event.getScreen().width - font.width(text)) / 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, 1200);
        g.fill(left - 3, 3, left + font.width(text) + 3, 17, 0xCC17131C);
        g.drawString(font, text, left, 6, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    private static final class Picker extends Screen {
        private final Screen parent;
        private final String dimension;
        private final boolean permissions;
        private final List<ClaimView> claims;
        private int page;

        Picker(Screen parent, ChunkArea area, String dimension, boolean permissions) {
            super(
                    Component.literal(
                            permissions ? "Choose claim permissions" : "Choose claim to expand"));
            this.parent = parent;
            this.dimension = dimension;
            this.permissions = permissions;
            claims =
                    ClientClaimState.snapshot().stream()
                            .filter(
                                    c ->
                                            c.dimension().equals(dimension)
                                                    && c.minX() <= area.right()
                                                    && c.maxX() >= area.left()
                                                    && c.minZ() <= area.bottom()
                                                    && c.maxZ() >= area.top())
                            .toList();
        }

        @Override
        protected void init() {
            int count = Math.max(1, (height - 100) / 24);
            for (int i = page * count; i < Math.min(claims.size(), (page + 1) * count); i++) {
                var c = claims.get(i);
                String label =
                        (c.id().contains("/") ? "Subclaim: " : "")
                                + c.label()
                                + " · Y "
                                + c.minY()
                                + "–"
                                + c.maxY();
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(
                                                font.plainSubstrByWidth(
                                                        label, Math.min(340, width - 30))),
                                        b -> {
                                            minecraft.setScreen(null);
                                            send(
                                                    "claimtools "
                                                            + (permissions
                                                                    ? "permissions"
                                                                    : "select")
                                                            + " \""
                                                            + c.id()
                                                            + "\" "
                                                            + dimension);
                                        })
                                .bounds(
                                        width / 2 - Math.min(180, (width - 20) / 2),
                                        40 + (i % count) * 24,
                                        Math.min(360, width - 20),
                                        20)
                                .build());
            }
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Next page"),
                                    b -> {
                                        page =
                                                (page + 1)
                                                        % Math.max(
                                                                1,
                                                                (claims.size() + count - 1)
                                                                        / count);
                                        rebuildWidgets();
                                    })
                            .bounds(width / 2 - 150, height - 52, 145, 20)
                            .build());
            addRenderableWidget(
                    Button.builder(Component.literal("Back"), b -> onClose())
                            .bounds(width / 2 + 5, height - 52, 145, 20)
                            .build());
        }

        @Override
        public void render(GuiGraphics g, int x, int y, float ticks) {
            super.render(g, x, y, ticks);
            g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
            if (claims.isEmpty())
                g.drawCenteredString(
                        font, "No displayed claims in this selection.", width / 2, 45, 0xFFFFFFFF);
            g.drawCenteredString(
                    font,
                    "Flan checks your permissions when you choose a claim.",
                    width / 2,
                    height - 20,
                    0xFFDDDDDD);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
