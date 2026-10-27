package verity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import verity.VerityMod;

/** Original stepped-sphere geometry; no assets from another VERITY mod. */
public final class VerityModel extends EntityModel<VerityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(VerityMod.id("verity"), "main");
    private final ModelPart body, leftEye, rightEye, mouth, leftBrow, rightBrow;
    public VerityModel(ModelPart root) {
        super(root); body = root.getChild("body");
        leftEye = body.getChild("left_eye"); rightEye = body.getChild("right_eye");
        mouth = body.getChild("mouth"); leftBrow = body.getChild("left_brow"); rightBrow = body.getChild("right_brow");
    }
    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot(); var orb = CubeListBuilder.create().texOffs(0, 0);
        for (int y = -7; y < 7; y++) {
            float r = (float)Math.sqrt(49 - (y + 0.5f) * (y + 0.5f));
            orb.addBox(-r, y, -r, 2 * r, 1, 2 * r);
        }
        var body = root.addOrReplaceChild("body", orb, PartPose.offset(0, 15, 0));
        for (String side : new String[]{"left", "right"}) {
            float x = side.equals("left") ? 2.5f : -2.5f;
            var eye = body.addOrReplaceChild(side + "_eye", CubeListBuilder.create().texOffs(64, 0)
                    .addBox(-1.5f, -2, -0.5f, 3, 4, 1), PartPose.offset(x, -1.5f, -7));
            eye.addOrReplaceChild("pupil", CubeListBuilder.create().texOffs(64, 16)
                    .addBox(-0.6f, -1.1f, -0.5f, 1.2f, 2.2f, 0.5f), PartPose.offset(0, 0, -0.5f));
            body.addOrReplaceChild(side + "_brow", CubeListBuilder.create().texOffs(64, 16)
                    .addBox(-1.7f, -0.3f, -0.5f, 3.4f, 0.6f, 1), PartPose.offset(x, -4.3f, -6.8f));
        }
        body.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(64, 32)
                .addBox(-2, -0.35f, -0.4f, 4, 0.7f, 0.8f), PartPose.offset(0, 2.9f, -6.9f));
        return LayerDefinition.create(mesh, 128, 64);
    }
    @Override public void setupAnim(VerityRenderState s) {
        super.setupAnim(s);
        body.y = 15 + (float)Math.sin(s.ageInTicks * 0.08) * 0.65f;
        body.xRot = s.xRot * ((float)Math.PI / 180f) * 0.35f;
        float blink = s.ageInTicks % 90 < 3 ? 0.12f : 1;
        leftEye.yScale = rightEye.yScale = blink;
        boolean angry = s.mood < 20;
        leftBrow.zRot = angry ? -0.4f : s.mood < 40 ? -0.15f : 0.1f;
        rightBrow.zRot = -leftBrow.zRot;
        mouth.yScale = s.mood >= 60 ? 1.5f : 0.7f;
        if (s.phase == 3) body.xScale = body.yScale = body.zScale = 1.5f;
        // Mouth is an emotion pose only. No fabricated audio/lip synchronization.
    }
}
