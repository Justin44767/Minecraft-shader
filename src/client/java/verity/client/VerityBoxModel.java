package verity.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import verity.VerityMod;

/** Original wooden shipping case, reinforced corners, gold lock and hinged lid. */
public final class VerityBoxModel extends Model<VerityBoxRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(VerityMod.id("sealed_box"), "main");
    private final ModelPart lid, seal;
    public VerityBoxModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        lid = root.getChild("lid"); seal = root.getChild("seal");
    }
    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7, 0, -7, 14, 2, 14).addBox(-7, 2, -7, 14, 7, 1)
                .addBox(-7, 2, 6, 14, 7, 1).addBox(-7, 2, -6, 1, 7, 12).addBox(6, 2, -6, 1, 7, 12), PartPose.ZERO);
        var lid = root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-7, 0, -14, 14, 3, 14), PartPose.offset(0, 9, 7));
        lid.addOrReplaceChild("rim", CubeListBuilder.create().texOffs(64, 0).addBox(-7.2f, -.3f, -14.2f, 14.4f, .6f, 14.4f), PartPose.ZERO);
        root.addOrReplaceChild("corners", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-7.1f, 0, -7.1f, 1.4f, 9, 1.4f).addBox(5.7f, 0, -7.1f, 1.4f, 9, 1.4f)
                .addBox(-7.1f, 0, 5.7f, 1.4f, 9, 1.4f).addBox(5.7f, 0, 5.7f, 1.4f, 9, 1.4f), PartPose.ZERO);
        root.addOrReplaceChild("seal", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-2, 5, -7.6f, 4, 6, 1), PartPose.ZERO);
        root.addOrReplaceChild("inner_glow", CubeListBuilder.create().texOffs(64, 32)
                .addBox(-5.7f, 2.1f, -5.7f, 11.4f, .1f, 11.4f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }
    @Override public void setupAnim(VerityBoxRenderState state) {
        super.setupAnim(state);
        lid.xRot = state.opening * 1.95f;
        seal.visible = state.opening < .15;
    }
}
