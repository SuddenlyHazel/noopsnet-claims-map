// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.event.ItemInteractEvents;
import io.github.flemmli97.flan.platform.integration.permissions.PermissionNodeHandler;
import io.github.flemmli97.flan.player.ClaimMode;
import io.github.flemmli97.flan.player.PlayerClaimData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/** All mutations run as the requesting player on the server, through Flan. */
final class ClaimEdits {
    private record Selection(String id, String dimension) {}
    private static final Map<UUID, Selection> selections = new HashMap<>();
    static void forget(UUID player) { selections.remove(player); }
    static void clearSelections() { selections.clear(); }
    static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("claimtools")
                .requires(s -> PermissionNodeHandler.INSTANCE.perm(s, PermissionNodeHandler.CLAIM_CREATE))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Claim tools: chunk | select <id> <dimension> | expand <north/south/east/west/up/down> <blocks> | clear. Expansion uses your selected Flan claim, otherwise the claim you stand in."), false);
                    return 1;
                });
        root.then(Commands.literal("chunk").executes(c -> {
            var p = c.getSource().getPlayerOrException();
            return chunk(p, p.getBlockX() >> 4, p.getBlockZ() >> 4, p.level().dimension().location().toString());
        }).then(Commands.argument("x", IntegerArgumentType.integer(-1874999, 1874999))
                .then(Commands.argument("z", IntegerArgumentType.integer(-1874999, 1874999))
                .then(Commands.argument("dimension", ResourceLocationArgument.id()).executes(c -> chunk(c.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), ResourceLocationArgument.getId(c, "dimension").toString()))))));
        root.then(Commands.literal("select").then(Commands.argument("id", StringArgumentType.string())
                .then(Commands.argument("dimension", ResourceLocationArgument.id()).executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    dimension(p, ResourceLocationArgument.getId(c, "dimension").toString());
                    Claim claim = resolve(p, StringArgumentType.getString(c, "id"));
                    editable(p, claim);
                    var data = PlayerClaimData.get(p);
                    data.setEditClaim(null, 0);
                    data.setEditingCorner(null);
                    selections.put(p.getUUID(), new Selection(StringArgumentType.getString(c, "id"), p.level().dimension().location().toString()));
                    data.addDisplayClaim(claim, io.github.flemmli97.flan.player.display.EnumDisplayType.MAIN, p.getBlockY());
                    c.getSource().sendSuccess(() -> Component.literal("Selected " + (claim.isSubclaim() ? "subclaim" : "claim") + ". Use /claimtools expand <direction> <blocks>."), false);
                    return 1;
                }))));
        root.then(Commands.literal("clear").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            forget(player.getUUID());
            var data = PlayerClaimData.get(player);
            data.setEditClaim(null, 0); data.setEditingCorner(null);
            c.getSource().sendSuccess(() -> Component.literal("Claim selection cleared."), false);
            return 1;
        }));
        var expand = Commands.literal("expand");
        for (Direction direction : Direction.values()) {
            expand.then(Commands.literal(direction.getName()).then(Commands.argument("blocks", IntegerArgumentType.integer(1, 4096))
                    .executes(c -> expand(c.getSource(), direction, IntegerArgumentType.getInteger(c, "blocks")))));
        }
        root.then(expand);
        ClaimAreaCommands.register(root);
        event.getDispatcher().register(root);
    }

    private static int chunk(ServerPlayer p, int x, int z, String dimension) throws CommandSyntaxException {
        dimension(p, dimension);
        if (!ItemInteractEvents.canClaimWorld(p.serverLevel(), p)) return 0;
        // Map claims stay local, require no terrain loads, and have bounded cost.
        if (Math.abs((long)x - (p.getBlockX() >> 4)) > 8 || Math.abs((long)z - (p.getBlockZ() >> 4)) > 8)
            throw error("Move within 8 chunks of that chunk to claim it.");
        BlockPos from = new BlockPos(x * 16, p.level().getMinBuildHeight(), z * 16);
        BlockPos to = new BlockPos(x * 16 + 15, p.level().getMaxBuildHeight() - 1, z * 16 + 15);
        if (!p.level().getWorldBorder().isWithinBounds(from) || !p.level().getWorldBorder().isWithinBounds(to))
            throw error("That chunk crosses the world border.");
        var data = PlayerClaimData.get(p);
        ClaimMode mode = data.getClaimMode();
        Claim selection = data.currentEdit();
        BlockPos corner = data.editingCorner();
        try {
            data.setEditMode(ClaimMode.DEFAULT_3D);
            return ClaimStorage.get(p.serverLevel()).createClaim(from, to, p) ? 1 : 0;
        } finally {
            data.setEditMode(mode);
            data.setEditClaim(selection, p.getBlockY());
            data.setEditingCorner(corner);
        }
    }

    static Claim resolve(ServerPlayer p, String id) throws CommandSyntaxException {
        try {
            String[] parts = id.split("/", -1);
            Claim parent = ClaimStorage.get(p.serverLevel()).getFromUUID(UUID.fromString(parts[0]));
            if (parent != null && parts.length == 1) return parent;
            if (parent != null && parts.length == 2) {
                UUID child = UUID.fromString(parts[1]);
                for (Claim sub : parent.getAllSubclaims()) if (sub.getClaimID().equals(child)) return sub;
            }
        } catch (IllegalArgumentException ignored) { }
        throw error("Claim no longer exists in this dimension. Reopen the map.");
    }

    private static Claim target(ServerPlayer p) throws CommandSyntaxException {
        var data = PlayerClaimData.get(p);
        Claim claim = data.currentEdit();
        if (claim != null) {
            // Validate the selected object belongs to this dimension and is still current.
            String id = claim.parentClaim() == null ? claim.getClaimID().toString()
                    : claim.parentClaim().getClaimID() + "/" + claim.getClaimID();
            if (resolve(p, id) != claim || claim.isRemoved()) throw error("Selection is stale. Select the claim again.");
        } else if (selections.containsKey(p.getUUID())) {
            var selected = selections.get(p.getUUID());
            dimension(p, selected.dimension());
            claim = resolve(p, selected.id());
        } else {
            claim = ClaimStorage.get(p.serverLevel()).getClaimAt(p.blockPosition());
            if (claim != null && data.getClaimMode().isSubclaim) claim = claim.getSubClaim(p.blockPosition());
        }
        if (claim == null) throw error("Select an existing claim on the map or stand inside it first. An unfinished new-claim selection is not a claim.");
        editable(p, claim);
        return claim;
    }

    private static void editable(ServerPlayer p, Claim claim) throws CommandSyntaxException {
        if (!claim.canInteract(p, BuiltinPermission.EDITCLAIM, p.blockPosition())
                || claim.parentClaim() != null && !claim.parentClaim().canInteract(p, BuiltinPermission.EDITCLAIM, p.blockPosition()))
            throw error("Flan does not allow you to edit this claim.");
    }

    private static int expand(CommandSourceStack source, Direction direction, int amount) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        if (!ItemInteractEvents.canClaimWorld(p.serverLevel(), p)) return 0;
        Claim claim = target(p);
        if (!claim.is3d() && direction.getAxis() == Direction.Axis.Y)
            throw error("Vertical expansion requires a 3D claim. This claim already extends upward without a ceiling.");
        var b = claim.getDimensions();
        var resize = ClaimResize.expand(new ClaimResize.Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()), direction.getName(), amount);
        var next = resize.bounds();
        if (claim.is3d() && (next.minY() < p.level().getMinBuildHeight() || next.maxY() >= p.level().getMaxBuildHeight()))
            throw error("Expansion would cross the build-height limit (" + p.level().getMinBuildHeight() + " to " + (p.level().getMaxBuildHeight() - 1) + ").");
        BlockPos from = new BlockPos(resize.fromX(), resize.fromY(), resize.fromZ());
        BlockPos to = new BlockPos(resize.toX(), resize.toY(), resize.toZ());
        if (Math.abs((long)next.minX()) >= 30000000 || Math.abs((long)next.maxX()) >= 30000000
                || Math.abs((long)next.minZ()) >= 30000000 || Math.abs((long)next.maxZ()) >= 30000000
                || next.area() > 4_194_304 || !p.level().getWorldBorder().isWithinBounds(to))
            throw error("Expansion exceeds world or claim-size limits.");
        if (claim.parentClaim() != null) {
            var parent = claim.parentClaim();
            var limit = parent.getDimensions();
            if (next.minX() < limit.minX() || next.minY() < limit.minY() || next.minZ() < limit.minZ()
                    || next.maxX() > limit.maxX() || next.maxY() > limit.maxY() || next.maxZ() > limit.maxZ())
                throw error("A subclaim must stay entirely inside its parent claim.");
            if (!parent.resizeSubclaim(claim, from, to).isEmpty()) throw error("Expansion overlaps another subclaim.");
        } else if (!ClaimStorage.get(p.serverLevel()).resizeClaim(claim, from, to, p)) return 0;
        source.sendSuccess(() -> Component.literal("Expanded " + direction.getName() + " by " + amount + " blocks. Y " + next.minY() + "–" + next.maxY() + ". Permissions and subclaims retained."), false);
        return 1;
    }
    static void dimension(ServerPlayer p, String dimension) throws CommandSyntaxException {
        if (!p.level().dimension().location().toString().equals(dimension)) throw error("Open the map for your current dimension.");
    }
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
