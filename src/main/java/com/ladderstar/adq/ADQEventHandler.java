package com.ladderstar.adq;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@EventBusSubscriber(modid = AeronauticsDeliveryQuests.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ADQEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[ADQ] Server starting. Loading quests...");
        ServerLevel overworld = event.getServer().overworld();
        ADQSchematicManager.loadSchematics(overworld);
        QuestGenerator.init(overworld);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("[ADQ] Registering command structures...");
        registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Only tick on the Overworld to prevent duplicate quest generation/ticking logs across dimensions/sublevels
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }

            // 1. Periodic quest generation check (Every questInterval minutes)
            long gameTime = level.getGameTime();
            long intervalTicks = (long) ADQConfig.QUEST_INTERVAL.get() * 60L * 20L;
            if (gameTime % intervalTicks == 0) {
                QuestGenerator.generateNewQuestAsync(level);
            }

            // 2. Track quest deliveries (every 20 ticks / 1 second)
            if (gameTime % 20 == 0) {
                DeliveryTracker.tick(level);
            }

            // 3. Render delivery/pickup bounds particles (every 10 ticks / 0.5 seconds)
            if (gameTime % 10 == 0) {
                DeliveryTracker.renderParticles(level);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check if player has an active quest
            boolean hasActiveQuest = false;
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (player.getUUID().equals(quest.getAcceptedBy()) && !quest.isCompleted()) {
                    hasActiveQuest = true;
                    break;
                }
            }
            // If they have no active quest, scan their inventory and delete any "Quest Delivery Compass" items
            if (!hasActiveQuest) {
                boolean removed = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(Items.COMPASS)) {
                        Component name = stack.get(DataComponents.CUSTOM_NAME);
                        if (name != null && name.getString().contains("Quest Delivery Compass")) {
                            player.getInventory().setItem(i, ItemStack.EMPTY);
                            removed = true;
                        }
                    }
                }
                if (removed) {
                    player.containerMenu.broadcastChanges();
                    LOGGER.info("[ADQ] Purged orphan Quest Delivery Compass from logging-in player {}", player.getName().getString());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        net.minecraft.world.level.LevelAccessor level = event.getLevel();
        BlockPos pos = event.getPos();

        if (level instanceof ServerLevel serverLevel) {
            String dimPath = serverLevel.dimension().location().getPath();
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (quest.getAcceptedBy() != null && !quest.isCompleted()) {
                    // 1. Check Sable SubLevel dimension via path (only cancel if invulnerability configuration is active!)
                    java.util.UUID cargoId = quest.getCargoEntityId();
                    if (cargoId != null && dimPath.contains(cargoId.toString())) {
                        if (ADQConfig.ENABLE_CARGO_INVULNERABILITY.get()) {
                            event.setCanceled(true);
                            if (event.getPlayer() instanceof ServerPlayer sp) {
                                sp.sendSystemMessage(Component.literal("§c[ADQ] Cargo blocks are protected and cannot be broken."));
                            }
                            return;
                        }
                    }

                    // 2. Check Overworld spawned blocks (Stage 0 cargo blocks before securing)
                    if (serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                        BlockPos startPos = quest.getStartingPos();
                        if (pos.getX() >= startPos.getX() - 1 && pos.getX() <= startPos.getX() + 1 &&
                            pos.getZ() >= startPos.getZ() - 1 && pos.getZ() <= startPos.getZ() + 1 &&
                            pos.getY() >= startPos.getY() && pos.getY() <= startPos.getY() + 2) {
                            event.setCanceled(true);
                            if (event.getPlayer() instanceof ServerPlayer sp) {
                                sp.sendSystemMessage(Component.literal("§c[ADQ] You cannot break quest cargo blocks!"));
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!ADQConfig.ENABLE_CARGO_INVULNERABILITY.get()) return;

        net.minecraft.world.level.LevelAccessor level = event.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            String dimPath = serverLevel.dimension().location().getPath();
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (quest.getAcceptedBy() != null && !quest.isCompleted()) {
                    java.util.UUID cargoId = quest.getCargoEntityId();
                    if (cargoId != null && dimPath.contains(cargoId.toString())) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!ADQConfig.ENABLE_CARGO_INVULNERABILITY.get()) return;

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            String dimPath = serverLevel.dimension().location().getPath();
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (quest.getAcceptedBy() != null && !quest.isCompleted()) {
                    java.util.UUID cargoId = quest.getCargoEntityId();
                    if (cargoId != null && dimPath.contains(cargoId.toString())) {
                        event.getAffectedBlocks().clear();
                        return;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent event) {
        if (!ADQConfig.ENABLE_CARGO_INVULNERABILITY.get()) return;

        if (event.getEntity().level() instanceof ServerLevel serverLevel) {
            String dimPath = serverLevel.dimension().location().getPath();
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (quest.getAcceptedBy() != null && !quest.isCompleted()) {
                    java.util.UUID cargoId = quest.getCargoEntityId();
                    if (cargoId != null && dimPath.contains(cargoId.toString())) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    private static final java.util.Map<String, Long> actionCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean checkAndSetActionCooldown(ServerPlayer player, String action) {
        long now = System.currentTimeMillis();
        String key = player.getUUID().toString() + "_" + action;
        long lastUsed = actionCooldowns.getOrDefault(key, 0L);
        if (now - lastUsed < 5000L) {
            long remainingMs = 5000L - (now - lastUsed);
            double remainingSecs = remainingMs / 1000.0;
            player.sendSystemMessage(Component.literal(String.format("§cPlease wait %.1f seconds before running this command/action again.", remainingSecs)));
            return false;
        }
        actionCooldowns.put(key, now);
        return true;
    }

    public static void clearActionCooldown(ServerPlayer player, String action) {
        String key = player.getUUID().toString() + "_" + action;
        actionCooldowns.remove(key);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("adq")
                .requires(source -> source.hasPermission(2))
                .executes(ADQEventHandler::openBoardCommand)
                .then(Commands.literal("cancel")
                    .executes(ADQEventHandler::cancelQuestCommand)
                )
                .then(Commands.literal("compass")
                    .executes(ADQEventHandler::reissueCompassCommand)
                )
                .then(Commands.literal("reload")
                    .requires(source -> source.hasPermission(2))
                    .executes(ADQEventHandler::reloadCommand)
                )
                .then(Commands.literal("generate")
                    .requires(source -> source.hasPermission(2))
                    .executes(ADQEventHandler::adminGenerateCommand)
                )
                .then(Commands.literal("complete")
                    .requires(source -> source.hasPermission(2))
                    .executes(ADQEventHandler::adminCompleteCommand)
                )
                .then(Commands.literal("delete")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(ADQEventHandler::adminDeleteCommand)
                    )
                )
                .then(Commands.literal("deleteall")
                    .requires(source -> source.hasPermission(2))
                    .executes(ADQEventHandler::adminDeleteAllCommand)
                )
        );
    }

    private static int openBoardCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!checkAndSetActionCooldown(player, "open")) return 0;
            context.getSource().getServer().execute(() -> {
                QuestBoardMenuHandler.openBoard(player);
                clearActionCooldown(player, "open");
            });
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
    }

    private static int reloadCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null) {
                if (!checkAndSetActionCooldown(player, "reload")) return 0;
            }
            QuestGenerator.loadQuests();
            QuestGenerator.loadCooldowns();
            context.getSource().sendSuccess(() -> Component.literal("§a§l[ADQ] Admin: Quests and Cooldowns successfully reloaded from disk!"), true);
            if (player != null) {
                clearActionCooldown(player, "reload");
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to reload ADQ data: " + e.getMessage()));
            return 0;
        }
    }

    private static int cancelQuestCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!checkAndSetActionCooldown(player, "cancel")) return 0;
            boolean found = false;
            
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (player.getUUID().equals(quest.getAcceptedBy()) && !quest.isCompleted()) {
                    // Call assembler to remove cargo entity BEFORE resetting fields!
                    CargoAssembler.removeCargo(context.getSource().getLevel(), quest);
                    // Call marker manager to clear compass and waypoints
                    MarkerManager.clearMarkers(player, quest);

                    quest.setAcceptedBy(null);
                    quest.setCargoEntityId(null);
                    quest.setCargoPickedUp(false);
                    quest.setAcceptedTime(0);

                    QuestGenerator.saveQuests();
                    player.sendSystemMessage(Component.literal("§c§l[ADQ] Quest Canceled: §fThe delivery cargo has been recalled."));
                    
                    if (player.getServer() != null && ADQConfig.ANNOUNCE_CANCEL.get()) {
                        player.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§6§l[ADQ] §c" + player.getName().getString() + " §7has canceled the contract: §e" + quest.getName() + "§7. Cargo recalled."),
                            false
                        );
                    }
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                player.sendSystemMessage(Component.literal("§cYou do not have any active delivery contracts."));
            } else {
                clearActionCooldown(player, "cancel");
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
    }

    private static boolean hasCompassInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.COMPASS)) {
                Component name = stack.get(DataComponents.CUSTOM_NAME);
                if (name != null && name.getString().contains("Quest Delivery Compass")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int reissueCompassCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!checkAndSetActionCooldown(player, "compass")) return 0;
            QuestModel activeQuest = null;
            
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (player.getUUID().equals(quest.getAcceptedBy()) && !quest.isCompleted()) {
                    activeQuest = quest;
                    break;
                }
            }
            
            if (activeQuest == null) {
                player.sendSystemMessage(Component.literal("§cYou do not have any active delivery contracts to reissue a compass for."));
                return 1;
            }

            if (hasCompassInInventory(player)) {
                player.sendSystemMessage(Component.literal("§cYou already possess a Quest Delivery Compass in your inventory!"));
                return 1;
            }
            
            MarkerManager.ensureAndCalibrateCompass(player, activeQuest);
            clearActionCooldown(player, "compass");
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
    }

    private static int adminGenerateCommand(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            if (!checkAndSetActionCooldown(player, "generate")) return 0;
        }
        if (QuestGenerator.getAvailableQuests().size() >= ADQConfig.MAX_ACTIVE_QUESTS.get()) {
            context.getSource().sendFailure(Component.literal("Quest board is already full."));
            return 0;
        }
        if (QuestGenerator.isGenerating()) {
            context.getSource().sendFailure(Component.literal("§cQuest generation is already running. Please wait."));
            return 0;
        }
        QuestGenerator.generateNewQuestAsync(level, player != null ? player.getUUID() : null);
        context.getSource().sendSuccess(() -> Component.literal("§a§l[ADQ] Admin: Procedural quest generation started in background. Check the quest board in a moment."), true);
        return 1;
    }

    private static int adminCompleteCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!checkAndSetActionCooldown(player, "complete")) return 0;
            boolean completed = false;
            
            for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
                if (player.getUUID().equals(quest.getAcceptedBy()) && !quest.isCompleted()) {
                    DeliveryTracker.forceCompleteQuest(player, quest);
                    completed = true;
                    break;
                }
            }
            
            if (!completed) {
                player.sendSystemMessage(Component.literal("§cYou do not have any active delivery contracts to complete."));
            } else {
                clearActionCooldown(player, "complete");
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
    }

    private static int adminDeleteCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null) {
                if (!checkAndSetActionCooldown(player, "delete")) return 0;
            }
            int index = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "index") - 1; // 1-based to 0-based
            List<QuestModel> quests = QuestGenerator.getAvailableQuests();
            
            synchronized (quests) {
                if (index < 0 || index >= quests.size()) {
                    context.getSource().sendFailure(Component.literal("§c[ADQ] Invalid quest index. Please check active quest count."));
                    return 0;
                }
                
                QuestModel quest = quests.get(index);
                ServerLevel level = context.getSource().getLevel();
                
                // Clean up quest (cargo, markers) if active
                if (quest.getAcceptedBy() != null) {
                    ServerPlayer targetPlayer = (ServerPlayer) level.getPlayerByUUID(quest.getAcceptedBy());
                    if (targetPlayer != null) {
                        MarkerManager.clearMarkers(targetPlayer, quest);
                        targetPlayer.sendSystemMessage(Component.literal("§c§l[ADQ] Quest Force Deleted by Admin: §fThe delivery cargo has been recalled."));
                    }
                    CargoAssembler.removeCargo(level, quest);
                }
                
                quests.remove(index);
                QuestGenerator.saveQuests();
                QuestBoardMenuHandler.resyncToAllPlayers(context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.literal("§a§l[ADQ] Admin: Successfully deleted quest '" + quest.getName() + "'."), true);
                if (player != null) {
                    clearActionCooldown(player, "delete");
                }
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminDeleteAllCommand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null) {
                if (!checkAndSetActionCooldown(player, "deleteall")) return 0;
            }
            List<QuestModel> quests = QuestGenerator.getAvailableQuests();
            ServerLevel level = context.getSource().getLevel();
            
            synchronized (quests) {
                int count = quests.size();
                for (QuestModel quest : quests) {
                    if (quest.getAcceptedBy() != null) {
                        ServerPlayer targetPlayer = (ServerPlayer) level.getPlayerByUUID(quest.getAcceptedBy());
                        if (targetPlayer != null) {
                            MarkerManager.clearMarkers(targetPlayer, quest);
                            targetPlayer.sendSystemMessage(Component.literal("§c§l[ADQ] Quest Force Deleted by Admin: §fThe delivery cargo has been recalled."));
                        }
                        CargoAssembler.removeCargo(level, quest);
                    }
                }
                quests.clear();
                QuestGenerator.saveQuests();
                QuestBoardMenuHandler.resyncToAllPlayers(context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.literal("§a§l[ADQ] Admin: Successfully deleted all " + count + " quests."), true);
                if (player != null) {
                    clearActionCooldown(player, "deleteall");
                }
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
