package com.ladderstar.adq.network;

import com.ladderstar.adq.AeronauticsDeliveryQuests;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AeronauticsDeliveryQuests.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModPackets {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AeronauticsDeliveryQuests.MODID);

        // Register Server -> Client packet (Open UI & Sync Quests)
        registrar.playToClient(
            ClientboundQuestSyncPacket.TYPE,
            ClientboundQuestSyncPacket.STREAM_CODEC,
            ClientboundQuestSyncPacket::handle
        );

        // Register Client -> Server packet (Submit Quest Actions)
        registrar.playToServer(
            ServerboundQuestActionPacket.TYPE,
            ServerboundQuestActionPacket.STREAM_CODEC,
            ServerboundQuestActionPacket::handle
        );
    }
}
