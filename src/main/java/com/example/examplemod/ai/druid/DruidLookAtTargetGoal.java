package com.example.examplemod.ai.druid;

import com.example.examplemod.entity.VillageDruidEntity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class DruidLookAtTargetGoal extends Goal {
    private final VillageDruidEntity druid;

    public DruidLookAtTargetGoal(VillageDruidEntity druid) {
        this.druid = druid;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.druid.getTarget();
        return target != null && target.isAlive() && !this.druid.isCasting();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.druid.getTarget();
        return target != null && target.isAlive() && !this.druid.isCasting();
    }

    @Override
    public void tick() {
        LivingEntity target = this.druid.getTarget();
        if (target != null) {
            this.druid.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }
}