// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.noopsnet.claimsmap.ClaimView;
import net.noopsnet.claimsmap.ClientClaimState;

import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

import java.util.Iterator;
import java.util.List;

/** Uses Xaero's public element pipeline, before its map controls and menus. */
public final class ClaimMapRenderer
        extends ElementRenderer<ClaimView, ClaimMapRenderer.Context, ClaimMapRenderer> {
    public static final class Context {
        String dimension = "";
        double scale = 1;
        GuiGraphics graphics;
    }

    public ClaimMapRenderer() {
        super(new Context(), new Provider(), new Reader());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public boolean shouldBeDimScaled() {
        return false;
    }

    @Override
    public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return ClaimsMapClient.visible && location == ElementRenderLocation.WORLD_MAP && !shadow;
    }

    @Override
    public void preRender(
            ElementRenderInfo info,
            MultiBufferSource.BufferSource buffers,
            MultiTextureRenderTypeRendererProvider textures,
            boolean shadow) {
        context.dimension =
                info.mapDimension == null ? "" : info.mapDimension.location().toString();
        context.scale = info.scale;
    }

    @Override
    public void postRender(
            ElementRenderInfo info,
            MultiBufferSource.BufferSource buffers,
            MultiTextureRenderTypeRendererProvider textures,
            boolean shadow) {
        if (context.graphics != null) {
            context.graphics.flush();
            context.graphics = null;
        }
    }

    @Override
    public void renderElementShadow(
            ClaimView claim,
            boolean hovered,
            float optionalScale,
            double offsetX,
            double offsetZ,
            ElementRenderInfo info,
            GuiGraphics graphics,
            MultiBufferSource.BufferSource buffers,
            MultiTextureRenderTypeRendererProvider textures) {}

    @Override
    public boolean renderElement(
            ClaimView claim,
            boolean hovered,
            double depth,
            float optionalScale,
            double offsetX,
            double offsetZ,
            ElementRenderInfo info,
            GuiGraphics graphics,
            MultiBufferSource.BufferSource buffers,
            MultiTextureRenderTypeRendererProvider textures) {
        if (!Double.isFinite(info.scale) || info.scale <= 0) return false;
        context.graphics = graphics;
        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetZ, depth);
        ClaimDrawing.draw(graphics, claim, info.scale, true);
        graphics.pose().popPose();
        return true;
    }

    private static final class Provider extends ElementRenderProvider<ClaimView, Context> {
        private Iterator<ClaimView> iterator = List.<ClaimView>of().iterator();

        @Override
        public void begin(ElementRenderLocation location, Context context) {
            iterator =
                    ClientClaimState.snapshot().stream()
                            .filter(c -> c.dimension().equals(context.dimension))
                            .iterator();
        }

        @Override
        public boolean hasNext(ElementRenderLocation location, Context context) {
            return iterator.hasNext();
        }

        @Override
        public ClaimView getNext(ElementRenderLocation location, Context context) {
            return iterator.next();
        }

        @Override
        public void end(ElementRenderLocation location, Context context) {
            iterator = List.<ClaimView>of().iterator();
        }
    }

    private static final class Reader extends ElementReader<ClaimView, Context, ClaimMapRenderer> {
        @Override
        public boolean isHidden(ClaimView c, Context context) {
            return !c.dimension().equals(context.dimension);
        }

        @Override
        public double getRenderX(ClaimView c, Context context, float ticks) {
            return ClaimDrawing.centerX(c);
        }

        @Override
        public double getRenderZ(ClaimView c, Context context, float ticks) {
            return ClaimDrawing.centerZ(c);
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
            return -halfX(c, ctx);
        }

        @Override
        public int getRenderBoxRight(ClaimView c, Context ctx, float ticks) {
            return halfX(c, ctx);
        }

        @Override
        public int getRenderBoxTop(ClaimView c, Context ctx, float ticks) {
            return -halfZ(c, ctx);
        }

        @Override
        public int getRenderBoxBottom(ClaimView c, Context ctx, float ticks) {
            return halfZ(c, ctx);
        }

        private int halfX(ClaimView c, Context ctx) {
            return (int) Math.ceil(((double) c.maxX() + 1 - c.minX()) * ctx.scale / 2) + 2;
        }

        private int halfZ(ClaimView c, Context ctx) {
            return (int) Math.ceil(((double) c.maxZ() + 1 - c.minZ()) * ctx.scale / 2) + 2;
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
        public boolean isInteractable(ElementRenderLocation location, ClaimView c) {
            return false;
        }

        // A claim may enclose the entire view while its center lies off screen.
        @Override
        public boolean isOnScreen(
                ClaimView c,
                double cameraX,
                double cameraZ,
                int width,
                int height,
                double scale,
                double optionalScale,
                double dimensionScale,
                Context context,
                float ticks) {
            double halfWidth = width / (2 * scale), halfHeight = height / (2 * scale);
            return c.maxX() + 1.0 >= cameraX - halfWidth
                    && c.minX() <= cameraX + halfWidth
                    && c.maxZ() + 1.0 >= cameraZ - halfHeight
                    && c.minZ() <= cameraZ + halfHeight;
        }
    }
}
