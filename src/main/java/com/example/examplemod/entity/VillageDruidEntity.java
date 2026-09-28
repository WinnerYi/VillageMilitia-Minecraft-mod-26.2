package com.example.examplemod.entity;

import com.example.examplemod.ai.druid.DruidCombatPositionGoal;
import com.example.examplemod.ai.druid.DruidEmergencyEscapeGoal;
import com.example.examplemod.ai.druid.DruidLookAtTargetGoal;
import com.example.examplemod.ai.druid.DruidSpellGoal;
import com.example.examplemod.ai.militia.MilitiaAttackTargetGoal;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;

public class VillageDruidEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> IS_CASTING =
        SynchedEntityData.defineId(VillageDruidEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CAST_TICKS =
        SynchedEntityData.defineId(VillageDruidEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPELL_TYPE =
        SynchedEntityData.defineId(VillageDruidEntity.class, EntityDataSerializers.INT);

    public VillageDruidEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(1, new DruidEmergencyEscapeGoal(this));
        this.goalSelector.addGoal(1, new DruidSpellGoal(this));
        this.goalSelector.addGoal(2, new DruidCombatPositionGoal(this));
        this.goalSelector.addGoal(3, new DruidLookAtTargetGoal(this));
        this.goalSelector.addGoal(4, new MoveThroughVillageGoal(this, 0.5D, false, 4, () -> true));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.55D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new MilitiaAttackTargetGoal(this));
    }

    public boolean isCasting() {
        return this.entityData.get(IS_CASTING);
    }

    public void setCasting(boolean casting) {
        this.entityData.set(IS_CASTING, casting);
        if (!casting) {
            this.entityData.set(CAST_TICKS, 0);
        }
    }

    public void setCastTicks(int castTicks) {
        this.entityData.set(CAST_TICKS, castTicks);
    }

    public int getSpellType() {
        return this.entityData.get(SPELL_TYPE);
    }

    public void setSpellType(int spellType) {
        this.entityData.set(SPELL_TYPE, spellType);
    }

    public float getCastProgress(float partialTick) {
        return Math.min(1.0F, (this.entityData.get(CAST_TICKS) + partialTick) / 30.0F);
    }
    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) { return SoundEvents.VILLAGER_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
         boolean wasHurt = super.hurtServer(level, source, amount);
        if (wasHurt) {
            if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) {
                level.sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    this.getX(), 
                    this.getEyeY() + 0.5D, 
                    this.getZ(),
                    5,                    
                    0.25D, 0.25D, 0.25D,  
                    0.02D                  
                );
            }
        }
        return wasHurt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_CASTING, false);
        builder.define(CAST_TICKS, 0);
        builder.define(SPELL_TYPE, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
            .add(Attributes.MAX_HEALTH, 16.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.38D)
            .add(Attributes.FOLLOW_RANGE, 18.0D);
    }
}