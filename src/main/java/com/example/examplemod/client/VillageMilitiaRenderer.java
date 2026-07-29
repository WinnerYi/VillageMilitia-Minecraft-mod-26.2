package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.VillageMilitiaEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

public class VillageMilitiaRenderer extends HumanoidMobRenderer<VillageMilitiaEntity, VillageMilitiaRenderer.MyRenderState, HumanoidModel<VillageMilitiaRenderer.MyRenderState>> {
    
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        ExampleMod.MODID,
        "textures/entity/village_millita.png"
    );

    public VillageMilitiaRenderer(EntityRendererProvider.Context context) {
        super(
            context, 
            new VillageMilitiaModel(context.bakeLayer(ModelLayers.PLAYER)), 
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 
            0.5F
        );
        var armorModelSet = net.minecraft.client.renderer.entity.ArmorModelSet.bake(
            ModelLayers.PLAYER_ARMOR,
            context.getModelSet(),
            part -> new HumanoidModel<MyRenderState>(part)
        );

        this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<
            MyRenderState, 
            HumanoidModel<MyRenderState>, 
            HumanoidModel<MyRenderState>
        >(
            this,
            armorModelSet,
            context.getEquipmentRenderer()
        ));
    }

    public static class MyRenderState extends HumanoidRenderState {
        public boolean isCelebrating = false;
        public boolean isHoldingCrossbow = false;
        public boolean isHoldingBow = false; 
        public boolean isChargingCrossbow = false;
        public boolean isAggressive = false;
    }

    @Override
    public MyRenderState createRenderState() {
        return new MyRenderState();
    }

    @Override
    public void extractRenderState(VillageMilitiaEntity entity, MyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isCelebrating = entity.isCelebrating();
        
        // 攻擊/揮手動畫計算
        if (entity.swinging) {
            float duration = (float) entity.getCurrentSwingDuration();
            if (duration <= 0.0F) duration = 6.0F;
            
            state.attackTime = (entity.swingTime + partialTick) / duration;
            state.attackTime = net.minecraft.util.Mth.clamp(state.attackTime, 0.0F, 1.0F);
        } else {
            state.attackTime = 0.0F;
        }
        
        // 蹲下狀態修正
        state.isCrouching = entity.isShiftKeyDown() || entity.getPose() == net.minecraft.world.entity.Pose.CROUCHING;

        // 讀取主手物品與敵對狀態
        ItemStack mainHand = entity.getMainHandItem();
        state.isHoldingCrossbow = mainHand.getItem() instanceof CrossbowItem;
        state.isHoldingBow = mainHand.getItem() instanceof BowItem; // 🏹 檢查是否持弓
        state.isAggressive = entity.isAggressive() || entity.getTarget() != null;

        // ----------------------------------------------------
        // 雙手姿勢（ArmPose）分流控制
        // ----------------------------------------------------
        if (state.isHoldingCrossbow) {
            if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof CrossbowItem) {
                state.isUsingItem = true;
                state.useItemHand = entity.getUsedItemHand();
                state.ticksUsingItem = (float) entity.getTicksUsingItem();
                state.maxCrossbowChargeDuration = (float) CrossbowItem.getChargeDuration(entity.getUseItem(), entity);
            }
            boolean isCharged = CrossbowItem.isCharged(mainHand);

            
            if (state.isChargingCrossbow || entity.isUsingItem()) {
                state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            } else {
                if (isCharged || state.isAggressive || entity.getTarget() != null) {
                    state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                    state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                } else {
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                    state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                }
            }
        } 
        // 2. 🏹 弓（Bow）邏輯（關鍵新增區塊！）
        else if (state.isHoldingBow) {
            // 如果民兵正在使用物品（也就是在 AI 目錄裡執行了 startUsingItem）
            if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof BowItem) {
                state.isUsingItem = true;
                state.useItemHand = entity.getUsedItemHand();
                
                state.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                state.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else {
                // 平常沒在拉弓時：敵對狀態下拿著弓，普通時放鬆
                if (state.isAggressive) {
                    state.rightArmPose = HumanoidModel.ArmPose.ITEM;
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                } else {
                    state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                }
            }
        } 
        // 3. 近戰 / 其他武器邏輯
        else {
            if (state.attackTime > 0.0F) {
                state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            } else {
                if ((mainHand.has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON) || mainHand.has(net.minecraft.core.component.DataComponents.WEAPON)) && state.isAggressive) {
                    state.rightArmPose = HumanoidModel.ArmPose.ITEM;
                } else {
                    state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                }

                if (state.isUsingItem && state.useItemHand == net.minecraft.world.InteractionHand.OFF_HAND) {
                    state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                } else {
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                }
            }
        }
    }

    

    @Override
    public Identifier getTextureLocation(MyRenderState state) {
        return TEXTURE;
    }
}