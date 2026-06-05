package com.ladderstar.adq;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.UUID;

public class DeliveryTracker {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void tick(ServerLevel level) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }

        Iterator<QuestModel> iterator = QuestGenerator.getAvailableQuests().iterator();
        while (iterator.hasNext()) {
            QuestModel quest = iterator.next();
            
            // 1. If quest is unaccepted (on the board), enforce unclaimed expiration timer
            if (quest.getAcceptedBy() == null) {
                long expirationMs = (long) ADQConfig.QUEST_EXPIRATION_TIME.get() * 60L * 1000L;
                if (quest.getCreationTime() > 0 && System.currentTimeMillis() - quest.getCreationTime() >= expirationMs) {
                    LOGGER.info("[ADQ] Unclaimed quest '{}' has expired from the board and is being cycled.", quest.getName());
                    iterator.remove();
                    QuestGenerator.saveQuests();
                    continue;
                }
            }

            // 2. If quest is active, track delivery progress
            if (quest.getAcceptedBy() != null && !quest.isCompleted()) {
                // Enforce accepted time limit check
                long limitMs = (long) ADQConfig.QUEST_TIME_LIMIT.get() * 60L * 1000L;
                if (quest.getAcceptedTime() > 0 && System.currentTimeMillis() - quest.getAcceptedTime() >= limitMs) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(quest.getAcceptedBy());
                    failQuest(level, quest, player);
                    continue;
                }

                ServerPlayer player = level.getServer().getPlayerList().getPlayer(quest.getAcceptedBy());
                if (player != null) {
                    ServerLevel playerLevel = player.serverLevel();
                    if (!quest.isCargoPickedUp()) {
                        // Stage 0: Travel to cargo pickup position
                        BlockPos startPos = quest.getStartingPos();
                        double distance = Math.sqrt(player.blockPosition().distSqr(startPos));

                        // Show HUD feed (Using Create's kpg weight branding)
                        player.sendSystemMessage(Component.literal("§6[ADQ] Travel to Cargo pickup: §f" + (int)distance + " blocks"), true);

                        if (distance <= 15.0) {
                            boolean spawned = CargoAssembler.spawnAndAssembleCargo(player, quest);
                            if (spawned) {
                                quest.setCargoPickedUp(true);
                                QuestGenerator.saveQuests();

                                playerLevel.playSound(null, player.getX(), player.getY(), player.getZ(), 
                                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);

                                player.sendSystemMessage(Component.literal("§a§l[ADQ] Cargo Secured! §7The delivery location has been marked. Quest Compass updated."));
                                player.sendSystemMessage(Component.literal("§7Transport the physical cargo contraption to the destination village at §f" + quest.getEndingPos().toShortString()));

                                if (player.getServer() != null && ADQConfig.ANNOUNCE_SECURE.get()) {
                                    player.getServer().getPlayerList().broadcastSystemMessage(
                                        Component.literal("§6§l[ADQ] §a" + player.getName().getString() + " §7has secured the cargo for contract: §e" + quest.getName() + "§7. Ready for transport!"),
                                        false
                                    );
                                }

                                MarkerManager.updateCompassToDelivery(player, quest);
                            } else {
                                player.sendSystemMessage(Component.literal("§c§l[ADQ] Quest Broken! §fFailed to compile Aeronautics physics contraption. Cancel contract via the Quest Board."));
                            }
                        }
                    } else {
                        // Stage 1: Transport cargo to destination point
                        BlockPos destPos = quest.getEndingPos();
                        dev.ryanhcode.sable.sublevel.ServerSubLevel cargoSubLevel = null;

                        if (ModList.get().isLoaded("sable") && quest.getCargoEntityId() != null) {
                            try {
                                dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container = 
                                    dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer.getContainer(playerLevel);
                                if (container != null) {
                                    dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(quest.getCargoEntityId());
                                    if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel sSub) {
                                        cargoSubLevel = sSub;
                                    }
                                }
                            } catch (Throwable t) {
                                LOGGER.error("[ADQ] Exception looking up Sable sublevel", t);
                            }
                        }

                        if (cargoSubLevel != null) {
                            dev.ryanhcode.sable.companion.math.Pose3dc pose = cargoSubLevel.logicalPose();
                            org.joml.Vector3dc pos = pose.position();
                            BlockPos cargoPos = new BlockPos((int)pos.x(), (int)pos.y(), (int)pos.z());
                            
                            double dx = Math.abs(cargoPos.getX() - destPos.getX());
                            double dz = Math.abs(cargoPos.getZ() - destPos.getZ());
                            double dy = Math.abs(cargoPos.getY() - destPos.getY());
                            double distance = Math.sqrt(cargoPos.distSqr(destPos));

                            player.sendSystemMessage(Component.literal("§a[ADQ] Cargo distance to delivery: §e" + (int)distance + " blocks (horizontal: " + (int)Math.max(dx, dz) + "/4)"), true);

                            if (dx <= 4.0 && dz <= 4.0 && dy <= 6.0) {
                                int remainingBlocks = countRemainingSublevelBlocks(playerLevel, cargoSubLevel, quest);
                                completeQuest(player, quest, iterator, remainingBlocks);
                            }
                        } else {
                            player.sendSystemMessage(Component.literal("§c[ADQ] Cargo physics sublevel missing! Re-locate or cancel contract."), true);
                        }
                    }
                }
            }
        }
    }

    private static int countRemainingSublevelBlocks(ServerLevel level, dev.ryanhcode.sable.sublevel.ServerSubLevel cargoSubLevel, QuestModel quest) {
        try {
            dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot = cargoSubLevel.getPlot();
            if (plot == null) return quest.getOriginalBlockCount();

            BlockPos plotCenter = plot.getCenterBlock();
            StructureTemplate template = ADQSchematicManager.getSchematic(level, quest.getSchematicName());
            if (template == null) return quest.getOriginalBlockCount();

            Vec3i size = template.getSize();
            int W = size.getX();
            int H = size.getY();
            int L = size.getZ();

            ServerLevel sublevelWorld = cargoSubLevel.getLevel();
            BlockPos plotOrigin = plotCenter.offset(-W / 2, 0, -L / 2);

            int nonAirCount = 0;
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    for (int z = 0; z < L; z++) {
                        BlockPos p = plotOrigin.offset(x, y, z);
                        if (!sublevelWorld.getBlockState(p).isAir()) {
                            nonAirCount++;
                        }
                    }
                }
            }

            LOGGER.info("[ADQ] Checked cargo sublevel blocks: {} / {} remaining.", nonAirCount, quest.getOriginalBlockCount());
            return nonAirCount;
        } catch (Throwable t) {
            LOGGER.error("[ADQ] Error counting remaining cargo blocks in sublevel", t);
            return quest.getOriginalBlockCount();
        }
    }

    public static void forceCompleteQuest(ServerPlayer player, QuestModel quest) {
        LOGGER.info("[ADQ] Force-completing quest: {} for player {}", quest.getName(), player.getName().getString());
        completeQuest(player, quest, null, -1);
    }

    private static void completeQuest(ServerPlayer player, QuestModel quest, Iterator<QuestModel> iterator, int remainingBlocks) {
        ServerLevel level = player.serverLevel();
        quest.setCompleted(true);

        LOGGER.info("[ADQ] Quest '{}' completed by {}", quest.getName(), player.getName().getString());

        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);

        player.sendSystemMessage(Component.literal("§a§l§k!!!§r §a§lCONTRACT COMPLETE: §e§l" + quest.getName() + " §a§l§k!!!"));
        player.sendSystemMessage(Component.literal("§7You have successfully navigated the trade route and delivered the cargo!"));

        // Determine reward scale if invulnerability is OFF and some blocks are missing
        double scale = 1.0;
        if (!ADQConfig.ENABLE_CARGO_INVULNERABILITY.get() && remainingBlocks >= 0 && quest.getOriginalBlockCount() > 0) {
            double remainingRatio = (double) remainingBlocks / quest.getOriginalBlockCount();
            scale = 1.0 - (1.0 - remainingRatio) * ADQConfig.REWARD_REDUCTION_SCALE.get();
            scale = Math.max(0.0, Math.min(1.0, scale));

            if (scale < 1.0) {
                player.sendSystemMessage(Component.literal(
                    "§c[ADQ] Cargo damaged! Some blocks are missing upon delivery. Rewards scaled down to §e" + (int)(scale * 100) + "%§c!"
                ));
            }
        }

        if (player.getServer() != null && ADQConfig.ANNOUNCE_COMPLETE.get()) {
            player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6§l[ADQ] §a" + player.getName().getString() + " §7has successfully completed the contract: §e" + quest.getName() + " §7and received their rewards!"),
                false
            );
        }

        MarkerManager.clearMarkers(player, quest);
        CargoAssembler.removeCargo(level, quest);

        // Dispense rewards with scaling
        for (String reward : quest.getRewards()) {
            String[] parts = reward.split(":");
            if (parts.length >= 2) {
                try {
                    String namespace = parts[0];
                    String path = parts[1];
                    int count = parts.length > 2 ? java.lang.Integer.parseInt(parts[2]) : 1;

                    int scaledCount = (int) Math.round(count * scale);
                    if (scaledCount > 0) {
                        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
                        if (item != null && !item.toString().equals("air")) {
                            ItemStack rewardStack = new ItemStack(item, scaledCount);
                            if (!player.getInventory().add(rewardStack)) {
                                player.drop(rewardStack, false);
                            }
                            player.sendSystemMessage(Component.literal("§2- Received Reward: §f" + item.getDescription().getString() + " x" + scaledCount));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[ADQ] Failed to dispense reward: " + reward, e);
                }
            }
        }

        int scaledXp = (int) Math.round(500 * scale);
        if (scaledXp > 0) {
            player.giveExperiencePoints(scaledXp);
            player.sendSystemMessage(Component.literal("§2- Received Reward: §f" + scaledXp + " Experience Points"));
        }

        if (iterator != null) {
            iterator.remove();
        } else {
            QuestGenerator.getAvailableQuests().remove(quest);
        }

        QuestGenerator.saveQuests();
    }

    private static void failQuest(ServerLevel level, QuestModel quest, ServerPlayer player) {
        LOGGER.info("[ADQ] Quest '{}' failed due to time limit expiration.", quest.getName());

        CargoAssembler.removeCargo(level, quest);

        if (player != null) {
            MarkerManager.clearMarkers(player, quest);
            player.sendSystemMessage(Component.literal("§c§l[ADQ] CONTRACT FAILED: §fThe delivery contract time limit has expired! Cargo recalled."));
        }

        if (level.getServer() != null && ADQConfig.ANNOUNCE_FAIL.get()) {
            String pName = player != null ? player.getName().getString() : "A pilot";
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6§l[ADQ] §c" + pName + " §7failed to complete the contract: §e" + quest.getName() + " §7in time."),
                false
            );
        }

        quest.setAcceptedBy(null);
        quest.setCargoEntityId(null);
        quest.setCargoPickedUp(false);
        quest.setAcceptedTime(0);

        QuestGenerator.saveQuests();
    }

    /**
     * Renders visual indicators (particle beacons and outlines) in the world for the active quest pilot.
     * Designed to be highly performant by sending particles directly to the target player only.
     */
    public static void renderParticles(ServerLevel level) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }

        for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
            if (quest.getAcceptedBy() != null && !quest.isCompleted()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(quest.getAcceptedBy());
                // Only render if player is online, in the Overworld, and matching this tick level
                if (player != null && player.serverLevel() == level) {
                    if (!quest.isCargoPickedUp()) {
                        // Stage 0: Cargo Pickup Zone
                        BlockPos startPos = quest.getStartingPos();
                        double distanceSq = player.blockPosition().distSqr(startPos);
                        
                        // Pillar visible up to 300 blocks away (300^2 = 90000)
                        if (distanceSq <= 90000.0) {
                            double px = startPos.getX() + 0.5;
                            double pz = startPos.getZ() + 0.5;
                            
                            // High-density vertical beacon beam (emerald villager sparkles, 2 per layer with spread)
                            for (double yOffset = 1.0; yOffset <= 30.0; yOffset += 1.5) {
                                double py = startPos.getY() + yOffset;
                                level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, false, px, py, pz, 2, 0.1, 0.0, 0.1, 0.0);
                            }
                            
                            // Outlining circle on the ground if close (within 10 chunks: 160 blocks, 160^2 = 25600)
                            if (distanceSq <= 25600.0) {
                                for (int i = 0; i < 32; i++) {
                                    double angle = i * (2.0 * Math.PI / 32.0);
                                    double cx = px + 15.0 * Math.cos(angle);
                                    double cz = pz + 15.0 * Math.sin(angle);
                                    int cy = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int)cx, (int)cz);
                                    level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, false, cx, cy + 0.2, cz, 2, 0.0, 0.1, 0.0, 0.0);
                                }
                            }
                        }
                    } else {
                        // Stage 1: Delivery Dropoff Zone
                        BlockPos destPos = quest.getEndingPos();
                        double distanceSq = player.blockPosition().distSqr(destPos);
                        
                        // Pillar visible up to 300 blocks away
                        if (distanceSq <= 90000.0) {
                            double px = destPos.getX() + 0.5;
                            double pz = destPos.getZ() + 0.5;
                            
                            // High-density vertical beacon beam (cyan GLOW particles, 2 per layer with spread)
                            for (double yOffset = 1.0; yOffset <= 30.0; yOffset += 1.5) {
                                double py = destPos.getY() + yOffset;
                                level.sendParticles(player, ParticleTypes.GLOW, false, px, py, pz, 2, 0.1, 0.0, 0.1, 0.0);
                            }
                            
                            // Outlining 9x13x9 box boundaries if close (within 10 chunks: 160 blocks, 160^2 = 25600)
                            if (distanceSq <= 25600.0) {
                                int[] dxs = {-4, 4};
                                int[] dys = {-6, 6};
                                int[] dzs = {-4, 4};
                                
                                // Render 8 corners (2 particles each for extra visibility)
                                for (int dx : dxs) {
                                    for (int dy : dys) {
                                        for (int dz : dzs) {
                                            level.sendParticles(player, ParticleTypes.GLOW, false, px + dx, destPos.getY() + 0.5 + dy, pz + dz, 2, 0.08, 0.08, 0.08, 0.0);
                                        }
                                    }
                                }
                                
                                // Render middle coordinates on horizontal boundaries
                                for (int dz : dzs) {
                                    for (int dy : dys) {
                                        level.sendParticles(player, ParticleTypes.GLOW, false, px, destPos.getY() + 0.5 + dy, pz + dz, 2, 0.08, 0.08, 0.08, 0.0);
                                    }
                                }
                                for (int dx : dxs) {
                                    for (int dy : dys) {
                                        level.sendParticles(player, ParticleTypes.GLOW, false, px + dx, destPos.getY() + 0.5 + dy, pz, 2, 0.08, 0.08, 0.08, 0.0);
                                    }
                                }
                                
                                // Render middle coordinates on vertical boundaries
                                for (int dx : dxs) {
                                    for (int dz : dzs) {
                                        level.sendParticles(player, ParticleTypes.GLOW, false, px + dx, destPos.getY() + 0.5 - 2, pz + dz, 2, 0.08, 0.08, 0.08, 0.0);
                                        level.sendParticles(player, ParticleTypes.GLOW, false, px + dx, destPos.getY() + 0.5 + 2, pz + dz, 2, 0.08, 0.08, 0.08, 0.0);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
