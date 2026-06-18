package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraphdemo.block.HologramBlock;
import com.lowdragmc.kilagraphdemo.block.HologramBlockEntity;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplays;
import com.lowdragmc.kilagraphdemo.client.render.HologramPlacements;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Draws a hologram block's projected light (a cyan frustum aligned to its mounting face) plus, above it, a
 * model shaded by the displayed {@link com.lowdragmc.kilagraph.rendertype.RenderTypeGraph}. The geometry is
 * shared with {@link ServerHologramRenderer} via {@link HologramProjection}; this renderer resolves the
 * display from the client-side {@link HologramDisplays} (per-block runtime override, else the default).
 */
public class HologramRenderer implements BlockEntityRenderer<HologramBlockEntity, HologramRenderState> {

    public HologramRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public HologramRenderState createRenderState() {
        return new HologramRenderState();
    }

    @Override
    public void extractRenderState(HologramBlockEntity blockEntity, HologramRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(HologramBlock.FACING);
        Level level = blockEntity.getLevel();
        if (level != null) {
            state.posKey = GlobalPos.of(level.dimension(), blockEntity.getBlockPos());
            state.placement = HologramPlacements.resolve(state.posKey);
            state.spinDegrees = ((level.getGameTime() + partialTicks) * state.placement.spinDegreesPerTick()) % 360f;
        } else {
            state.posKey = null;
            state.placement = HologramPlacement.DEFAULT;
        }
    }

    @Override
    public void submit(HologramRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        Quaternionf facingRotation = HologramProjection.facingRotation(state.facing);
        // 1) The projector "light beam": the cyan cone, aligned to the mounting face.
        HologramProjection.submitCone(poseStack, collector, facingRotation);

        // 2) The projected hologram: a model shaded by the displayed RenderTypeGraph, floating above the
        //    projector. Skipped (cone only) while the graph fails to compile.
        HologramDisplay display = state.posKey != null
                ? HologramDisplays.resolve(state.posKey)
                : HologramDisplays.getDefault();
        RenderTypeGraphMaterial material = display.updateMaterial();
        if (material != null) {
            HologramProjection.submitModel(poseStack, collector, facingRotation, state.spinDegrees,
                    state.placement, display, material);
        }
    }

    // The projection can extend well beyond the projector block and must keep rendering when the block's own
    // chunk-section is off-screen. shouldRenderOffScreen() moves the BE to the always-evaluated global set;
    // getRenderBoundingBox() (frustum-tested there) is expanded to the projection's reach.

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(HologramBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        GlobalPos key = level == null ? null : GlobalPos.of(level.dimension(), blockEntity.getBlockPos());
        HologramPlacement placement = key == null ? HologramPlacement.DEFAULT : HologramPlacements.resolve(key);
        float radius = key == null ? com.lowdragmc.kilagraphdemo.graph.ModelSelection.DEFAULT_RADIUS
                : HologramDisplays.resolve(key).renderRadius();
        return new AABB(blockEntity.getBlockPos()).inflate(HologramProjection.cullHalfExtent(placement, radius));
    }
}
