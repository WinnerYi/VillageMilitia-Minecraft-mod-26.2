package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class MilitiaBowRetreatGoal extends Goal {
    private final VillageMilitiaEntity mob;
    
    // AI 尋路與計時參數
    private int pathUpdateTimer = 0;
    private int cooldownTicks = 0;       // 射擊後的冷卻/反應時間
    private int targetChargeTicks = 20;  // 預計拉弓多久（滿拉約 20 ticks / 1秒）
    private double moveSpeedFactor = 1.0D;

    public MilitiaBowRetreatGoal(VillageMilitiaEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() 
            && this.mob.getMainHandItem().getItem() instanceof BowItem;
    }

    @Override
    public void start() {
        this.resetRandomParams();
        this.pathUpdateTimer = 0;
    }

    @Override
    public void stop() {
        this.mob.stopUsingItem();
        this.mob.getNavigation().stop();
        this.cooldownTicks = 0;
        
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

        // 冷卻時間處理
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return;
        }

        // 開始/繼續拉弓
        if (!this.mob.isUsingItem()) {
            this.mob.startUsingItem(InteractionHand.MAIN_HAND);
        }

        int ticksHeld = this.mob.getTicksUsingItem();
        boolean isAimingToShoot = ticksHeld >= (this.targetChargeTicks - 3);

        boolean isRiding = this.mob.isPassenger() && this.mob.getVehicle() instanceof PathfinderMob;
        PathfinderMob movingEntity = isRiding ? (PathfinderMob) this.mob.getVehicle() : this.mob;

        // 瞄準射擊瞬間（最後 3 ticks）：停止移動以保證瞄準質感
        if (isAimingToShoot) {
            movingEntity.getNavigation().stop();
        } else {
            double distanceToTarget = movingEntity.distanceTo(target);

            //  距離大於 15 格：向前推進靠近目標
            if (distanceToTarget > 10.0D && this.mob.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.GUARD) {
                this.pathUpdateTimer++;
                if (this.pathUpdateTimer >= 10 || movingEntity.getNavigation().isDone()) {
                    this.pathUpdateTimer = 0;
                    double baseSpeed = isRiding ? 0.7D : 0.5D;
                    movingEntity.getNavigation().moveTo(target, baseSpeed * this.moveSpeedFactor);
                }
            } 
            //  小於 10 格：太近了，往後撤退
            else if (distanceToTarget <= 11.0D && this.mob.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.GUARD 
                     || distanceToTarget <= 4.0D && this.mob.getMilitiaMode() == VillageMilitiaEntity.MilitiaMode.GUARD ){
                this.pathUpdateTimer++;
                if (this.pathUpdateTimer >= 10 || movingEntity.getNavigation().isDone()) {
                    this.pathUpdateTimer = 0;

                    Vec3 safeRetreatPos = DefaultRandomPos.getPosAway(
                        movingEntity, 
                        isRiding ? 10 : 7, 
                        4, 
                        target.position()
                    );

                    if (safeRetreatPos != null) {
                        double baseSpeed = isRiding ? 1.25D : 0.65D;
                        double finalSpeed = baseSpeed * this.moveSpeedFactor;
                        movingEntity.getNavigation().moveTo(safeRetreatPos.x, safeRetreatPos.y, safeRetreatPos.z, finalSpeed);
                    }
                }

                // 隨機跳躍（僅限非騎乘狀態）
                if (!isRiding && this.mob.onGround() && !this.mob.getNavigation().isDone()) {
                    if (this.mob.getRandom().nextFloat() < 0.06F) {
                        this.mob.getJumpControl().jump();
                    }
                }
            }
        }

        // 發射邏輯
        if (ticksHeld >= this.targetChargeTicks) {
            if (this.mob.level() instanceof ServerLevel serverLevel) {
                float power = BowItem.getPowerForTime(ticksHeld);
                if (power >= 0.1F) {
                    ItemStack bowStack = this.mob.getMainHandItem();
                    ItemStack arrowStack = this.mob.getProjectile(bowStack);

                    AbstractArrow arrow = ProjectileUtil.getMobArrow(this.mob, arrowStack, power, bowStack);
                    
                    double dx = target.getX() - this.mob.getX();
                    double dy = target.getY(0.3333333333333333D) - arrow.getY();
                    double dz = target.getZ() - this.mob.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    float uncertainty = 14.0F - serverLevel.getDifficulty().getId() * 4.0F;
                    arrow.shoot(dx, dy + dist * 0.20000000298023224D, dz, power * 1.6F, uncertainty);

                    this.mob.playSound(
                        net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 
                        1.0F, 
                        1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F)
                    );
                    serverLevel.addFreshEntity(arrow);
                }
            }

            // 放箭並重置
            this.mob.stopUsingItem();
            this.resetRandomParams();
        }
    }

    private void resetRandomParams() {
        this.cooldownTicks = 8 + this.mob.getRandom().nextInt(14);
        this.targetChargeTicks = 18 + this.mob.getRandom().nextInt(7);
        this.moveSpeedFactor = 0.9D + this.mob.getRandom().nextDouble() * 0.2D;
        this.pathUpdateTimer = 99; // 重置時強制下次 tick 立刻尋路
    }
}