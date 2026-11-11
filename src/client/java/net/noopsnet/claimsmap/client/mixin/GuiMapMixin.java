// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap.client.mixin;

import java.util.ArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.noopsnet.claimsmap.client.ClaimMapActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
    @Shadow private MapTileSelection mapTileSelection;
    @Shadow private int rightClickX;
    @Shadow private int rightClickZ;
    @Shadow private ResourceKey<Level> rightClickDim;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void noopsnet$claimOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> result) {
        ClaimMapActions.append((GuiMap)(Object)this, result.getReturnValue(), mapTileSelection, rightClickX, rightClickZ, rightClickDim);
    }
}
