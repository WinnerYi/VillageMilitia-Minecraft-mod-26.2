package com.example.examplemod; // 請確保跟你的主程式 package 一致

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModEntities {
    // 建立一個延遲註冊器 (DeferredRegister)
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = 
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ExampleMod.MODID);

    // 註冊我們的村民守衛
    public static final Supplier<EntityType<VillageMilitiaEntity>> VILLAGE_MILITIA =
        ENTITY_TYPES.register("village_militia", () -> 
            EntityType.Builder.of(VillageMilitiaEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 1.95F) // 碰撞箱大小（almost same as 村民）
                .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ExampleMod.MODID, "village_militia")))
        );
}