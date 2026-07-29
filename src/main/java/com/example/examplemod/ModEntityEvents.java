package com.example.examplemod;

import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.Ravager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.EnumSet;


@EventBusSubscriber(modid = ExampleMod.MODID)
public class ModEntityEvents {

    // 建立一個「完全不看 isAlliedTo、不看任何陣營」的強行開火/鎖定 Goal
    public static class ForceTargetGuardGoal extends Goal {
        private final Mob mob;
        private final double range = 16.0D; // 偵測守衛的範圍（半徑 16 格）

        public ForceTargetGuardGoal(Mob mob) {
            this.mob = mob;
            // 宣告這個 AI 會佔用「目標鎖定（TARGET）」的行為權限
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        // 💡 每刻檢查：什麼時候該啟動這個 AI？
        @Override
        public boolean canUse() {
            // 如果目前已經有活著的目標了，就先維持現狀
            if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
                return false;
            }

            // 🔍 用最純粹的空間雷達，抓周圍最近的守衛實體
            java.util.List<VillageMilitiaEntity> targets = this.mob.level().getEntitiesOfClass(
                VillageMilitiaEntity.class,
                this.mob.getBoundingBox().inflate(range, 4.0D, range),
                LivingEntity::isAlive
            );

            if (!targets.isEmpty()) {
                // 找到最近的守衛，直接繞過所有檢查，強行寫入仇恨欄位！
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

        // 防呆：如果是守衛自己出生，完全不要動，避免內鬨
        if (event.getEntity() instanceof VillageMilitiaEntity) {
            return;
        }

        // 🧟 1. 讓所有殭屍打守衛（殭屍本來就可以用原生的，但用這個更穩）
        if (event.getEntity() instanceof Zombie zombie) {
            zombie.targetSelector.addGoal(1, new ForceTargetGuardGoal(zombie));
        }

        // 🪓 2. 讓整支災厄村民大軍（Pillager, Vindicator, Evoker...）主動打守衛
        // 💡 拋棄被 final 擋死的 NearestAttackableTargetGoal，改用我們不受限制的空間雷達！
        if (event.getEntity() instanceof Raider raider) {
            raider.targetSelector.addGoal(1, new ForceTargetGuardGoal(raider));
        }

    }


    @SubscribeEvent
    public static void onVillagerInteract(PlayerInteractEvent.EntityInteract event) {
        // 1. 確保只在伺服器端處理邏輯，且玩家點擊的是主手
        if (event.getLevel().isClientSide() || event.getHand() != event.getEntity().getUsedItemHand()) {
            return;
        }

        net.minecraft.world.entity.player.Player player = event.getEntity();
        ItemStack mainHandItem = player.getMainHandItem();
        
        // 條件前置檢查：玩家必須【處於潛行狀態 (Shift)】且【
        if (player.isShiftKeyDown() && mainHandItem.is(Items.IRON_HELMET)) {
            // 2. 檢查玩家點擊的實體是不是村民
            if (event.getTarget() instanceof Villager villager) {

                boolean isNone = villager.getVillagerData().profession().is(VillagerProfession.NONE);
               
                if (isNone && !villager.isBaby()) {
                     
                // 3. 核心判定：檢查村民是不是「失業/無職業」狀態 (NONE)
                    ServerLevel serverLevel = (ServerLevel) event.getLevel();
                    BlockPos spawnPos = villager.blockPosition();

                    // 4. 建立你的民兵實體
                    VillageMilitiaEntity militia = ModEntities.VILLAGE_MILITIA.get().create(
                        serverLevel, 
                        null,                    // PostSpawnProcessor (不需要特殊處理程序，傳 null)
                        spawnPos,                // BlockPos (生成座標)
                        net.minecraft.world.entity.EntitySpawnReason.SPAWNER, // EntitySpawnReason (生成原因)
                        false,                   // boolean (是否為原本的「根據歷史載入」)
                        false                    // boolean (是否強制對齊方塊中心)
                    );
                    if (militia != null) {
                        // 將民兵移到村民當前的位置，並複製村民的角度
                        militia.setPos(villager.getX(), villager.getY(), villager.getZ());
                        // 2. 設定旋轉角度
                        militia.setYRot(villager.getYRot());
                        militia.setXRot(villager.getXRot());
                        militia.setYHeadRot(villager.getYRot());
                        serverLevel.addFreshEntity(militia);
                        villager.discard();

                        // 🔊 加上震撼的音效回饋（村民受傷加上裝甲裝備聲）
                        serverLevel.playSound(null, spawnPos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);
                        
                        // 6. 設定右鍵互T動成功，阻止原版交易選單與揮劍攻擊動作
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
                
                // 3. 搜尋受害者周圍 32 格內的所有民兵
                net.minecraft.world.phys.AABB alertArea = victim.getBoundingBox().inflate(32.0D);
                java.util.List<VillageMilitiaEntity> nearbyMilitia = victim.level().getEntitiesOfClass(
                    VillageMilitiaEntity.class, 
                    alertArea
                );
                
                // 4. 讓所有附近的民兵把兇手設為第一攻擊目標
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
            // 1. 檢查受傷的是不是民兵
            if (event.getEntity() instanceof VillageMilitiaEntity militia) {
                // 2. 取得攻擊者（必須是活體生物，例如殭屍、骷髏或玩家）
                if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                    
                    // 3. 搜尋民兵周圍 16 格內的鐵巨人
                    net.minecraft.world.phys.AABB searchArea = militia.getBoundingBox().inflate(16.0D);
                    java.util.List<net.minecraft.world.entity.animal.golem.IronGolem> golems = 
                        militia.level().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, searchArea);

                    // 4. 讓附近所有的鐵巨人都把仇恨目標轉向這個攻擊者！
                    for (net.minecraft.world.entity.animal.golem.IronGolem golem : golems) {
                        // 確保攻擊者不是鐵巨人自己
                        if (attacker != golem) {
                            golem.setTarget(attacker);
                        }
                    }
                }
            }
        }
}