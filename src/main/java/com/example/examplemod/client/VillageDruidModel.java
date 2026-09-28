package com.example.examplemod.client;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class VillageDruidModel extends HumanoidModel<VillageDruidRenderer.MyRenderState> {
    public VillageDruidModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(VillageDruidRenderer.MyRenderState state) {
        super.setupAnim(state);

        if (!state.isCasting) {
            return;
        }

        float pulse = Mth.sin(state.castProgress * Mth.PI);

        switch (state.spellType) {
            case 1 -> setupNatureBurst(pulse);
            case 2 -> setupHealingLight(pulse);
            default -> setupThornBinding(pulse);
        }
    }

    private void setupThornBinding(float pulse) {
        this.rightArm.xRot = -1.3F - pulse * 0.25F;
        this.rightArm.yRot = -0.18F;
        this.rightArm.zRot = 0.05F;

        this.leftArm.xRot = -0.35F;
        this.leftArm.yRot = 0.35F;
        this.leftArm.zRot = -0.2F;
    }

    private void setupNatureBurst(float pulse) {
        float armPitch = -1.35F - pulse * 0.45F;

        this.rightArm.xRot = armPitch;
        this.leftArm.xRot = armPitch;
        this.rightArm.yRot = -0.4F - pulse * 0.15F;
        this.leftArm.yRot = 0.4F + pulse * 0.15F;
        this.rightArm.zRot = 0.12F;
        this.leftArm.zRot = -0.12F;
    }

    private void setupHealingLight(float pulse) {
        this.rightArm.xRot = -0.75F - pulse * 0.15F;
        this.leftArm.xRot = -0.75F - pulse * 0.15F;
        this.rightArm.yRot = -0.55F;
        this.leftArm.yRot = 0.55F;
        this.rightArm.zRot = 0.18F;
        this.leftArm.zRot = -0.18F;
    }

    
}