package com.example.examplemod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public class VillageMilitiaModel extends HumanoidModel<VillageMilitiaRenderer.MyRenderState> {

    public VillageMilitiaModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(VillageMilitiaRenderer.MyRenderState state) {
        //  先執行原版肢體動畫（走路、揮手、拿武器等）
        super.setupAnim(state);

        //  強制把右臂（主手）向天空高高舉起！
        if (state.isCelebrating && !state.isAggressive) {
            this.rightArm.xRot = -2.5F; // -2.5F 約為向上舉高 143 度
            this.rightArm.yRot = 0.1F;  
            this.rightArm.zRot = 0.0F;
        }
    }
}