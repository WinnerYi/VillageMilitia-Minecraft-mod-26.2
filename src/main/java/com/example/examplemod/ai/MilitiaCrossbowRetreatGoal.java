package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class MilitiaCrossbowRetreatGoal extends Goal {
    private final VillageMilitiaEntity mob;
    
    // --- 隨機與尋路參數 ---
    private int pathUpdateTimer = 0;
    private double moveSpeedFactor = 1.0D;     
    private int shootDelay = 0;               
    private int chargeDelay = 0;              
    private int strafeTimer = 0;              
    private int strafeInterval = 20;          

    public MilitiaCrossbowRetreatGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() 
            && this.mob.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    @Override
    public void start() {
        this.resetRandomParams();
        this.pathUpdateTimer = 0;
    }

    @Override
    public void stop() {
        this.mob.setChargingCrossbow(false);
        this.mob.stopUsingItem();
        this.mob.setShiftKeyDown(false); 
        this.mob.getNavigation().stop();
        if (this.mob.getVehicle() instanceof PathfinderMob vehicleMob) {
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

        // 永遠看向目標
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack crossbow = this.mob.getMainHandItem();
        boolean isCharged = CrossbowItem.isCharged(crossbow);
        boolean isRiding = this.mob.isPassenger() && this.mob.getVehicle() instanceof PathfinderMob;
        PathfinderMob movingEntity = isRiding ? (PathfinderMob) this.mob.getVehicle() : this.mob;

        this.strafeTimer++;
        if (this.strafeTimer >= this.strafeInterval) {
            this.strafeTimer = 0;
            this.strafeInterval = 15 + this.mob.getRandom().nextInt(25);
            this.moveSpeedFactor = 0.8D + this.mob.getRandom().nextDouble() * 0.4D; 
        }

        // ----------------------------------------------------
        // 階段 1：未裝填（拉弦 + 控距）
        // ----------------------------------------------------
        if (!isCharged) {
            if (this.chargeDelay > 0) {
                this.chargeDelay--;
                movingEntity.getNavigation().stop();
                return;
            }

            if (!isRiding) {
                this.mob.setShiftKeyDown(true);
            }

            this.mob.setChargingCrossbow(true);

            if (!this.mob.isUsingItem()) {
                this.mob.startUsingItem(InteractionHand.MAIN_HAND);
            }

            double distanceToTarget = movingEntity.distanceTo(target);

            // 🎯 A. 距離大於 9 格：一邊拉弦，一邊向前推進
            if (distanceToTarget > 10.0D && this.mob.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.GUARD) {
                this.pathUpdateTimer++;
                if (this.pathUpdateTimer >= 8 || movingEntity.getNavigation().isDone()) {
                    this.pathUpdateTimer = 0;
                    double baseSpeed = isRiding ? 0.6D : 0.45D;
                    movingEntity.getNavigation().moveTo(target, baseSpeed * this.moveSpeedFactor);
                }
            }
        
            // 🎯 小於 10 格：敵人太近，一邊拉弦，一邊往後撤退
            else  if (distanceToTarget <= 11.0D && this.mob.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.GUARD 
                     || distanceToTarget <= 4.0D && this.mob.getMilitiaMode() == VillageMilitiaEntity.MilitiaMode.GUARD 
                     ){
                this.pathUpdateTimer++;
                if (this.pathUpdateTimer >= 8 || movingEntity.getNavigation().isDone()) {
                    this.pathUpdateTimer = 0;

                    int retreatDistance = isRiding ? 10 : 7;
                    Vec3 safeRetreatPos = DefaultRandomPos.getPosAway(
                        movingEntity, 
                        retreatDistance, 
                        4, 
                        target.position()
                    );

                    if (safeRetreatPos != null) {
                        double baseSpeed = isRiding ? 1.15D : 0.45D;
                        double finalSpeed = baseSpeed * this.moveSpeedFactor;
                        movingEntity.getNavigation().moveTo(safeRetreatPos.x, safeRetreatPos.y, safeRetreatPos.z, finalSpeed);
                    }
                }
            }
            
        } 
        // ----------------------------------------------------
        // 階段 2：已裝填（瞄準 + 發射）
        // ----------------------------------------------------
        else {
            this.mob.setChargingCrossbow(false);
            this.mob.stopUsingItem(); 

            movingEntity.getNavigation().stop();

            if (this.shootDelay > 0) {
                this.shootDelay--;
                return;
            }
            
            if (this.mob.level() instanceof ServerLevel) {
                float randomVelocity = 1.4F + this.mob.getRandom().nextFloat() * 0.4F; 
                this.mob.performRangedAttack(target, randomVelocity);
            }

            this.mob.setShiftKeyDown(false);
            this.resetRandomParams();
        }
    }

    private void resetRandomParams() {
        this.shootDelay = 9 + this.mob.getRandom().nextInt(15);
        this.chargeDelay = 2 + this.mob.getRandom().nextInt(7);
        this.moveSpeedFactor = 0.8D + this.mob.getRandom().nextDouble() * 0.35D;
        this.strafeTimer = 0;
        this.strafeInterval = 15 + this.mob.getRandom().nextInt(20);
        this.pathUpdateTimer = 99; 
    }
}