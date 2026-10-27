package verity.client;

import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import verity.VerityMod;
import verity.entity.VerityEntity;

public final class VerityEntityRenderer extends MobRenderer<VerityEntity, VerityRenderState, VerityModel> {
    public VerityEntityRenderer(EntityRendererProvider.Context context) { super(context, new VerityModel(context.bakeLayer(VerityModel.LAYER)), 0.35f); }
    @Override public VerityRenderState createRenderState() { return new VerityRenderState(); }
    @Override public Identifier getTextureLocation(VerityRenderState state) {
        return VerityMod.id(state.phase >= 2 || state.glitch ? "textures/entity/verity_dark.png" : "textures/entity/verity.png");
    }
    @Override public void extractRenderState(VerityEntity entity, VerityRenderState state, float delta) {
        super.extractRenderState(entity, state, delta); state.mood = entity.mood(); state.phase = entity.phase();
        state.reveal = Math.clamp(1 - (entity.revealTicks() - delta) / 40f, 0, 1);
        state.speaking = entity.speechLeft() > 0 && entity.speechClip() > 0;
        state.glitch = entity.glitchTicks() > 0;
        state.transformation = Math.clamp(1 - (entity.transformTicks() - delta) / 60f, 0, 1);
        state.speechAmplitude = state.speaking ? VeritySpeechEnvelope.amplitude(entity.speechClip(),
                verity.voice.VerityVoiceClips.clip(entity.speechClip()).ticks() - entity.speechLeft() + delta) : 0;
    }
    @Override protected void scale(VerityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose) {
        float factor = .15f + .85f * (float)Math.sin(state.reveal * Math.PI / 2);
        pose.scale(factor, factor, factor);
    }
    @Override protected int getBlockLightLevel(VerityEntity e, BlockPos pos) { return 15; }
}
