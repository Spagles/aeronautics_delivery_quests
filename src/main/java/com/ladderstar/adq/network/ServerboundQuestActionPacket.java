package com.ladderstar.adq.network;

import com.ladderstar.adq.QuestBoardMenuHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ServerboundQuestActionPacket(Action action, UUID questId) implements CustomPacketPayload {

    public enum Action {
        ACCEPT, CANCEL, REISSUE, GENERATE, DELETE_ALL, RELOAD, FILL
    }

    public static final Type<ServerboundQuestActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("aeronautics_delivery_quests", "quest_action"));

    public static final StreamCodec<FriendlyByteBuf, ServerboundQuestActionPacket> STREAM_CODEC = StreamCodec.of(
        ServerboundQuestActionPacket::write, ServerboundQuestActionPacket::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QuestBoardMenuHandler.handleServerPacketAction(player, this.action, this.questId);
            }
        });
    }

    private static void write(FriendlyByteBuf buf, ServerboundQuestActionPacket packet) {
        buf.writeEnum(packet.action);
        if (packet.questId != null) {
            buf.writeBoolean(true);
            buf.writeUUID(packet.questId);
        } else {
            buf.writeBoolean(false);
        }
    }

    private static ServerboundQuestActionPacket read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID questId = null;
        if (buf.readBoolean()) {
            questId = buf.readUUID();
        }
        return new ServerboundQuestActionPacket(action, questId);
    }
}
