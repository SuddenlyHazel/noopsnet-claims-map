// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap.client;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.noopsnet.claimsmap.ClaimView;
import net.noopsnet.claimsmap.ClientClaimState;

import org.slf4j.LoggerFactory;

import xaero.common.HudMod;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.map.MinimapElementMapRendererHandler;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

/** Adapter for the pinned 26.6.0 FBO minimap; uses its clipping and rotation. */
final class MinimapClaims {
    private static Field handlerField;
    private static MinimapElementMapRendererHandler installed;
    private static boolean unavailable;

    private MinimapClaims() {}

    static void install() {
        if (unavailable || HudMod.INSTANCE == null || HudMod.INSTANCE.getMinimap() == null) return;
        try {
            if (handlerField == null)
                handlerField = field(MinimapFBORenderer.class, "minimapElementMapRendererHandler");
            var fbo = HudMod.INSTANCE.getMinimap().getMinimapFBORenderer();
            if (fbo == null) return;
            var handler = (MinimapElementMapRendererHandler) handlerField.get(fbo);
            if (handler == null || handler == installed) return;
            var context = new Context(handler);
            handler.add(new Renderer(context));
            installed = handler;
        } catch (ReflectiveOperationException | RuntimeException error) {
            unavailable = true;
            LoggerFactory.getLogger("noopsnet_claims_map")
                    .warn(
                            "Minimap claim overlays unavailable; full-screen overlays remain"
                                    + " enabled",
                            error);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class Context {
        final Object handler;
        final Field ps, pc, zoom;
        String dimension = "";
        double angle, scale = 1;

        Context(Object handler) throws ReflectiveOperationException {
            this.handler = handler;
            Class<?> type = MinimapElementMapRendererHandler.class;
            ps = field(type, "ps");
            pc = field(type, "pc");
            zoom = field(type, "zoom");
        }
    }

    private static final class Renderer extends MinimapElementRenderer<ClaimView, Context> {
        Renderer(Context context) {
            super(new Reader(), new Provider(), context);
        }

        @Override
        public int getOrder() {
            return -100;
        }

        @Override
        public boolean shouldRender(MinimapElementRenderLocation location) {
            return ClaimsMapClient.visible
                    && !unavailable
                    && location == MinimapElementRenderLocation.IN_MINIMAP;
        }

        @Override
        public void preRender(
                MinimapElementRenderInfo info,
                MultiBufferSource.BufferSource buffers,
                MultiTextureRenderTypeRendererProvider textures) {
            context.dimension =
                    info.mapDimension == null ? "" : info.mapDimension.location().toString();
            try {
                context.scale = context.zoom.getDouble(context.handler);
                context.angle =
                        Math.atan2(
                                context.pc.getDouble(context.handler),
                                context.ps.getDouble(context.handler));
            } catch (IllegalAccessException error) {
                unavailable = true;
            }
        }

        @Override
        public void postRender(
                MinimapElementRenderInfo info,
                MultiBufferSource.BufferSource buffers,
                MultiTextureRenderTypeRendererProvider textures) {}

        @Override
        public boolean renderElement(
                ClaimView claim,
                boolean highlighted,
                boolean offscreen,
                double depth,
                float optionalScale,
                double offsetX,
                double offsetZ,
                MinimapElementRenderInfo info,
                GuiGraphics graphics,
                MultiBufferSource.BufferSource buffers) {
            if (unavailable || !Double.isFinite(context.scale) || context.scale <= 0) return false;
            graphics.pose().pushPose();
            graphics.pose().translate(offsetX, offsetZ, depth);
            graphics.pose().mulPose(Axis.ZP.rotation((float) context.angle));
            ClaimDrawing.draw(graphics, claim, context.scale, false);
            graphics.pose().popPose();
            return true;
        }
    }

    private static final class Provider extends MinimapElementRenderProvider<ClaimView, Context> {
        private Iterator<ClaimView> iterator = List.<ClaimView>of().iterator();

        @Override
        public void begin(MinimapElementRenderLocation location, Context context) {
            iterator =
                    ClientClaimState.snapshot().stream()
                            .filter(c -> c.dimension().equals(context.dimension))
                            .iterator();
        }

        @Override
        public boolean hasNext(MinimapElementRenderLocation location, Context context) {
            return iterator.hasNext();
        }

        @Override
        public ClaimView getNext(MinimapElementRenderLocation location, Context context) {
            return iterator.next();
        }

        @Override
        public void end(MinimapElementRenderLocation location, Context context) {
            iterator = List.<ClaimView>of().iterator();
        }
    }

    private static final class Reader extends MinimapElementReader<ClaimView, Context> {
        @Override
        public boolean isHidden(ClaimView c, Context context) {
            return !c.dimension().equals(context.dimension);
        }

        @Override
        public double getRenderX(ClaimView c, Context ctx, float ticks) {
            return ClaimDrawing.centerX(c);
        }

        @Override
        public double getRenderY(ClaimView c, Context ctx, float ticks) {
            return c.minY();
        }

        @Override
        public double getRenderZ(ClaimView c, Context ctx, float ticks) {
            return ClaimDrawing.centerZ(c);
        }

        @Override
        public double getCoordinateScale(ClaimView c, Context ctx, MinimapElementRenderInfo info) {
            return 1;
        }

        @Override
        public int getInteractionBoxLeft(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getInteractionBoxRight(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getInteractionBoxTop(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getInteractionBoxBottom(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getRenderBoxLeft(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getRenderBoxRight(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getRenderBoxTop(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getRenderBoxBottom(ClaimView c, Context ctx, float ticks) {
            return 0;
        }

        @Override
        public int getLeftSideLength(ClaimView c, Minecraft mc) {
            return 0;
        }

        @Override
        public String getMenuName(ClaimView c) {
            return c.label();
        }

        @Override
        public String getFilterName(ClaimView c) {
            return c.label();
        }

        @Override
        public int getMenuTextFillLeftPadding(ClaimView c) {
            return 0;
        }

        @Override
        public int getRightClickTitleBackgroundColor(ClaimView c) {
            return 0;
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return false;
        }

        @Override
        public boolean isInteractable(MinimapElementRenderLocation location, ClaimView c) {
            return false;
        }
    }
}
