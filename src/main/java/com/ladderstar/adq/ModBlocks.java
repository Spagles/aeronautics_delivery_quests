package com.ladderstar.adq;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeronauticsDeliveryQuests.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeronauticsDeliveryQuests.MODID);

    public static final DeferredHolder<Block, DeliveryQuestsTableBlock> DELIVERY_QUESTS_TABLE = BLOCKS.register(
        "delivery_quests_table",
        () -> new DeliveryQuestsTableBlock(BlockBehaviour.Properties.of()
            .strength(3.0F, 6.0F)
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Item, BlockItem> DELIVERY_QUESTS_TABLE_ITEM = ITEMS.register(
        "delivery_quests_table",
        () -> new BlockItem(DELIVERY_QUESTS_TABLE.get(), new Item.Properties())
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
