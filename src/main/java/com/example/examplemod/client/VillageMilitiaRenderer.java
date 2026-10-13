package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.VillageMilitiaEntity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.util.Mth;
import net.minecraft.tags.ItemTags;


public class VillageMilitiaRenderer extends MobRenderer<
    VillageMilitiaEntity,
    VillageMilitiaRenderer.MyRenderState,
    IllagerModel<VillageMilitiaRenderer.MyRenderState>
> {
    
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        ExampleMod.MODID,
        "textures/entity/villager_militiaa.png"
    );

    public VillageMilitiaRenderer(EntityRendererProvider.Context context) {
        super(
            context, 
            new VillageMilitiaModel(context.bakeLayer(ModelLayers.VINDICATOR)),
            0.5F
        );
        this.addLayer(new MilitiaItemInHandLayer(this));
        this.addLayer(new MilitiaBackWeaponLayer(this));
        this.addLayer(new MilitiaArmorLayer(this, context));
    }

    public static class MyRenderState extends IllagerRenderState {
        public boolean isCelebrating = false;
        public boolean isHoldingCrossbow = false;
        public boolean isHoldingBow = false; 
        public boolean isChargingCrossbow = false;
        public boolean isBlocking = false;
        public boolean isHoldingSpear = false;
        public boolean isHoldingMeleeWeapon = false;
        public boolean isEmptyHandAttack = false;
        public boolean isRightHandWeaponAttack = false;
        public boolean isInCombat = false;
        public boolean hasTarget = false;
        public boolean shouldCrossArms = false;
        public boolean renderMeleeWeaponOnBack = false;
        public final net.minecraft.client.renderer.item.ItemStackRenderState backWeaponState =
            new net.minecraft.client.renderer.item.ItemStackRenderState();
    }

    @Override
    public MyRenderState createRenderState() {
        return new MyRenderState();
    }

    @Override
    public void extractRenderState(VillageMilitiaEntity entity, MyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(
            entity,
            state,
            partialTick,
            this.itemModelResolver
        );
        state.isCelebrating = entity.isCelebrating();
        
     
        state.isRiding = entity.isPassenger();
        state.mainArm = HumanoidArm.RIGHT;
        state.isCrouching = !state.isRiding
            && (entity.isShiftKeyDown() || entity.getPose() == Pose.CROUCHING);
        state.isChargingCrossbow = entity.isChargingCrossbow();
        state.isBlocking = entity.isUsingItem()
            && entity.getUsedItemHand() == InteractionHand.OFF_HAND
            && entity.getOffhandItem().getItem() instanceof ShieldItem;

        // 讀取主手物品與敵對狀態
        ItemStack mainHand = entity.getMainHandItem();
        state.isHoldingCrossbow = mainHand.getItem() instanceof CrossbowItem;
        state.isHoldingBow = mainHand.getItem() instanceof BowItem; 
        state.isHoldingSpear = mainHand.has(DataComponents.KINETIC_WEAPON);
        state.isHoldingMeleeWeapon = mainHand.has(DataComponents.WEAPON);
        state.isEmptyHandAttack = mainHand.isEmpty() && state.swingAnimation > 0.0F;
        state.isRightHandWeaponAttack = state.swingAnimation > 0.0F
            && (state.isHoldingMeleeWeapon || state.isHoldingSpear);
        state.hasTarget = entity.getTarget() != null && entity.getTarget().isAlive();
        state.isAggressive = entity.isAggressive() || state.hasTarget;
        state.isInCombat = state.isAggressive || state.hasTarget
            || entity.isMilitiaSwinging();
        state.renderMeleeWeaponOnBack = (mainHand.is(ItemTags.SWORDS)
            || mainHand.is(ItemTags.AXES)
            || mainHand.is(Items.MACE))
            && !state.isAggressive
            && !state.hasTarget
            && !state.isRiding
            && !entity.isMilitiaSwinging();
        if (state.renderMeleeWeaponOnBack) {
            this.itemModelResolver.updateForLiving(
                state.backWeaponState,
                mainHand,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                entity
            );
            state.rightHandItemState.clear();
        } else {
            state.backWeaponState.clear();
        }
        state.shouldCrossArms = !state.hasTarget
            && !state.isAggressive
            && !entity.isMilitiaSwinging()
            && !state.isRiding
            && !state.isHoldingSpear
            && !state.isHoldingCrossbow
            && !state.isHoldingBow;

        // ----------------------------------------------------
        // 雙手姿勢（ArmPose）分流控制
        // ----------------------------------------------------
        if (state.isHoldingCrossbow) {
            boolean isChargingCrossbow = entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                && entity.getUseItem().getItem() instanceof CrossbowItem;

            if (isChargingCrossbow) {
                state.isUsingItem = true;
                state.useItemHand = entity.getUsedItemHand();
                state.ticksUsingItem = (float) entity.getTicksUsingItem();
                state.maxCrossbowChargeDuration = CrossbowItem.getChargeDuration(entity.getUseItem(), entity);
            }
            boolean isCharged = CrossbowItem.isCharged(mainHand);

            if (state.isChargingCrossbow || isChargingCrossbow) {
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
            state.armPose = state.isChargingCrossbow || isChargingCrossbow
                ? AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE
                : AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
        } 
        
        else if (state.isHoldingBow) {
            boolean isDrawingBow = entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                && entity.getUseItem().getItem() instanceof BowItem;

            if (isDrawingBow) {
                state.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                state.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else {
                if (state.isAggressive) {
                    state.rightArmPose = HumanoidModel.ArmPose.ITEM;
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                } else {
                    state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                }
            } 
            state.armPose = isDrawingBow
                ? AbstractIllager.IllagerArmPose.BOW_AND_ARROW
                : AbstractIllager.IllagerArmPose.NEUTRAL;
        } 

        else if (mainHand.has(DataComponents.KINETIC_WEAPON)) {
            
           
            if (entity.isUsingItem() && entity.getUseItem().has(DataComponents.KINETIC_WEAPON)) {
                state.isUsingItem = true;
                state.useItemHand = entity.getUsedItemHand();
                
                state.rightArmPose = HumanoidModel.ArmPose.SPEAR; 
                state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            } 
            // 敵對/警戒中
            else if (state.isAggressive) {
                state.rightArmPose = HumanoidModel.ArmPose.ITEM;
                state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            } 
            // 平時放鬆
            else {
                state.rightArmPose = HumanoidModel.ArmPose.SPEAR;
            }
            state.armPose = state.swingAnimation > 0.0F
                ? AbstractIllager.IllagerArmPose.ATTACKING
                : AbstractIllager.IllagerArmPose.NEUTRAL;
        }
    
        else {
            if (state.swingAnimation > 0.0F) {
                state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                state.armPose = AbstractIllager.IllagerArmPose.ATTACKING;
            } else {
                if ((mainHand.has(DataComponents.KINETIC_WEAPON) || mainHand.has(DataComponents.WEAPON)) && state.isAggressive) {
                    state.rightArmPose = HumanoidModel.ArmPose.ITEM;
                } else {
                    state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                }

                if (state.isUsingItem && state.useItemHand == net.minecraft.world.InteractionHand.OFF_HAND) {
                    state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                } else {
                    state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                }
                state.armPose = state.shouldCrossArms
                    ? AbstractIllager.IllagerArmPose.CROSSED
                    : AbstractIllager.IllagerArmPose.NEUTRAL;
            }
        }

        if (!state.shouldCrossArms
            && state.armPose == AbstractIllager.IllagerArmPose.CROSSED) {
            state.armPose = AbstractIllager.IllagerArmPose.NEUTRAL;
        }
        if (state.hasTarget && state.armPose == AbstractIllager.IllagerArmPose.CROSSED) {
            state.armPose = AbstractIllager.IllagerArmPose.NEUTRAL;
        }
    }

    

    @Override
    public Identifier getTextureLocation(MyRenderState state) {
        return TEXTURE;
    }

    private static final class MilitiaArmorLayer
        extends RenderLayer<MyRenderState, IllagerModel<MyRenderState>> {
        private final ArmorModelSet<MilitiaArmorModel> armorModels;
        private final EquipmentLayerRenderer equipmentRenderer;

        private MilitiaArmorLayer(
            VillageMilitiaRenderer renderer,
            EntityRendererProvider.Context context
        ) {
            super(renderer);
            this.armorModels = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR,
                context.getModelSet(),
                MilitiaArmorModel::new
            );
            this.equipmentRenderer = context.getEquipmentRenderer();
        }

        @Override
        public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            MyRenderState state,
            float yRot,
            float xRot
        ) {
            renderArmor(poseStack, collector, packedLight, state, EquipmentSlot.CHEST, state.chestEquipment);
            renderArmor(poseStack, collector, packedLight, state, EquipmentSlot.LEGS, state.legsEquipment);
            renderArmor(poseStack, collector, packedLight, state, EquipmentSlot.FEET, state.feetEquipment);
            renderArmor(poseStack, collector, packedLight, state, EquipmentSlot.HEAD, state.headEquipment);
        }

        private void renderArmor(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            MyRenderState state,
            EquipmentSlot slot,
            ItemStack stack
        ) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.assetId().isEmpty()) {
                return;
            }

            EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                : EquipmentClientInfo.LayerType.HUMANOID;
            MilitiaArmorModel armorModel = this.armorModels.get(slot);
            armorModel.setupAnim(state);
            ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> assetId =
                equippable.assetId().orElseThrow();
            this.equipmentRenderer.renderLayers(
                layerType,
                assetId,
                armorModel,
                state,
                stack,
                poseStack,
                collector,
                packedLight,
                state.outlineColor
            );
        }
    }

    private static final class MilitiaArmorModel extends HumanoidModel<MyRenderState> {
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        private MilitiaArmorModel(ModelPart root) {
            super(root);
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        @Override
        public void setupAnim(MyRenderState state) {
            super.setupAnim(state);

            if (state.isRiding) {
                this.rightLeg.xRot = -1.4137167F;
                this.rightLeg.yRot = 0.31415927F;
                this.rightLeg.zRot = 0.07853982F;
                this.leftLeg.xRot = -1.4137167F;
                this.leftLeg.yRot = -0.31415927F;
                this.leftLeg.zRot = -0.07853982F;
            } else {
                float walkSpeed = state.walkAnimationSpeed;
                float walkPos = state.walkAnimationPos;
                this.rightLeg.xRot = Mth.cos(walkPos * 0.6662F) * 1.4F * walkSpeed * 0.5F;
                this.leftLeg.xRot = Mth.cos(walkPos * 0.6662F + (float) Math.PI)
                    * 1.4F * walkSpeed * 0.5F;
                this.rightLeg.yRot = 0.0F;
                this.rightLeg.zRot = 0.0F;
                this.leftLeg.yRot = 0.0F;
                this.leftLeg.zRot = 0.0F;
            }
        }
    }

    private static final class MilitiaItemInHandLayer
        extends ItemInHandLayer<MyRenderState, IllagerModel<MyRenderState>> {
        private MilitiaItemInHandLayer(VillageMilitiaRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            MyRenderState state,
            float yRot,
            float xRot
        ) {
            if (!state.shouldCrossArms) {
                super.submit(poseStack, collector, packedLight, state, yRot, xRot);
            }
        }
    }

    private static final class MilitiaBackWeaponLayer
        extends RenderLayer<MyRenderState, IllagerModel<MyRenderState>> {
        private MilitiaBackWeaponLayer(VillageMilitiaRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            MyRenderState state,
            float yRot,
            float xRot
        ) {
            if (!state.renderMeleeWeaponOnBack || state.backWeaponState.isEmpty()) {
                return;
            }

            poseStack.pushPose();
            poseStack.translate(0.0F, 0.15F, 0.3F);
            poseStack.rotateDegrees(Axis.YP, 90.0F);
            poseStack.scale(0.8F, 0.8F, 0.8F);
            state.backWeaponState.submit(
                poseStack,
                collector,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
            );
            poseStack.popPose();
        }
    }
}