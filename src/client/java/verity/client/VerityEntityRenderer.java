package verity.client;

import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import verity.VerityMod;
import verity.entity.VerityEntity;

public final class VerityEntityRenderer extends MobRenderer<VerityEntity, VerityRenderState, VerityModel> {
    public VerityEntityRenderer(EntityRendererProvider.Context context) { super(context, new VerityModel(context.bakeLayer(VerityModel.LAYER)), 0.35f); }
    @Override public VerityRenderState createRenderState() { return new VerityRenderState(); }
    @Override public Identifier getTextureLocation(VerityRenderState state) { return VerityMod.id("textures/entity/verity.png"); }
    @Override public void extractRenderState(VerityEntity entity, VerityRenderState state, float delta) {
        super.extractRenderState(entity, state, delta); state.mood = entity.mood(); state.phase = entity.phase();
    }
    @Override protected int getBlockLightLevel(VerityEntity e, BlockPos pos) { return 15; }
}
