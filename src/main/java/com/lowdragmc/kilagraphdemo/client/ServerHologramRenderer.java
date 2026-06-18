package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraphdemo.block.HologramBlock;
import com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.render.ServerHologramDisplays;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renderer for the {@link ServerHologramBlockEntity}. Draws the same cyan cone as the regular hologram, but
 * the projected model comes from the <b>server-synced</b> work uid resolved through
 * {@link ServerHologramDisplays} (which lazily downloads it on first render). While the work is still being
 * pulled, it draws a billboarded {@code NN%} progress readout where the model will appear.
 */
public class ServerHologramRenderer
        implements BlockEntityRenderer<ServerHologramBlockEntity, ServerHologramRenderState> {

    private static final float TEXT_SCALE = 0.025f;
    private static final int TEXT_COLOR = 0xFF66FFFF; // bright cyan

    public ServerHologramRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ServerHologramRenderState createRenderState() {
        return new ServerHologramRenderState();
    }

    @Override
    public void extractRenderState(ServerHologramBlockEntity blockEntity, ServerHologramRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(HologramBlock.FACING);
        state.placement = blockEntity.getPlacement();
        Level level = blockEntity.getLevel();
        state.spinDegrees = level == null ? 0f
                : ((level.getGameTime() + partialTicks) * state.placement.spinDegreesPerTick()) % 360f;

        String uid = blockEntity.getDisplayedWorkUid();
        state.hasUid = uid != null && !uid.isEmpty();
        // Resolve (and lazily pull) the work this block displays.
        ServerHologramDisplays.Resolved resolved = ServerHologramDisplays.resolve(uid);
        state.display = resolved.display();
        state.progress = resolved.progress();
    }

    @Override
    public void submit(ServerHologramRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        Quaternionf facingRotation = HologramProjection.facingRotation(state.facing);
        HologramProjection.submitCone(poseStack, collector, facingRotation);

        HologramDisplay display = state.display;
        if (display != null) {
            RenderTypeGraphMaterial material = display.updateMaterial();
            if (material != null) {
                HologramProjection.submitModel(poseStack, collector, facingRotation, state.spinDegrees,
                        state.placement, display, material);
                return;
            }
        }
        // No display yet but a work is assigned: show the download progress as billboarded text.
        if (state.hasUid) {
            submitProgressText(state, poseStack, collector, camera);
        }
    }

    // The projection can extend well beyond the projector block; keep it rendering when the block's section is
    // off-screen (shouldRenderOffScreen → global set) and let the expanded box do the frustum culling.

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ServerHologramBlockEntity blockEntity) {
        HologramPlacement placement = blockEntity.getPlacement();
        ServerHologramDisplays.Resolved resolved = ServerHologramDisplays.resolve(blockEntity.getDisplayedWorkUid());
        float radius = resolved.display() != null ? resolved.display().renderRadius() : ModelSelection.DEFAULT_RADIUS;
        return new AABB(blockEntity.getBlockPos()).inflate(HologramProjection.cullHalfExtent(placement, radius));
    }

    private void submitProgressText(ServerHologramRenderState state, PoseStack poseStack,
                                    SubmitNodeCollector collector, CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        int pct = Math.max(0, Math.min(100, Math.round(state.progress * 100f)));
        Component text = Component.literal(pct + "%");
        float halfWidth = font.width(text) / 2f;

        // Anchor at the model position (offset along the facing axis), then billboard toward the camera.
        Vector3f dir = state.facing.step();
        poseStack.pushPose();
        poseStack.translate(0.5f + dir.x() * HologramProjection.MODEL_OFFSET,
                0.5f + dir.y() * HologramProjection.MODEL_OFFSET,
                0.5f + dir.z() * HologramProjection.MODEL_OFFSET);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE); // flip into GUI text convention, facing camera
        collector.submitText(poseStack, -halfWidth, -font.lineHeight / 2f, text.getVisualOrderText(),
                true, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, TEXT_COLOR, 0, 0);
        poseStack.popPose();
    }
}
