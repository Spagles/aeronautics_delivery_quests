package com.ladderstar.adq.client;

import com.ladderstar.adq.network.ClientboundQuestSyncPacket;
import net.minecraft.client.Minecraft;

public class ClientPacketHandler {

    public static void handleSync(ClientboundQuestSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestBoardScreen boardScreen) {
            boardScreen.updateQuests(packet.quests(), packet.cooldownRemainingSeconds(), packet.nextQuestTimerSeconds(), packet.isGenerating());
        } else if (packet.openScreen()) {
            mc.setScreen(new QuestBoardScreen(packet.quests(), packet.cooldownRemainingSeconds(), packet.nextQuestTimerSeconds(), packet.isGenerating()));
        }
    }
}
