package verity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import verity.VerityMod;
import verity.box.*;

public final class VerityBoxRenderer implements BlockEntityRenderer<VerityBoxBlockEntity, VerityBoxRenderState> {
    private final VerityBoxModel model;
    public VerityBoxRenderer(BlockEntityRendererProvider.Context context) { model = new VerityBoxModel(context.bakeLayer(VerityBoxModel.LAYER)); }
    @Override public VerityBoxRenderState createRenderState() { return new VerityBoxRenderState(); }
    @Override public void extractRenderState(VerityBoxBlockEntity box, VerityBoxRenderState state, float partialTicks,
                                            Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(box, state, breakProgress);
        state.opening = box.opening(partialTicks);
        state.facing = box.getBlockState().getValue(VerityBoxBlock.FACING);
    }
    @Override public void submit(VerityBoxRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(.5, 0, .5);
        pose.mulPose(new org.joml.Matrix4f().rotationY((float)Math.toRadians(180 - state.facing.toYRot())));
        collector.submitModel(model, state, pose, VerityMod.id("textures/entity/box.png"), state.lightCoords, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
    }
}
