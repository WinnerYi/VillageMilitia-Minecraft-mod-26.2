package com.example.examplemod.ai.druid;

import com.example.examplemod.entity.VillageDruidEntity;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class DruidEmergencyEscapeGoal extends Goal {
    private static final float HEALTH_THRESHOLD = 0.25F;
    private static final int EFFECT_DURATION = 70;
    private static final int COOLDOWN_DURATION = 240;

    private final VillageDruidEntity druid;
    private int cooldownTicks;

    public DruidEmergencyEscapeGoal(VillageDruidEntity druid) {
        this.druid = druid;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return false;
        }

        return this.druid.getHealth() <= this.druid.getMaxHealth() * HEALTH_THRESHOLD
            && !this.druid.hasEffect(MobEffects.INVISIBILITY);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        this.druid.addEffect(new MobEffectInstance(
            MobEffects.INVISIBILITY,
            EFFECT_DURATION,
            0,
            false,
            true
        ));
        this.druid.addEffect(new MobEffectInstance(
            MobEffects.LEVITATION,
            90,
            0,
            false,
            true
        ));
        this.druid.addEffect(new MobEffectInstance(
            MobEffects.SLOW_FALLING,
            140,
            0,
            false,
            true
        ));

        if (this.druid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.WHITE_SMOKE,
                this.druid.getX(),
                this.druid.getY(0.5D),
                this.druid.getZ(),
                28,
                0.45D,
                0.7D,
                0.45D,
                0.04D
            );
        }

        LivingEntity target = this.druid.getTarget();
        if (target != null && target.isAlive()) {
            Vec3 escapePosition = DefaultRandomPos.getPosAway(
                this.druid,
                12,
                5,
                target.position()
            );

            if (escapePosition != null) {
                this.druid.getNavigation().moveTo(
                    escapePosition.x,
                    escapePosition.y,
                    escapePosition.z,
                    0.82D
                );
            }
        }

        this.cooldownTicks = COOLDOWN_DURATION;
    }
}