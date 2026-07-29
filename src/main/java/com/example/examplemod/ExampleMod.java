package com.example.examplemod;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("village_militia")
public class ExampleMod {
    public static final String MODID = "village_militia";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.SpawnEggItem> MILITIA_SPAWN_EGG = 
        ITEMS.registerItem("militia_spawn_egg",
            properties -> new net.minecraft.world.item.SpawnEggItem(
                properties.spawnEgg(ModEntities.VILLAGE_MILITIA.get())
            )
    );

    

    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        ModEntities.ENTITY_TYPES.register(modEventBus);
    
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerRenderers);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerEntityAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.VILLAGE_MILITIA.get(), VillageMilitiaEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }
        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        
        
        // ================== 【新增：將生怪蛋塞入原版「生成蛋」分頁】 ==================
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(MILITIA_SPAWN_EGG.get());
        }
        // =========================================================================
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.VILLAGE_MILITIA.get(), com.example.examplemod.client.VillageMilitiaRenderer::new);
    }


    // ================== 【對齊 26.2 原始碼方法名】 ==================
    @net.neoforged.fml.common.EventBusSubscriber(modid = ExampleMod.MODID)
    public static class GameEvents {
        
        @net.neoforged.bus.api.SubscribeEvent
        public static void onIronGolemTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
            // 1. 檢查發起仇恨的是不是鐵巨人
            if (event.getEntity() instanceof net.minecraft.world.entity.animal.golem.IronGolem) {
               
                if (event.getNewAboutToBeSetTarget() instanceof VillageMilitiaEntity) {
                    // 3. 攔截仇恨！強制讓鐵巨人直接無視他
                    event.setCanceled(true);
                }
            }
        }
    }

    
}
