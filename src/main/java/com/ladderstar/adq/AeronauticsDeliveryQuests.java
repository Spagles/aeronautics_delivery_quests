package com.ladderstar.adq;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AeronauticsDeliveryQuests.MODID)
public class AeronauticsDeliveryQuests {
    public static final String MODID = "aeronautics_delivery_quests";
    public static final Logger LOGGER = LogManager.getLogger();

    public AeronauticsDeliveryQuests(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[TNM Quests] Loading TNM Aeronautics Quests...");

        // Register our blocks and items
        ModBlocks.register(modEventBus);

        // Register the common configuration
        modContainer.registerConfig(ModConfig.Type.COMMON, ADQConfig.SPEC, "aeronautics_delivery_quests.toml");

        // Initialize Schematic Manager (loads NBT defaults)
        ADQSchematicManager.init();

        modEventBus.addListener(this::addCreative);

        LOGGER.info("[TNM Quests] Loaded successfully!");
    }

    private void addCreative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.DELIVERY_QUESTS_TABLE_ITEM.get());
        }
    }
}
