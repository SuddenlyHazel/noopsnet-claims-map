// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

@Mod("noopsnet_claims_map")
public final class NoopsClaimsMap {
    public static final String ID = "noopsnet_claims_map";
    private final Map<UUID, List<ClaimView>> lastSent = new HashMap<>();
    private int ticks;

    public NoopsClaimsMap(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) -> {
            if (ModList.get().isLoaded("flan")) ClaimEdits.register(event);
        });
        NeoForge.EVENT_BUS.addListener(this::loggedIn);
        NeoForge.EVENT_BUS.addListener(this::changedDimension);
        NeoForge.EVENT_BUS.addListener(this::loggedOut);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().executesOn(HandlerThread.NETWORK)
                .playToClient(ClaimsSnapshotPayload.TYPE, ClaimsSnapshotPayload.CODEC,
                        (payload, context) -> {
                            long generation = ClientClaimState.generation();
                            context.enqueueWork(() -> ClientClaimState.replaceIfCurrent(payload.claims(), generation));
                        });
    }

    private void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastSent.remove(player.getUUID());
            refresh(player);
        }
    }

    private void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ModList.get().isLoaded("flan")) ClaimEdits.forget(player.getUUID());
            refresh(player);
        }
    }

    private void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSent.remove(event.getEntity().getUUID());
        if (ModList.get().isLoaded("flan")) ClaimEdits.forget(event.getEntity().getUUID());
    }

    private void tick(ServerTickEvent.Post event) {
        if (++ticks < 20) return;
        ticks = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) refresh(player);
    }

    private void refresh(ServerPlayer player) {
        // The client pack intentionally has no Flan. An integrated server must
        // remain usable without loading the optional Flan adapter at all.
        if (!ModList.get().isLoaded("flan")) return;
        if (!player.connection.hasChannel(ClaimsSnapshotPayload.TYPE)) return;
        List<ClaimView> snapshot = FlanClaims.snapshot(player);
        if (snapshot.equals(lastSent.get(player.getUUID()))) return;
        PacketDistributor.sendToPlayer(player, new ClaimsSnapshotPayload(snapshot));
        lastSent.put(player.getUUID(), snapshot);
    }

    private void stopped(ServerStoppedEvent event) {
        lastSent.clear();
        if (ModList.get().isLoaded("flan")) ClaimEdits.clearSelections();
        ticks = 0;
    }
}
