package com.example.examplemod.ai.druid;

import com.example.examplemod.entity.VillageDruidEntity;
import com.example.examplemod.entity.VillageMilitiaEntity;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DruidSpellGoal extends Goal {
    private static final int CAST_TIME = 25;
    private static final float SELF_HEAL_THRESHOLD = 0.35F;
    private static final float COMBAT_HEAL_THRESHOLD = 0.30F;
    private static final float IDLE_HEAL_THRESHOLD = 0.75F;
    private static final double MIN_RANGE = 4.0D;
    private static final double CLOSE_TARGET_RANGE = 6.0D;
    private static final double MAX_RANGE = 14.0D;
    private static final double CASTING_MOVE_SPEED = 0.24D;

    private final VillageDruidEntity druid;
    private int castTicks;
    private int cooldownTicks;
    private LivingEntity spellTarget;
    private SpellType spellType;

    private enum SpellType {
        THORN_BINDING,
        NATURE_BURST,
        ARCANE_RAY,
        POISON_BLOOM,
        HEALING_LIGHT
    }

    public DruidSpellGoal(VillageDruidEntity druid) {
        this.druid = druid;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return false;
        }

        if (this.druid.getHealth() <= this.druid.getMaxHealth() * SELF_HEAL_THRESHOLD) {
            this.spellTarget = this.druid;
            this.spellType = SpellType.HEALING_LIGHT;
            return true;
        }

        LivingEntity target = this.druid.getTarget();

        if (target != null && target.isAlive()) {
            LivingEntity criticalAlly = this.findInjuredAlly(COMBAT_HEAL_THRESHOLD);
            if (criticalAlly != null) {
                this.spellTarget = criticalAlly;
                this.spellType = SpellType.HEALING_LIGHT;
                return true;
            }
        }

        if (target == null || !target.isAlive()) {
            LivingEntity injuredAlly = this.findInjuredAlly(IDLE_HEAL_THRESHOLD);
            if (injuredAlly != null) {
                this.spellTarget = injuredAlly;
                this.spellType = SpellType.HEALING_LIGHT;
                return true;
            }
            return false;
        }

        double distance = this.druid.distanceTo(target);
        if (distance <= CLOSE_TARGET_RANGE && this.druid.hasLineOfSight(target)) {
            this.spellTarget = target;
            this.spellType = SpellType.NATURE_BURST;
            return true;
        }

        if (distance < MIN_RANGE
            && distance <= MAX_RANGE
            && this.druid.hasLineOfSight(target)) {
            return false;
        }

        if (distance <= MAX_RANGE && this.druid.hasLineOfSight(target)) {
            this.spellTarget = target;
            int attackType = ThreadLocalRandom.current().nextInt(4);
            this.spellType = switch (attackType) {
                case 0 -> SpellType.THORN_BINDING;
                case 1 -> SpellType.NATURE_BURST;
                case 2 -> SpellType.ARCANE_RAY;
                default -> SpellType.POISON_BLOOM;
            };
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.spellTarget;
        if (this.spellType == SpellType.HEALING_LIGHT && target == this.druid) {
            return this.castTicks < CAST_TIME && this.cooldownTicks <= 0;
        }

        return target != null
            && target.isAlive()
            && this.castTicks < CAST_TIME
            && this.cooldownTicks <= 0
            && this.druid.distanceTo(target) <= MAX_RANGE
            && this.druid.hasLineOfSight(target);
    }

    @Override
    public void start() {
        this.castTicks = 0;
        this.druid.setSpellType(this.spellType.ordinal());
        this.druid.setCasting(true);
    }

    @Override
    public void tick() {
        LivingEntity target = this.spellTarget;
        if (target == null) {
            return;
        }

        this.druid.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.updateCastingMovement(target);
        this.castTicks++;
        this.druid.setCastTicks(this.castTicks);
        this.sendCastingCircle();
        

        if (this.druid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                this.getCastingParticles(),
                this.druid.getX(),
                this.druid.getEyeY(),
                this.druid.getZ(),
                2,
                0.16D,
                0.16D,
                0.16D,
                0.02D
            );
        }

        if (this.castTicks >= CAST_TIME) {
            this.castSpell(target);
            this.cooldownTicks = this.getCooldownTicks();
        }
    }

    @Override
    public void stop() {
        this.castTicks = 0;
        this.druid.setCasting(false);
        this.druid.setSpellType(0);
        this.druid.getNavigation().stop();
        this.spellTarget = null;
        this.spellType = null;
    }

    private void castSpell(LivingEntity target) {
        if (this.spellType == SpellType.HEALING_LIGHT) {
            target.heal(5.0F);
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
            this.druid.playSound(SoundEvents.PLAYER_LEVELUP, 0.8F, 1.35F);
            this.sendParticles(target, ParticleTypes.HEART, 10);
            this.sendParticles(target, ParticleTypes.CHERRY_LEAVES, 10);
            return;
        }

        if (this.spellType == SpellType.THORN_BINDING) {
            target.hurt(this.druid.damageSources().magic(), 4.0F);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1));
            this.druid.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 1.15F);
            this.sendParticles(target, ParticleTypes.CRIT, 18);
            this.sendParticles(target, ParticleTypes.FALLING_SPORE_BLOSSOM, 12);
            return;
        }

        if (this.spellType == SpellType.ARCANE_RAY) {
            target.hurt(this.druid.damageSources().magic(), 6.0F);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            this.druid.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.4F);
            this.sendParticles(target, ParticleTypes.END_ROD, 18);
            this.sendParticles(target, ParticleTypes.ENCHANTED_HIT, 10);
            return;
        }

        if (this.spellType == SpellType.POISON_BLOOM) {
            target.hurt(this.druid.damageSources().magic(), 2.0F);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
            this.druid.playSound(SoundEvents.SPORE_BLOSSOM_STEP, 1.0F, 0.7F);
            this.sendParticles(target, ParticleTypes.CRIMSON_SPORE, 20);
            this.sendParticles(target, ParticleTypes.POOF, 8);
            return;
        }

        target.hurt(this.druid.damageSources().magic(), 3.0F);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));

        Vec3 knockbackDirection = target.position().subtract(this.druid.position()).normalize();
        target.push(knockbackDirection.x * 1.15D, 0.28D, knockbackDirection.z * 1.15D);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
        this.druid.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.2F, 0.75F);
        this.damageNearbyEnemies(target);
        this.sendParticles(target, ParticleTypes.EXPLOSION_EMITTER, 1);
        this.sendParticles(target, ParticleTypes.EXPLOSION, 4);
        this.sendParticles(target, ParticleTypes.ENCHANTED_HIT, 16);
    }

    private void updateCastingMovement(LivingEntity target) {
        if (this.spellType == SpellType.HEALING_LIGHT && target == this.druid) {
            this.druid.getNavigation().stop();
            return;
        }

        if (this.castTicks % 6 != 0) {
            return;
        }

        double distance = this.druid.distanceTo(target);
        if (distance < 7.0D) {
            Vec3 retreatPosition = DefaultRandomPos.getPosAway(
                this.druid,
                5,
                3,
                target.position()
            );

            if (retreatPosition != null) {
                this.druid.getNavigation().moveTo(
                    retreatPosition.x,
                    retreatPosition.y,
                    retreatPosition.z,
                    CASTING_MOVE_SPEED
                );
            }
        } else if (distance > 11.0D) {
            this.druid.getNavigation().moveTo(target, CASTING_MOVE_SPEED);
        } else {
            this.moveSidewaysAroundTarget(target);
        }
    }

    private void moveSidewaysAroundTarget(LivingEntity target) {
        Vec3 offset = this.druid.position().subtract(target.position());
        Vec3 horizontalOffset = new Vec3(offset.x, 0.0D, offset.z);
        if (horizontalOffset.lengthSqr() < 0.01D) {
            return;
        }

        Vec3 radialDirection = horizontalOffset.normalize();
        double direction = (this.castTicks / 6) % 2 == 0 ? 1.0D : -1.0D;
        Vec3 sidewaysDirection = new Vec3(
            -radialDirection.z * direction,
            0.0D,
            radialDirection.x * direction
        );
        Vec3 strafePosition = this.druid.position().add(sidewaysDirection.scale(3.0D));

        this.druid.getNavigation().moveTo(
            strafePosition.x,
            strafePosition.y,
            strafePosition.z,
            CASTING_MOVE_SPEED
        );
    }

    private LivingEntity findInjuredAlly(float healthThreshold) {
        AABB searchArea = this.druid.getBoundingBox().inflate(MAX_RANGE);
        List<LivingEntity> allies = this.druid.level().getEntitiesOfClass(
            LivingEntity.class,
            searchArea,
            entity -> entity != this.druid
                && entity.isAlive()
                && entity.getHealth() < entity.getMaxHealth() * healthThreshold
                && (entity instanceof VillageDruidEntity
                    || entity instanceof VillageMilitiaEntity
                    || entity instanceof AbstractVillager
                    || entity instanceof IronGolem)
                && this.druid.hasLineOfSight(entity)
        );

        allies.sort((first, second) -> Float.compare(
            first.getHealth() / first.getMaxHealth(),
            second.getHealth() / second.getMaxHealth()
        ));

        return allies.isEmpty() ? null : allies.get(0);
    }

    private void sendParticles(LivingEntity target, net.minecraft.core.particles.ParticleOptions particles, int count) {
        if (this.druid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                particles,
                target.getX(),
                target.getY(0.5D),
                target.getZ(),
                count,
                0.25D,
                0.25D,
                0.25D,
                0.05D
            );
        }
    }

   
    private void sendCastingCircle() {
        if (!(this.druid.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ParticleOptions circleParticle = this.getCastingParticles();
        double pulse = Math.sin(this.castTicks * 0.35D) * 0.08D;
        double outerRadius = 0.85D + pulse;
        double innerRadius = 0.48D + pulse * 0.5D;
        double y = this.druid.getY() + 0.04D;

        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0D * index / 16.0D;
            serverLevel.sendParticles(
                circleParticle,
                this.druid.getX() + Math.cos(angle) * outerRadius,
                y,
                this.druid.getZ() + Math.sin(angle) * outerRadius,
                1,
                0.01D,
                0.01D,
                0.01D,
                0.0D
            );
        }

        if (this.castTicks % 2 == 0) {
            for (int index = 0; index < 8; index++) {
                double angle = Math.PI * 2.0D * index / 8.0D + this.castTicks * 0.12D;
                serverLevel.sendParticles(
                    ParticleTypes.END_ROD,
                    this.druid.getX() + Math.cos(angle) * innerRadius,
                    y + 0.02D,
                    this.druid.getZ() + Math.sin(angle) * innerRadius,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D
                );
            }
        }
    }

    private void damageNearbyEnemies(LivingEntity primaryTarget) {
        AABB blastArea = primaryTarget.getBoundingBox().inflate(2.5D);
        List<Monster> nearbyEnemies = this.druid.level().getEntitiesOfClass(
            Monster.class,
            blastArea,
            entity -> entity != primaryTarget && entity.isAlive()
        );

        for (Monster nearbyEnemy : nearbyEnemies) {
            nearbyEnemy.hurt(this.druid.damageSources().magic(), 2.0F);

            Vec3 knockbackDirection = nearbyEnemy.position()
                .subtract(primaryTarget.position())
                .normalize();
            nearbyEnemy.push(
                knockbackDirection.x * 0.75D,
                0.18D,
                knockbackDirection.z * 0.75D
            );
        }
    }

    private ParticleOptions getCastingParticles() {
        return switch (this.spellType) {
            case THORN_BINDING -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case NATURE_BURST -> ParticleTypes.ENCHANT;
            case ARCANE_RAY -> ParticleTypes.END_ROD;
            case POISON_BLOOM -> ParticleTypes.CRIMSON_SPORE;
            case HEALING_LIGHT -> ParticleTypes.CHERRY_LEAVES;
            case null -> ParticleTypes.WITCH;
        };
    }

    private int getCooldownTicks() {
        return switch (this.spellType) {
            case THORN_BINDING -> ThreadLocalRandom.current().nextInt(40, 71);
            case NATURE_BURST -> ThreadLocalRandom.current().nextInt(50, 81);
            case ARCANE_RAY -> ThreadLocalRandom.current().nextInt(65, 96);
            case POISON_BLOOM -> ThreadLocalRandom.current().nextInt(55, 86);
            case HEALING_LIGHT -> ThreadLocalRandom.current().nextInt(140, 201);
            case null -> 100;
        };
    }
}