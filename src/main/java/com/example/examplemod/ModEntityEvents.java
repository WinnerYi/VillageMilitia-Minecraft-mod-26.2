package com.example.examplemod;

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

            
            java.util.List<VillageMilitiaEntity> targets = this.mob.level().getEntitiesOfClass(
                VillageMilitiaEntity.class,
                this.mob.getBoundingBox().inflate(range, 4.0D, range),
                LivingEntity::isAlive
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
            return currentTarget instanceof VillageMilitiaEntity && currentTarget.isAlive();
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

      
        if (event.getEntity() instanceof VillageMilitiaEntity) {
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
        
        
        if (player.isShiftKeyDown() && mainHandItem.is(Items.IRON_HELMET)) {
            
            if (event.getTarget() instanceof Villager villager) {

                boolean isNone = villager.getVillagerData().profession().is(VillagerProfession.NONE);
               
                if (isNone && !villager.isBaby()) {
                     
               
                    ServerLevel serverLevel = (ServerLevel) event.getLevel();
                    BlockPos spawnPos = villager.blockPosition();

                    
                    VillageMilitiaEntity militia = ModEntities.VILLAGE_MILITIA.get().create(
                        serverLevel, 
                        null,                    
                        spawnPos,               
                        net.minecraft.world.entity.EntitySpawnReason.SPAWNER, 
                        false,                   
                        false                  
                    );
                    if (militia != null) {
                       
                        militia.setPos(villager.getX(), villager.getY(), villager.getZ());
                       
                        militia.setYRot(villager.getYRot());
                        militia.setXRot(villager.getXRot());
                        militia.setYHeadRot(villager.getYRot());
                        serverLevel.addFreshEntity(militia);
                        villager.discard();

                       
                        serverLevel.playSound(null, spawnPos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);
                        
                        
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
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
                java.util.List<VillageMilitiaEntity> nearbyMilitia = victim.level().getEntitiesOfClass(
                    VillageMilitiaEntity.class, 
                    alertArea
                );
                
                //  讓所有附近的民兵把兇手設為第一攻擊目標
                for (VillageMilitiaEntity militia : nearbyMilitia) {
                    if (militia.getTarget() == null || militia.getTarget() != attacker) {
                        militia.setTarget(attacker);
                    }
                }
            }
        }
    }



    @net.neoforged.bus.api.SubscribeEvent
        public static void onMilitiaHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
            
            if (event.getEntity() instanceof VillageMilitiaEntity militia) {
               
                if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                    
                
                    net.minecraft.world.phys.AABB searchArea = militia.getBoundingBox().inflate(16.0D);
                    java.util.List<net.minecraft.world.entity.animal.golem.IronGolem> golems = 
                        militia.level().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, searchArea);

                    
                    for (net.minecraft.world.entity.animal.golem.IronGolem golem : golems) {
                        if (attacker != golem) {
                            golem.setTarget(attacker);
                        }
                    }
                }
            }
        }
}