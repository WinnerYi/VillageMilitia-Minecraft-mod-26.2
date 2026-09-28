package com.example.examplemod.ai.druid;

import com.example.examplemod.entity.VillageDruidEntity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class DruidCombatPositionGoal extends Goal {
    private static final double MIN_COMBAT_DISTANCE = 8.0D;
    private static final double MAX_COMBAT_DISTANCE = 14.0D;

    private final VillageDruidEntity druid;
    private int pathUpdateTicks;

    public DruidCombatPositionGoal(VillageDruidEntity druid) {
        this.druid = druid;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.druid.getTarget();
        if (target == null || !target.isAlive() || this.druid.isCasting()) {
            return false;
        }

        double distance = this.druid.distanceTo(target);
        return distance < MIN_COMBAT_DISTANCE || distance > MAX_COMBAT_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.druid.getTarget();
        if (target == null || !target.isAlive() || this.druid.isCasting()) {
            return false;
        }

        double distance = this.druid.distanceTo(target);
        return distance < MIN_COMBAT_DISTANCE || distance > MAX_COMBAT_DISTANCE;
    }

    @Override
    public void start() {
        this.pathUpdateTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.druid.getTarget();
        if (target == null) {
            return;
        }

        this.druid.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.pathUpdateTicks++;

        if (this.pathUpdateTicks < 4 && !this.druid.getNavigation().isDone()) {
            return;
        }
        this.pathUpdateTicks = 0;

        double distance = this.druid.distanceTo(target);
        if (distance > MAX_COMBAT_DISTANCE) {
            this.druid.getNavigation().moveTo(target, 0.70D);
            return;
        }

        Vec3 retreatPosition = DefaultRandomPos.getPosAway(
            this.druid,
            12,
            4,
            target.position()
        );

        if (retreatPosition != null) {
            this.druid.getNavigation().moveTo(
                retreatPosition.x,
                retreatPosition.y,
                retreatPosition.z,
                0.95D
            );
        }
    }

    @Override
    public void stop() {
        this.druid.getNavigation().stop();
        this.pathUpdateTicks = 0;
    }
}