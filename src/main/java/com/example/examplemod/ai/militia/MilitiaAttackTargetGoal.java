package com.example.examplemod.ai.militia;

import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.phys.AABB;

public class MilitiaAttackTargetGoal extends Goal {
    private static final int TARGET_DURATION_TICKS = 100;
    private final Mob mob;
    private final double range = 16.0D;
    private int lastHurtTimestamp = 0;
    private int targetTicks;
    private LivingEntity selectedTarget;

    public MilitiaAttackTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.checkAndSwitchTarget()) {
            return true;
        }

        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        AABB searchBox = this.mob.getBoundingBox().inflate(range, 4.0D, range);
        List<LivingEntity> enemies = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            entity -> {
                if (!entity.isAlive() || !this.mob.hasLineOfSight(entity)) {
                    return false;
                }
                if (entity instanceof Monster) {
                    if (entity instanceof Creeper) {
                        return false;
                    }
                    return true;
                }
                
                return false;
            }
        );

        if (!enemies.isEmpty()) {
            enemies.sort((e1, e2) -> Double.compare(this.mob.distanceToSqr(e1), this.mob.distanceToSqr(e2)));
            this.selectedTarget = enemies.get(0);
            this.mob.setTarget(this.selectedTarget);
            this.targetTicks = TARGET_DURATION_TICKS;
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // 戰鬥中持續檢查：如果又被打了，立刻切換目標
        if (this.checkAndSwitchTarget()) {
            return true;
        }

        LivingEntity currentTarget = this.mob.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) {
            return false;
        }
        return this.targetTicks-- > 0
            && this.mob.distanceToSqr(currentTarget) <= range * range;
    }

    @Override
    public void stop() {
        if (this.mob.getTarget() == this.selectedTarget) {
            this.mob.setTarget(null);
        }
        this.selectedTarget = null;
        this.targetTicks = 0;
        super.stop();
    }


    private boolean checkAndSwitchTarget() {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        int currentHurtTimestamp = this.mob.getLastHurtByMobTimestamp();

        if (attacker != null && attacker.isAlive() && this.mob.distanceToSqr(attacker) <= range * range) {
            
            // 只要時間戳改變（代表被打了一下）
            if (currentHurtTimestamp != this.lastHurtTimestamp) {
                this.lastHurtTimestamp = currentHurtTimestamp; // 立即更新時間戳

                // 如果攻擊者不是現在的目標
                if (attacker != this.mob.getTarget()) {
                    this.selectedTarget = attacker;
                    this.mob.setTarget(attacker);
                    this.targetTicks = TARGET_DURATION_TICKS;
                    this.mob.getNavigation().stop();
                    return true;
                }
            }
        }
        return false;
    }
}