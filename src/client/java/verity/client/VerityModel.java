package verity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import verity.VerityMod;

/** Original stepped-sphere geometry; no assets from another VERITY mod. */
public final class VerityModel extends EntityModel<VerityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(VerityMod.id("verity"), "main");
    private final ModelPart body, leftEye, rightEye, mouth, leftBrow, rightBrow;
    private final ModelPart leftHorn, rightHorn, leftArm, rightArm, leftSmile, rightSmile;
    public VerityModel(ModelPart root) {
        super(root); body = root.getChild("body");
        leftEye = body.getChild("left_eye"); rightEye = body.getChild("right_eye");
        mouth = body.getChild("mouth"); leftBrow = body.getChild("left_brow"); rightBrow = body.getChild("right_brow");
        leftHorn = body.getChild("left_horn"); rightHorn = body.getChild("right_horn");
        leftArm = body.getChild("left_arm"); rightArm = body.getChild("right_arm");
        leftSmile = body.getChild("left_smile"); rightSmile = body.getChild("right_smile");
    }
    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot(); var orb = CubeListBuilder.create().texOffs(0, 0);
        for (int x = -7; x < 7; x += 2) {
            for (int z = -7; z < 7; z += 2) {
                float square = 49 - (x + 1) * (x + 1) - (z + 1) * (z + 1);
                if (square <= 0) continue;
                float h = (float)Math.sqrt(square);
                orb.addBox(x, -h, z, 2, h * 2, 2);
            }
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
            body.addOrReplaceChild(side + "_smile", CubeListBuilder.create().texOffs(64, 32)
                    .addBox(-.4f, 0, -.4f, .8f, 1.2f, .8f), PartPose.offset(x > 0 ? 2.3f : -2.3f, 1.9f, -6.9f));
            body.addOrReplaceChild(side + "_horn", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1, -5, -1, 2, 5, 2).addBox(-.55f, -8, -.55f, 1.1f, 3, 1.1f),
                    PartPose.offset(x > 0 ? 4.7f : -4.7f, -5, 0));
            body.addOrReplaceChild(side + "_arm", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-.8f, 0, -.8f, 1.6f, 11, 1.6f)
                    .addBox(-1, 11, -1.2f, .45f, 3, .45f).addBox(-.2f, 11, -1.2f, .45f, 3.6f, .45f)
                    .addBox(.6f, 11, -1.2f, .45f, 3, .45f), PartPose.offset(x > 0 ? 6.7f : -6.7f, 1, 0));
        }
        body.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(64, 32)
                .addBox(-2, -0.35f, -0.4f, 4, 0.7f, 0.8f), PartPose.offset(0, 2.9f, -6.9f));
        return LayerDefinition.create(mesh, 128, 64);
    }
    @Override public void setupAnim(VerityRenderState s) {
        super.setupAnim(s);
        body.y = 15 + (float)Math.sin(s.ageInTicks * 0.08) * 0.65f;
        body.xRot = s.xRot * ((float)Math.PI / 180f) * 0.35f;
        body.yRot = s.yRot * ((float)Math.PI / 180f) * .7f;
        float blink = s.ageInTicks % 90 < 3 ? 0.12f : 1;
        leftEye.yScale = rightEye.yScale = blink;
        boolean angry = s.mood < 20;
        leftBrow.zRot = angry ? -0.4f : s.mood < 40 ? -0.15f : 0.1f;
        rightBrow.zRot = -leftBrow.zRot;
        mouth.yScale = s.mood >= 60 ? 1.5f : 0.7f;
        leftSmile.visible = rightSmile.visible = s.mood >= 60 || s.mood < 40;
        leftSmile.y = rightSmile.y = s.mood >= 60 ? 1.9f : 3.2f;
        boolean demon = s.phase == 3;
        leftHorn.visible = rightHorn.visible = leftArm.visible = rightArm.visible = demon;
        if (demon) {
            float t = s.transformation;
            body.xScale = body.zScale = 1 + .5f * t; body.yScale = 1 + .65f * t;
            leftHorn.yScale = rightHorn.yScale = Math.max(.01f, t);
            leftHorn.zRot = -.35f; rightHorn.zRot = .35f;
            leftArm.xRot = (float)Math.cos(s.ageInTicks * .16) * .25f - .45f;
            rightArm.xRot = -(float)Math.cos(s.ageInTicks * .16) * .25f - .45f;
            leftArm.zRot = -.3f; rightArm.zRot = .3f; mouth.xScale = 1.5f;
            body.x += (float)Math.sin(s.ageInTicks * 3) * (1 - t) * .8f;
        }
        if (s.speaking) mouth.yScale = .3f + s.speechAmplitude * 5;
    }
}
