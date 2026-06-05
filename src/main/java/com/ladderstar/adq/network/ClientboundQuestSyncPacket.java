package com.ladderstar.adq.network;

import com.ladderstar.adq.QuestModel;
import com.ladderstar.adq.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClientboundQuestSyncPacket(List<QuestModel> quests, long cooldownRemainingSeconds, long nextQuestTimerSeconds, boolean openScreen, boolean isGenerating) implements CustomPacketPayload {

    public static final Type<ClientboundQuestSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("aeronautics_delivery_quests", "quest_sync"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundQuestSyncPacket> STREAM_CODEC = StreamCodec.of(
        ClientboundQuestSyncPacket::write, ClientboundQuestSyncPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleSync(this);
        });
    }

    private static void write(FriendlyByteBuf buf, ClientboundQuestSyncPacket packet) {
        buf.writeLong(packet.cooldownRemainingSeconds);
        buf.writeLong(packet.nextQuestTimerSeconds);
        buf.writeBoolean(packet.openScreen);
        buf.writeBoolean(packet.isGenerating);
        buf.writeInt(packet.quests.size());
        for (QuestModel quest : packet.quests) {
            buf.writeUUID(quest.getQuestId());
            buf.writeUtf(quest.getName());
            buf.writeUtf(quest.getDescription());
            buf.writeBlockPos(quest.getStartingPos());
            buf.writeBlockPos(quest.getEndingPos());
            buf.writeUtf(quest.getWeightClass());
            buf.writeDouble(quest.getActualWeight());
            
            buf.writeInt(quest.getRewards().size());
            for (String reward : quest.getRewards()) {
                buf.writeUtf(reward);
            }
            
            if (quest.getAcceptedBy() != null) {
                buf.writeBoolean(true);
                buf.writeUUID(quest.getAcceptedBy());
            } else {
                buf.writeBoolean(false);
            }
            
            buf.writeBoolean(quest.isCompleted());
            buf.writeLong(quest.getCreationTime());
            buf.writeUtf(quest.getSchematicName() == null ? "" : quest.getSchematicName());
        }
    }

    private static ClientboundQuestSyncPacket read(FriendlyByteBuf buf) {
        long cooldown = buf.readLong();
        long nextQuestTimer = buf.readLong();
        boolean openScreen = buf.readBoolean();
        boolean isGenerating = buf.readBoolean();
        int size = buf.readInt();
        List<QuestModel> quests = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            String desc = buf.readUtf();
            BlockPos startPos = buf.readBlockPos();
            BlockPos endPos = buf.readBlockPos();
            String weightClass = buf.readUtf();
            double actualWeight = buf.readDouble();
            
            int rewardSize = buf.readInt();
            List<String> rewards = new ArrayList<>();
            for (int r = 0; r < rewardSize; r++) {
                rewards.add(buf.readUtf());
            }
            
            QuestModel quest = new QuestModel(id, name, desc, startPos, endPos, weightClass, actualWeight, rewards);
            
            if (buf.readBoolean()) {
                quest.setAcceptedBy(buf.readUUID());
            }
            quest.setCompleted(buf.readBoolean());
            quest.setCreationTime(buf.readLong());
            quest.setSchematicName(buf.readUtf());
            
            quests.add(quest);
        }
        return new ClientboundQuestSyncPacket(quests, cooldown, nextQuestTimer, openScreen, isGenerating);
    }
}
