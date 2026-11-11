// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap.client;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.noopsnet.claimsmap.ClientClaimState;
import xaero.map.WorldMap;

@Mod(value = "noopsnet_claims_map", dist = Dist.CLIENT)
public final class ClaimsMapClient {
    static boolean visible = true;
    private static boolean registered;
    public ClaimsMapClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ClaimsMapClient::tick);
        NeoForge.EVENT_BUS.addListener(ClaimsMapClient::logout);
        NeoForge.EVENT_BUS.addListener(ClaimsMapClient::commands);
        NeoForge.EVENT_BUS.addListener(ClaimMapActions::render);
    }
    private static void tick(ClientTickEvent.Post event) {
        if (!registered && WorldMap.mapElementRenderHandler != null) {
            WorldMap.mapElementRenderHandler.add(new ClaimMapRenderer());
            registered = true;
        }
        if (ModList.get().isLoaded("xaerominimap")) MinimapClaims.install();
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientClaimState.clear(); }
    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("claimmap").executes(context -> {
            visible = !visible;
            context.getSource().sendSuccess(() -> Component.literal("Claim map overlays " + (visible ? "shown" : "hidden")), false);
            return 1;
        }));
    }
}
