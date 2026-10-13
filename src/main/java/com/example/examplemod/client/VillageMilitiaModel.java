package com.example.examplemod.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;

public class VillageMilitiaModel extends IllagerModel<VillageMilitiaRenderer.MyRenderState> {
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart crossedArms;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public VillageMilitiaModel(ModelPart root) {
        super(root);
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.crossedArms = root.getChild("arms");
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    @Override
    public void setupAnim(VillageMilitiaRenderer.MyRenderState state) {
        if (state.swingAnimation <= 0.0F
            && !state.shouldCrossArms
            && state.armPose == net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose.CROSSED) {
            state.armPose = net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose.NEUTRAL;
        }
        super.setupAnim(state);

        if ((state.isEmptyHandAttack || state.isRightHandWeaponAttack) && !state.isRiding) {
            this.leftArm.xRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = 0.0F;
        }

        if (state.isCrouching && !state.isRiding) {
            this.body.xRot = 0.5F;
            this.rightArm.xRot += 0.4F;
            this.leftArm.xRot += 0.4F;
            this.rightLeg.z += 4.0F;
            this.leftLeg.z += 4.0F;
            this.head.y += 4.2F;
            this.body.y += 3.2F;
            this.rightArm.y += 3.2F;
            this.leftArm.y += 3.2F;
        }

        this.crossedArms.visible = state.shouldCrossArms;
        this.rightArm.visible = !state.shouldCrossArms;
        this.leftArm.visible = !state.shouldCrossArms;

        if (state.isBlocking) {
            this.leftArm.xRot = -1.25F;
            this.leftArm.yRot = 0.35F;
            this.leftArm.zRot = -0.08F;
        }

        if (state.isHoldingSpear && state.swingAnimation <= 0.0F) {
            this.rightArm.xRot = state.isUsingItem ? -1.15F : -0.85F;
            this.rightArm.yRot = -0.35F;
            this.rightArm.zRot = 0.0F;
        }

        if (state.isCelebrating && !state.isAggressive) {
            this.rightArm.xRot = -2.5F; // -2.5F 約為向上舉高 143 度
            this.rightArm.yRot = 0.1F;  
            this.rightArm.zRot = 0.0F;
        }
    }
}