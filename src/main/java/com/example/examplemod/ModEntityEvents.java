package com.example.examplemod;

import com.example.examplemod.entity.VillageDruidEntity;
import com.example.examplemod.entity.VillageMilitiaEntity;

import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.EnumSet;


@EventBusSubscriber(modid = ExampleMod.MODID)
public class ModEntityEvents {

    private static boolean isVillageUnit(LivingEntity entity) {
        return entity instanceof VillageMilitiaEntity
            || entity instanceof VillageDruidEntity;
    }

   
    public static class ForceTargetGuardGoal extends Goal {
        private final Mob mob;
        private final double range = 16.0D; 

        public ForceTargetGuardGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

       
        @Override
        public boolean canUse() {
            
            if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
                return false;
            }

            
            java.util.List<LivingEntity> targets = this.mob.level().getEntitiesOfClass(
                LivingEntity.class,
                this.mob.getBoundingBox().inflate(range, 4.0D, range),
                entity -> entity.isAlive()
                    && isVillageUnit(entity)
            );

            if (!targets.isEmpty()) {
                
                this.mob.setTarget(targets.get(0));
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = this.mob.getTarget();
            return currentTarget != null
                && isVillageUnit(currentTarget)
                && currentTarget.isAlive();
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

      
        if (event.getEntity() instanceof LivingEntity livingEntity
            && isVillageUnit(livingEntity)) {
            return;
        }

       
        if (event.getEntity() instanceof Zombie zombie) {
            zombie.targetSelector.addGoal(1, new ForceTargetGuardGoal(zombie));
        }

        
        if (event.getEntity() instanceof Raider raider) {
            raider.targetSelector.addGoal(1, new ForceTargetGuardGoal(raider));
        }

    }


    @SubscribeEvent
    public static void onVillagerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != event.getEntity().getUsedItemHand()) {
            return;
        }

        net.minecraft.world.entity.player.Player player = event.getEntity();
        ItemStack mainHandItem = player.getMainHandItem();

        if (player.isShiftKeyDown()) {
            if (event.getTarget() instanceof Villager villager) {
                boolean isNone = villager.getVillagerData().profession().is(VillagerProfession.NONE);

                if (isNone && !villager.isBaby()) {
                    ServerLevel serverLevel = (ServerLevel) event.getLevel();
                    BlockPos spawnPos = villager.blockPosition();
                    if (mainHandItem.is(Items.IRON_HELMET)) {
                        VillageMilitiaEntity militia = ModEntities.VILLAGE_MILITIA.get().create(
                            serverLevel, null, spawnPos,
                            net.minecraft.world.entity.EntitySpawnReason.SPAWNER,
                            false, false
                        );

                        if (militia != null) {
                            militia.setPos(villager.getX(), villager.getY(), villager.getZ());
                            militia.setYRot(villager.getYRot());
                            militia.setXRot(villager.getXRot());
                            militia.setYHeadRot(villager.getYRot());
                            
                            serverLevel.addFreshEntity(militia);
                            villager.discard();

                            if (!player.getAbilities().instabuild) {
                                mainHandItem.shrink(1);
                            }

                            serverLevel.playSound(null, spawnPos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);

                            event.setCancellationResult(InteractionResult.SUCCESS);
                            event.setCanceled(true);
                        }
                    } 
                    // 🎯 2. 金蘋果 -> 轉化為德魯伊 (Village Druid)
                    else if (mainHandItem.is(Items.GOLDEN_APPLE)) {
                        // ⚠️ 請確保 ModEntities.VILLAGE_DRUID 指向你的德魯伊 DeferredHolder / Supplier
                        var druid = ModEntities.VILLAGE_DRUID.get().create(
                            serverLevel, null, spawnPos,
                            net.minecraft.world.entity.EntitySpawnReason.SPAWNER,
                            false, false
                        );

                        if (druid != null) {
                            druid.setPos(villager.getX(), villager.getY(), villager.getZ());
                            druid.setYRot(villager.getYRot());
                            druid.setXRot(villager.getXRot());
                            druid.setYHeadRot(villager.getYRot());

                            serverLevel.addFreshEntity(druid);
                            villager.discard();

                        
                            if (!player.getAbilities().instabuild) {
                                mainHandItem.shrink(1);
                            }
                            serverLevel.playSound(null, spawnPos, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 1.0F, 1.0F);

                            event.setCancellationResult(InteractionResult.SUCCESS);
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onVillageMembersHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        net.minecraft.world.entity.LivingEntity victim = event.getEntity();

        if (victim instanceof AbstractVillager || victim instanceof IronGolem) {
            if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                
               
                net.minecraft.world.phys.AABB alertArea = victim.getBoundingBox().inflate(32.0D);
                java.util.List<LivingEntity> nearbyUnits = victim.level().getEntitiesOfClass(
                    LivingEntity.class,
                    alertArea
                );
                
                // 讓所有附近的村莊單位把兇手設為第一攻擊目標
                for (LivingEntity unit : nearbyUnits) {
                    if (isVillageUnit(unit)
                        && unit instanceof Mob mob
                        && (mob.getTarget() == null || mob.getTarget() != attacker)) {
                        mob.setTarget(attacker);
                    }
                }
            }
        }
    }



    @net.neoforged.bus.api.SubscribeEvent
        public static void onMilitiaHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
            
            if (event.getEntity() instanceof LivingEntity livingEntity
                && isVillageUnit(livingEntity)
                && livingEntity instanceof Mob villageUnit) {
               
                if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                    
                
                    net.minecraft.world.phys.AABB searchArea = villageUnit.getBoundingBox().inflate(16.0D);
                    java.util.List<net.minecraft.world.entity.animal.golem.IronGolem> golems = 
                        villageUnit.level().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, searchArea);

                    
                    for (net.minecraft.world.entity.animal.golem.IronGolem golem : golems) {
                        if (attacker != golem) {
                            golem.setTarget(attacker);
                        }
                    }
                }
            }
        }
}