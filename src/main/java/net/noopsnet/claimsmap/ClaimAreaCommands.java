// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.event.ItemInteractEvents;
import io.github.flemmli97.flan.gui.ClaimMenuScreenHandler;
import io.github.flemmli97.flan.platform.integration.permissions.PermissionNodeHandler;
import io.github.flemmli97.flan.player.ClaimMode;
import io.github.flemmli97.flan.player.PlayerClaimData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedHashSet;
import java.util.Set;

final class ClaimAreaCommands {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        var area = Commands.literal("area");
        for (String action : new String[]{"claim", "unclaim", "admin_claim", "admin_delete"}) {
            var branch = Commands.literal(action).requires(s -> !(s.getEntity() instanceof ServerPlayer p) || allowed(p, action));
            branch.then(Commands.argument("minX", IntegerArgumentType.integer(-1874999, 1874999))
                .then(Commands.argument("minZ", IntegerArgumentType.integer(-1874999, 1874999))
                .then(Commands.argument("maxX", IntegerArgumentType.integer(-1874999, 1874999))
                .then(Commands.argument("maxZ", IntegerArgumentType.integer(-1874999, 1874999))
                .then(Commands.argument("dimension", ResourceLocationArgument.id())
                .then(Commands.literal("confirm").executes(c -> apply(c, action))))))));
            area.then(branch);
        }
        root.then(area);
        root.then(Commands.literal("permissions")
            .requires(s -> !(s.getEntity() instanceof ServerPlayer p) || PermissionNodeHandler.INSTANCE.perm(p, PermissionNodeHandler.CMD_MENU, false))
            .then(Commands.argument("id", StringArgumentType.string())
            .then(Commands.argument("dimension", ResourceLocationArgument.id()).executes(c -> {
                var p = c.getSource().getPlayerOrException();
                ClaimEdits.dimension(p, ResourceLocationArgument.getId(c, "dimension").toString());
                Claim claim = ClaimEdits.resolve(p, StringArgumentType.getString(c, "id"));
                if (!PermissionNodeHandler.INSTANCE.perm(p, PermissionNodeHandler.CMD_MENU, false)
                        || !claim.canInteract(p, BuiltinPermission.EDITPERMS, p.blockPosition()))
                    throw error("Flan does not allow you to edit this claim's permissions.");
                ClaimMenuScreenHandler.openClaimMenu(p, claim);
                return 1;
            }))));
    }
    private static boolean allowed(ServerPlayer p, String action) {
        return switch (action) {
            case "admin_claim" -> p.hasPermissions(2) && PermissionNodeHandler.INSTANCE.perm(p, PermissionNodeHandler.CLAIM_ADMIN_CREATE, true);
            case "admin_delete" -> p.hasPermissions(2) && PermissionNodeHandler.INSTANCE.perm(p, PermissionNodeHandler.CMD_ADMIN_DELETE, true);
            case "unclaim" -> PermissionNodeHandler.INSTANCE.perm(p, PermissionNodeHandler.CMD_DELETE, false);
            default -> PermissionNodeHandler.INSTANCE.perm(p, PermissionNodeHandler.CLAIM_CREATE, false);
        };
    }
    private static int apply(CommandContext<CommandSourceStack> context, String action) throws CommandSyntaxException {
        ServerPlayer p = context.getSource().getPlayerOrException();
        if (!allowed(p, action)) throw error("You no longer have permission for that map action.");
        ClaimEdits.dimension(p, ResourceLocationArgument.getId(context, "dimension").toString());
        ChunkArea area;
        try {
            area = new ChunkArea(IntegerArgumentType.getInteger(context, "minX"), IntegerArgumentType.getInteger(context, "minZ"),
                    IntegerArgumentType.getInteger(context, "maxX"), IntegerArgumentType.getInteger(context, "maxZ"));
        } catch (IllegalArgumentException e) { throw error(e.getMessage()); }
        boolean admin = action.startsWith("admin_");
        if (!admin && !area.nearby(p.getBlockX() >> 4, p.getBlockZ() >> 4)) throw error("Move within 8 chunks of every selected chunk.");
        if (!admin && !ItemInteractEvents.canClaimWorld(p.serverLevel(), p)) return 0;
        BlockPos from = new BlockPos(area.left(), p.level().getMinBuildHeight(), area.top());
        BlockPos to = new BlockPos(area.right(), p.level().getMaxBuildHeight() - 1, area.bottom());
        if (!p.level().getWorldBorder().isWithinBounds(from) || !p.level().getWorldBorder().isWithinBounds(to))
            throw error("Selection crosses the world border.");
        var storage = ClaimStorage.get(p.serverLevel());
        if (action.equals("claim")) {
            var data = PlayerClaimData.get(p);
            ClaimMode mode = data.getClaimMode(); Claim selected = data.currentEdit(); BlockPos corner = data.editingCorner();
            try {
                data.setEditMode(ClaimMode.DEFAULT_3D);
                return storage.createClaim(from, to, p) ? 1 : 0;
            } finally {
                data.setEditMode(mode); data.setEditClaim(selected, p.getBlockY()); data.setEditingCorner(corner);
            }
        }
        if (action.equals("admin_claim")) {
            if (storage.createAdminClaim(from, to, p.serverLevel(), true) == null) throw error("Selection overlaps an existing claim. Nothing changed.");
            context.getSource().sendSuccess(() -> Component.literal("Created an admin claim covering " + area.chunks() + " chunks."), false);
            return 1;
        }
        Set<Claim> claims = new LinkedHashSet<>();
        for (int x = area.minX(); x <= area.maxX(); x++)
            for (int z = area.minZ(); z <= area.maxZ(); z++) claims.addAll(storage.getClaimsAt(x, z));
        if (claims.isEmpty()) throw error("No claims in this selection.");
        // Preflight every overlapping parent, at every height, before deleting any.
        // Never carve rectangles or discard subclaims outside the selection.
        for (Claim claim : claims) {
            var b = claim.getDimensions();
            if (!area.contains(b.minX(), b.minZ(), b.maxX(), b.maxZ()))
                throw error("Selection cuts through a larger claim. Select its entire footprint; nothing changed.");
            if (!admin && (!p.getUUID().equals(claim.getOwner()) || !claim.canInteract(p, BuiltinPermission.EDITCLAIM, p.blockPosition())))
                throw error("Selection includes a claim you cannot unclaim. Nothing changed.");
        }
        for (Claim claim : claims) storage.deleteClaim(claim, true, ClaimMode.DEFAULT, p.serverLevel());
        ClaimEdits.forget(p.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Removed " + claims.size() + " claims and their subclaims. Blocks in the world were not changed."), false);
        return claims.size();
    }
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
