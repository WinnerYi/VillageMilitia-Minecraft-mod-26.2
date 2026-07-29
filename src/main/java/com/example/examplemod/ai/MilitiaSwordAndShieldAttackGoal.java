package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import java.util.EnumSet;

public class MilitiaSwordAndShieldAttackGoal extends Goal {
    private double strafeDirection = 0.0D;
    private final VillageMilitiaEntity mob;
    private double attackCooldown = 0.0D;
    private double shieldTicks = 0.0D;
    private int retreatTicks = 0;

    public MilitiaSwordAndShieldAttackGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.mob.getMainHandItem().getItem() instanceof CrossbowItem) {
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.shieldTicks = 0;
        this.retreatTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.stopUsingItem();
        this.mob.getNavigation().stop();
        // 如果下馬了，順便確保馬也停下
        if (this.mob.getVehicle() instanceof Mob vehicleMob) {
            vehicleMob.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        // 1. 視線鎖定（無論如何，眼睛盯著怪）
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distanceSq = this.mob.distanceToSqr(target);
        double attackReach = this.getAttackReachSqr(target);

        if (this.attackCooldown > 0) {
            this.attackCooldown = Math.max(0, this.attackCooldown - 1.0D);
        }

       
        boolean isRiding = this.mob.isPassenger() && this.mob.getVehicle() instanceof net.minecraft.world.entity.PathfinderMob;

        //  戰鬥拉扯狀態
        if (this.retreatTicks > 0) {
            this.retreatTicks--;

            if (isRiding) {
                //  騎兵撤退：戰馬調頭全速奔跑（此時不舉盾，專心控馬）
                net.minecraft.world.entity.PathfinderMob vehicleMob = (net.minecraft.world.entity.PathfinderMob) this.mob.getVehicle();
                net.minecraft.world.phys.Vec3 retreatPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(
                    vehicleMob, 8, 4, target.position()
                );
                if (retreatPos != null) {
                    vehicleMob.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, 1.4D);
                }
            } else {
                // 步兵撤退：維持你原本的物理斜向推力
                this.mob.getNavigation().stop();
                double deltaX = this.mob.getX() - target.getX();
                double deltaZ = this.mob.getZ() - target.getZ();
                double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

                if (distance > 0) {
                    double backX = deltaX / distance;
                    double backZ = deltaZ / distance;
                    double strafeX = backZ;
                    double strafeZ = -backX;

                    double backSpeed = 0.09D;    
                    double strafeSpeed = 0.1D;  

                    double vecX = (backX * backSpeed) + (strafeX * strafeSpeed * this.strafeDirection);
                    double vecZ = (backZ * backSpeed) + (strafeZ * strafeSpeed * this.strafeDirection);
                    
                    this.mob.setDeltaMovement(vecX, this.mob.getDeltaMovement().y, vecZ);
                    this.mob.hurtMarked = true;
                }

                // 🏃‍♂️ 步兵步行的舊邏輯：後退快結束時舉盾準備接階段 2
                if (this.retreatTicks <= 3 && !this.mob.isUsingItem() && this.mob.getOffhandItem().is(Items.SHIELD)) {
                    this.mob.startUsingItem(InteractionHand.OFF_HAND);
                    this.shieldTicks = 10 + this.mob.getRandom().nextInt(20);; 
                }
            }

        } else if (this.shieldTicks > 0) {
            // =================【 舉盾階段 】=================
            this.shieldTicks--;
            
            if (isRiding) {
                net.minecraft.world.entity.PathfinderMob vehicleMob = (net.minecraft.world.entity.PathfinderMob) this.mob.getVehicle();
                vehicleMob.getNavigation().moveTo(target, 0.3D);
            } else {
               
                this.mob.getNavigation().moveTo(target, 0.2D);
            }
            
            if (this.shieldTicks == 0) {
                this.mob.stopUsingItem(); 
            }

        } else {
            if (isRiding) {
                net.minecraft.world.entity.PathfinderMob vehicleMob = (net.minecraft.world.entity.PathfinderMob) this.mob.getVehicle();
                vehicleMob.getNavigation().moveTo(target, 1.8D);
            } else {
                // 🏃‍♂️ 步兵衝鋒
                this.mob.getNavigation().moveTo(target, 0.85D);
            }
            
            // 進入攻擊距離且冷卻完畢
            if (distanceSq <= attackReach && this.attackCooldown <= 0 && this.mob.getSensing().hasLineOfSight(target)) {
                this.mob.stopUsingItem();
                this.mob.swing(InteractionHand.MAIN_HAND, true);
                if (this.mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.broadcastEntityEvent(this.mob, (byte) 4);
                    this.mob.doHurtTarget(serverLevel, target);
                }
                
                // 核心冷卻觸發
                if (this.mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)) {
                    this.attackCooldown = 20; // 攻擊冷卻 1 秒
                } else if (this.mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.WEAPON)) {
                    this.attackCooldown = 12.5D; // 攻擊冷卻 1 秒
                }
                

                if (isRiding) {
                    if (this.mob.getRandom().nextBoolean()) {
                        // 戰術 A：只撤退，不舉盾
                        this.retreatTicks = 15 + this.mob.getRandom().nextInt(10); ; 
                        this.shieldTicks = 0;
                    } else {
                        // 戰術 B：只舉盾，不撤退
                        if (this.mob.getOffhandItem().is(Items.SHIELD)) {
                            this.mob.startUsingItem(InteractionHand.OFF_HAND);
                            this.shieldTicks = 20; // 舉盾 1 秒
                        }
                        this.retreatTicks = 0;
                    }
                } else {
                    // 🏃‍♂️ 步兵維持原樣
                    if (this.mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)) {
                        // 長槍兵戳完後只進行短暫的微調（3-7 tick），保留更多衝鋒衝勁
                        this.retreatTicks = 3 + this.mob.getRandom().nextInt(25);
                    } else {
                        // 鐵劍兵維持原有的長時間撤退與舉盾準備
                        this.retreatTicks = 1 + this.mob.getRandom().nextInt(20);
                    }
                    this.shieldTicks = 0;
                    this.strafeDirection = this.mob.getRandom().nextBoolean() ? 0.7D : -0.7D;
                }
            }
        }
    }
    private double getAttackReachSqr(LivingEntity target) {
       
        if (this.mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)) {
            return  12.0D; 
        }
        return 7.5D; 
    }
}