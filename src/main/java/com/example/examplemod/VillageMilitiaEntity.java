package com.example.examplemod;

import com.example.examplemod.ai.MilitiaBowRetreatGoal;
import com.example.examplemod.ai.MilitiaReturnToGuardGoal;
import com.example.examplemod.ai.MilitiaAreaPatrolGoal;
import com.example.examplemod.ai.MilitiaAttackTargetGoal;
import com.example.examplemod.ai. MilitiaSpearWithoutShieldGoal ;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;


import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;


import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.illager.Pillager;

import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

import com.example.examplemod.ai.MilitiaFollowOwnerGoal;
import com.example.examplemod.ai.MilitiaSwordAndShieldAttackGoal;
import com.example.examplemod.ai.MilitiaCrossbowRetreatGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;



public class VillageMilitiaEntity extends PathfinderMob implements CrossbowAttackMob, InventoryCarrier{
    private final SimpleContainer inventory = new SimpleContainer(5);
    private int celebrateTicks = 0;
    private BlockPos guardPos = null;

    public enum MilitiaMode {
        PATROL,  
        GUARD,   
        FOLLOW, 
        IDLE    
    }
    

    @Override
    protected void registerGoals() {
        // Priority 0: 基本生存
        this.goalSelector.addGoal(0, new FloatGoal(this));
        
        // Priority 1: 開門與目標選擇
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VillageMilitiaEntity.class).setAlertOthers());
        this.targetSelector.addGoal(1, new MilitiaAttackTargetGoal(this));

        // Priority 1 & 2: 戰鬥相關走位與攻擊 AI
        this.goalSelector.addGoal(1, new MilitiaBowRetreatGoal(this));
        this.goalSelector.addGoal(2, new MilitiaCrossbowRetreatGoal(this));
        this.goalSelector.addGoal(2, new  MilitiaSpearWithoutShieldGoal<>(this, 0.9, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(2, new MilitiaSwordAndShieldAttackGoal(this));

        // =========================================================
        //  模式專屬 AI 邏輯 (Priority 2 ~ 5)
        // =========================================================
        
       
        this.goalSelector.addGoal(2, new MilitiaReturnToGuardGoal(this, 0.4D));
        this.goalSelector.addGoal(3, new MilitiaFollowOwnerGoal(this, 0.7D, 3.0F, 10.0F));
        this.goalSelector.addGoal(4, new MilitiaAreaPatrolGoal(this, 0.5D));
        this.goalSelector.addGoal(4, new MoveThroughVillageGoal(this, 0.5D, false, 4, () -> false) {
            @Override
            public boolean canUse() {
                return MilitiaMode.IDLE.equals(VillageMilitiaEntity.this.getMilitiaMode()) && super.canUse();
            }
        });

        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                return MilitiaMode.IDLE.equals(VillageMilitiaEntity.this.getMilitiaMode()) && super.canUse();
            }
        });

        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.5D) {
            @Override
            public boolean canUse() {
                return MilitiaMode.IDLE.equals(VillageMilitiaEntity.this.getMilitiaMode()) && super.canUse();
            }
        });

        // Priority 5: 閒置觀察
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        
    }

    private static final EntityDataAccessor<Boolean> IS_CHARGING_CROSSBOW = 
        SynchedEntityData.defineId(VillageMilitiaEntity.class, EntityDataSerializers.BOOLEAN);
    
    private static final EntityDataAccessor<Integer> MODE = 
        SynchedEntityData.defineId(VillageMilitiaEntity.class, EntityDataSerializers.INT);
   
    private static final EntityDataAccessor<Boolean> IS_CELEBRATING =
        SynchedEntityData.defineId(VillageMilitiaEntity.class, EntityDataSerializers.BOOLEAN);


    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_CHARGING_CROSSBOW, false);
        builder.define(IS_CELEBRATING, false);
        builder.define(MODE, MilitiaMode.IDLE.ordinal());
    }

    public MilitiaMode getMilitiaMode() {
        return MilitiaMode.values()[this.entityData.get(MODE)];
    }

    public void setMilitiaMode(MilitiaMode mode) {
        this.entityData.set(MODE, mode.ordinal());
        if (mode == MilitiaMode.GUARD) {
            // 切換成 GUARD 時，自動將當前位置設為守衛點
            this.guardPos = this.blockPosition();
        }
    }

    public BlockPos getGuardPos() {
        if (this.guardPos == null) {
            this.guardPos = this.blockPosition();
        }
        return this.guardPos;
    }


    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        // 1. 儲存民兵模式 (使用 Codec.INT)
        output.store("MilitiaMode", com.mojang.serialization.Codec.INT, this.getMilitiaMode().ordinal());

        // 2. 儲存 Guard 座標 (使用 BlockPos.CODEC)
        if (this.guardPos != null) {
            output.store("GuardPos", net.minecraft.core.BlockPos.CODEC, this.guardPos);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int modeOrdinal = input.read("MilitiaMode", com.mojang.serialization.Codec.INT).orElse(MilitiaMode.IDLE.ordinal());
        if (modeOrdinal >= 0 && modeOrdinal < MilitiaMode.values().length) {
            this.setMilitiaMode(MilitiaMode.values()[modeOrdinal]);
        }

        input.read("GuardPos", net.minecraft.core.BlockPos.CODEC).ifPresent(pos -> {
            this.guardPos = pos;
        });
    }

    @Override
    public boolean canBeLeashed() {
        return false; 
    }

    
    
    public VillageMilitiaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setRequiredPathLength(48.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.5D)
            .add(Attributes.ATTACK_DAMAGE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public boolean isHoldingBowAndCharging() {
        return this.isUsingItem() && this.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem;
    }

    

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        this.performCrossbowAttack(this, 1.6F);
    }

    public boolean isChargingCrossbow() {
        return this.entityData.get(IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean isCharging) {
        // this.entityData.set(IS_CHARGING_CROSSBOW, isCharging);
    }

     @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

   
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        if (weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem weaponItem) {

            return new ItemStack(net.minecraft.world.item.Items.ARROW);
        }
        
        return ItemStack.EMPTY;
    }


    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }
    
    public boolean isCelebrating() {
        return this.entityData.get(IS_CELEBRATING);
    }

    public void setCelebrating(boolean celebrating) {
        this.entityData.set(IS_CELEBRATING, celebrating);
    }
   
    private boolean isHeroNearby() {
        Player nearestPlayer = this.level().getNearestPlayer(this, 3.0D);
        return nearestPlayer != null && nearestPlayer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

   

   
    @Override
    public void setTarget(@javax.annotation.Nullable net.minecraft.world.entity.LivingEntity target) {
        if (target instanceof VillageMilitiaEntity ) {
            return; 
        }
        super.setTarget(target); 
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSq) {
        return false; 
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, spawnData);
        if (this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.BannerItem) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            
        }
        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);

        return result;
    }
   

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) { return net.minecraft.sounds.SoundEvents.VILLAGER_HURT; }
    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_DEATH; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean wasHurt = super.hurtServer(level, source, amount);

        // 如果成功造成傷害，且攻擊者是玩家（近戰或遠程）
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


    // ================== 【 右鍵智慧換裝與互動機制 】 ==================
@Override
public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
    ItemStack itemInHand = player.getItemInHand(hand);

    // 戰鬥中不互動
    if (this.getTarget() != null && this.getTarget().isAlive()) {
        return InteractionResult.PASS;
    }

   
    if (player.isShiftKeyDown()) {

        // --- Client 端處理 ---
        if (this.level().isClientSide()) {
            
            if (!itemInHand.isEmpty() || this.isPassenger()) {
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        }

        // --- Server 端處理 ---
        if (this.level() instanceof ServerLevel serverLevel) {

            // =========================================================
            // A. 手持綠寶石 + Shift + 右鍵：切換 4 種民兵模式
            // =========================================================
            if (itemInHand.is(Items.EMERALD)) {
                MilitiaMode currentMode = this.getMilitiaMode();

                MilitiaMode nextMode = switch (currentMode) {
                    case IDLE -> MilitiaMode.FOLLOW;
                    case FOLLOW -> MilitiaMode.GUARD;
                    case GUARD -> MilitiaMode.PATROL;
                    case PATROL -> MilitiaMode.IDLE;
                };

                this.setMilitiaMode(nextMode);
                this.getNavigation().stop();

                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 
                    7, 0.3D, 0.5D, 0.3D, 0.02D
                );

                if (nextMode == MilitiaMode.IDLE) {
                    ItemStack refund = new ItemStack(Items.EMERALD, 3);
                    this.spawnAtLocation(serverLevel, refund);
                } else {
                    if (!player.getAbilities().instabuild) {
                        itemInHand.shrink(1);
                    }
                }

                serverLevel.playSound(
                    null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.VILLAGER_YES,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F
                );
               
                player.swing(hand, true);
                return InteractionResult.SUCCESS_SERVER;
            }

            // =========================================================
            // B. 木棒 + Shift + 右鍵：卸下全部裝備
            // =========================================================
            if (itemInHand.is(Items.STICK)) {
                boolean dropAny = false;

                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack currentEquipped = this.getItemBySlot(slot);
                    if (!currentEquipped.isEmpty()) {
                        this.spawnAtLocation(serverLevel, currentEquipped.copy());
                        this.setItemSlot(slot, ItemStack.EMPTY);
                        dropAny = true;
                    }
                }

                if (dropAny) {
                    serverLevel.playSound(
                        null, this.getX(), this.getY(), this.getZ(), 
                        net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 
                        net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F
                    );
                    player.swing(hand, true);
                    return InteractionResult.SUCCESS_SERVER;
                }
            } 

            // =========================================================
            //  一般物品 + Shift + 右鍵：單件裝備替換邏輯
            // =========================================================
            else if (!itemInHand.isEmpty()) {
                EquipmentSlot slotToEquip = null;

                if (itemInHand.getItem() instanceof ShieldItem) {
                    slotToEquip = EquipmentSlot.OFFHAND;
                } 
                else if (itemInHand.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) {
                    var equippable = itemInHand.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                    if (equippable != null) {
                        slotToEquip = equippable.slot();
                    }
                }
                else if (itemInHand.isDamageableItem() || itemInHand.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                    slotToEquip = EquipmentSlot.MAINHAND;
                }

                if (slotToEquip != null) {
                    ItemStack currentEquipped = this.getItemBySlot(slotToEquip);

                    if (!currentEquipped.isEmpty()) {
                        this.spawnAtLocation(serverLevel, currentEquipped.copy());
                    }

                    ItemStack newEquip = itemInHand.copy();
                    newEquip.setCount(1);
                    this.setItemSlot(slotToEquip, newEquip);

                    if (!player.getAbilities().instabuild) {
                        itemInHand.shrink(1);
                    }

                    serverLevel.playSound(
                        null, this.getX(), this.getY(), this.getZ(), 
                        net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 
                        net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F
                    );
                    player.swing(hand, true);
                    return InteractionResult.SUCCESS_SERVER;
                }
            }

            // =========================================================
            // D. Shift + 雙手空手 + 主手右鍵：下馬邏輯 
            // =========================================================
            if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
                if (this.isPassenger()) {
                    this.stopRiding();
                    this.level().broadcastEntityEvent(this, (byte)4); 
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
        }
    }

        return super.interact(player, hand, location);
    }
   
    private int lastHorseCheckTick = 0;
   @Override
    public void aiStep() {
        super.aiStep();
                                /// important
        if (this.level().isClientSide() && this.swinging) {
            this.swingTime++; // 強制讓 -1 變成 0, 1, 2, 3, 4, 5...
            
            // 原版揮手預設持續 6 個 tick
            if (this.swingTime >= this.getCurrentSwingDuration()) {
                this.swingTime = 0;
                this.swinging = false;
            }
        }
        

        if (!this.level().isClientSide()) {
            
            // 1. 【原版收盾邏輯】如果 AI Goal 結束或失去目標，確保安全放下盾牌
            if (this.getTarget() == null || this.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                if (this.isUsingItem() && this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                    this.stopUsingItem();
                }
            }

            // 【重裝戰馬騎乘與卸載邏輯】
            if (!this.isPassenger()) {
                // =================【 上馬邏輯 】=================
                if (this.tickCount - this.lastHorseCheckTick >= 35) {
                    this.lastHorseCheckTick = this.tickCount;

                    net.minecraft.world.phys.AABB checkArea = this.getBoundingBox().inflate(2.0D);
                    java.util.List<net.minecraft.world.entity.animal.equine.Horse> nearbyHorses = 
                        this.level().getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Horse.class, checkArea);

                    for (net.minecraft.world.entity.animal.equine.Horse horse : nearbyHorses) {
                       
                        boolean hasSaddle = !horse.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty();
                        boolean hasNoPassengers = horse.getPassengers().isEmpty();

                        if (horse.isAlive() && hasNoPassengers && hasSaddle  && !horse.isLeashed()) {
                            this.startRiding(horse);
                            break;
                        }
                    }
                }
            } else {
                // =================【 下馬邏輯 】=================
                
                if (this.getVehicle() instanceof net.minecraft.world.entity.animal.equine.Horse horse) {
                    if (this.tickCount % 20 == 0) {
                        if (!horse.isAlive()) {
                            this.stopRiding();
                        }
                    }
                }
            }

            // 3. 【原版回血邏輯】每 100 ticks 治癒 (半顆心)
            if (this.tickCount % 100 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F);
                }
            }

            // 4. 【原版逃生邏輯】每 20 ticks 在水或岩漿中尋找陸地導航
            if (this.tickCount % 20 == 0 && (this.isInWater() || this.isInLava()) && this.getTarget() == null && this.getNavigation().isDone()) {
                net.minecraft.world.phys.Vec3 landPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, 10, 7);
                
                if (landPos != null) {
                    // 如果在岩漿裡，逃命速度加成到 1.5D！在水裡則維持 1.0D
                    double speed = this.isInLava() ? 1.4D : 1.0D;
                    this.getNavigation().moveTo(landPos.x, landPos.y, landPos.z, speed);
                }
            }
       
        }
    }
    
    @Override
    public void rideTick() {
        super.rideTick();
        if (this.getVehicle() instanceof net.minecraft.world.entity.animal.equine.Horse) {
            this.setPos(this.getX(), this.getY() - 0.60D, this.getZ());
        }
    }

   
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            if (this.level().isClientSide()) {
                
                this.stopUsingItem();      
                this.swinging = true;     
                this.swingTime = 0;       
                this.swingingArm = net.minecraft.world.InteractionHand.MAIN_HAND; 
            }
        } else if (id == 5) {
            if (this.level().isClientSide()) {
                this.stopUsingItem();
                this.swinging = true;
                this.swingTime = -1;
                this.swingingArm = net.minecraft.world.InteractionHand.OFF_HAND;
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
    
    private void spawnFootParticles(ServerLevel level, SimpleParticleType particleType, int count) {
   
        for (int i = 0; i < count; i++) {
            double offsetX = (this.random.nextDouble() - 0.5D) * 0.8D;
            double offsetZ = (this.random.nextDouble() - 0.5D) * 0.8D;
            
            level.sendParticles(
                particleType,
                this.getX() + offsetX,
                this.getY() + 0.1D, 
                this.getZ() + offsetZ,
                1,                  
                0.0D, 0.05D, 0.0D,  
                0.02D              
            );
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            
            if (this.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
               
                if (this.isUsingItem()) {
                    
                    if (this.getPose() != net.minecraft.world.entity.Pose.CROUCHING) {
                        this.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                        this.setShiftKeyDown(true);
                    }
                } else {
                    if (this.getPose() == net.minecraft.world.entity.Pose.CROUCHING) {
                        this.setPose(net.minecraft.world.entity.Pose.STANDING);
                        this.setShiftKeyDown(false);
                    }
                }
            } else {
                if (this.isShiftKeyDown()) {
                    this.setPose(net.minecraft.world.entity.Pose.STANDING);
                    this.setShiftKeyDown(false);
                }
            }
        }


        if (!this.level().isClientSide()) {
    
            if (isCelebrating()) {
                this.celebrateTicks--;
                if (this.celebrateTicks <= 0) {
                    setCelebrating(false);
                }
            } 
            
            else if (this.tickCount % 40 == 0 && isHeroNearby()) {
               
                if (this.random.nextFloat() < 1F) { 
                    setCelebrating(true);
                    this.celebrateTicks = 50; 
                }
            }
        }

        if (!this.level().isClientSide() && this.tickCount % 20 == 0) {
            if (this.level() instanceof ServerLevel serverLevel) {
                
                // 根據當前模式播放腳下粒子
                switch (this.getMilitiaMode()) { 
                    case FOLLOW -> {
                       
                        spawnFootParticles(serverLevel, ParticleTypes.END_ROD, 1);
                    }
                    case GUARD -> {
                       
                        spawnFootParticles(serverLevel, ParticleTypes.ENCHANT, 1);
                    }
                    case PATROL -> {
                        
                        spawnFootParticles(serverLevel, ParticleTypes.GLOW, 1);
                    }
                    case IDLE -> {
                        // IDLE 什麼都不做，
                    }
                }
            }
        }


    

        
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, boolean hitByPlayer) {
        super.dropCustomDeathLoot(serverLevel, damageSource, hitByPlayer);

        // 遍歷所有裝備欄位，並無條件 100% 噴在地上
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack itemStack = this.getItemBySlot(slot);
            if (!itemStack.isEmpty()) {
                this.spawnAtLocation(serverLevel, itemStack);
                this.setItemSlot(slot, ItemStack.EMPTY); 
            }
        }
    }
}