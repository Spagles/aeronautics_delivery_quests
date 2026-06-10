package com.ladderstar.adq;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ==================================================================================
 *                       AERONAUTICS DELIVERY QUESTS - GENERATOR
 * ==================================================================================
 * This class orchestrates the lifecycle, loading, and generation of delivery quests.
 * 
 * 1. GENERATE vs FILL ACTIONS:
 *    - Generate Quest (Command: `/adq generate` or board GUI button):
 *      Queues a single new quest generation task in the background. It locates one suitable
 *      pickup and delivery endpoint and adds the resulting quest to the board.
 *    - Fill Quests (Board GUI button):
 *      Calculates how many slots are empty on the board (based on `maxActiveQuestsPerBoard` config)
 *      and triggers multiple asynchronous generation tasks sequentially to completely fill the board.
 *
 * 2. QUEST GENERATION MODES (ADQConfig.QUEST_GEN_MODE):
 *    - CUSTOM Mode:
 *      Directly spawns quests exactly as authored in 'config/aeronautics_delivery_quests/custom_quests.json'.
 *      Each quest is copied into the game with its exact defined name, description, weight, rewards, and schematic.
 *    - PROCEDURAL Mode:
 *      Mixes and matches components. It randomly draws names, descriptions, weights, schematics,
 *      and rewards from different templates loaded from 'custom_quests.json'.
 *    - Automatic Fallback:
 *      If 'custom_quests.json' is empty, missing, or corrupt, both modes automatically fall back
 *      to using built-in procedural defaults (featuring 6+ default schematics and 5+ default quest templates).
 *
 * 3. QUEST LOCATION MODES (ADQConfig.QUEST_LOCATION_MODE):
 *    - VILLAGE:
 *      Searches for structures matching the '#minecraft:village' tag in the Overworld.
 *    - ANY_STRUCTURE:
 *      Searches for any registered Overworld structure (surface/top-level only).
 *    - RANDOM:
 *      Picks random coordinates inside the computed search bounds. Chunks are loaded to query
 *      the world heightmap and place the quest cargo safely on the surface.
 *
 * 4. CONCURRENCY & THREAD SAFETY:
 *    - An AtomicBoolean lock ('isGenerating') guards the asynchronous execution.
 *    - While a quest is generating in the background, further generation commands/calls
 *      are discarded to prevent concurrent structure queries from lagging the server.
 *    - When generation starts/finishes, a sync packet is broadcast to all active players
 *      to update their client GUI button states (e.g. greying out the Generate button).
 * ==================================================================================
 */
public class QuestGenerator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<QuestModel> availableQuests = new ArrayList<>();
    private static final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private static final List<CustomQuestTemplate> customTemplates = new ArrayList<>();
    private static Path questFilePath;
    private static Path cooldownFilePath;
    private static final java.util.concurrent.atomic.AtomicBoolean isGenerating = new java.util.concurrent.atomic.AtomicBoolean(false);

    public static boolean isGenerating() {
        return isGenerating.get();
    }

    public static class CustomQuestTemplate {
        public String name;
        public String description;
        public String schematicName;
        public String weightClass;
        public double actualWeight;
        public List<String> rewards;
        public String pickupPos = "";
        public String dropoffPos = "";

        public CustomQuestTemplate() {}

        public CustomQuestTemplate(String name, String description, String schematicName, String weightClass, double actualWeight, List<String> rewards) {
            this(name, description, schematicName, weightClass, actualWeight, rewards, "", "");
        }

        public CustomQuestTemplate(String name, String description, String schematicName, String weightClass, double actualWeight, List<String> rewards, String pickupPos, String dropoffPos) {
            this.name = name;
            this.description = description;
            this.schematicName = schematicName;
            this.weightClass = weightClass;
            this.actualWeight = actualWeight;
            this.rewards = rewards;
            this.pickupPos = pickupPos != null ? pickupPos : "";
            this.dropoffPos = dropoffPos != null ? dropoffPos : "";
        }
    }

    public static final List<CustomQuestTemplate> DEFAULT_TEMPLATES = List.of(
        new CustomQuestTemplate(
            "Secret Industrial Core",
            "A highly critical delivery containing sensitive mechanical components. Keep the shipment secure and intact!",
            "medium_machinery_pallet",
            "Medium",
            3500.0,
            List.of("minecraft:emerald:35", "create:mechanical_arm:1")
        ),
        new CustomQuestTemplate(
            "High-Value Vault Transport",
            "Transport a reinforced secure container containing corporate assets. Heavy and highly guarded!",
            "heavy_secure_container",
            "Heavy",
            9000.0,
            List.of("minecraft:emerald:50", "minecraft:gold_ingot:8")
        ),
        new CustomQuestTemplate(
            "Emergency Food Supplies",
            "Deliver urgent food rations to a starving village. Fast transport is requested.",
            "light_food_crate",
            "Light",
            1200.0,
            List.of("minecraft:emerald:15", "minecraft:bread:16")
        ),
        new CustomQuestTemplate(
            "Standard Copper Shipment",
            "A shipment of raw copper ores for industrial smelting.",
            "medium_ore_crate",
            "Medium",
            4000.0,
            List.of("minecraft:emerald:25", "create:copper_casing:4")
        ),
        new CustomQuestTemplate(
            "Industrial Boiler Delivery",
            "Transport a massive industrial boiler unit to the high-altitude power station.",
            "heavy_industrial_boiler",
            "Heavy",
            11000.0,
            List.of("minecraft:emerald:45", "create:fluid_tank:2")
        ),
        new CustomQuestTemplate(
            "Standard Cargo Haul",
            "A simple transport contract of standard copper components.",
            "light_cargo_crate",
            "Light",
            1500.0,
            List.of("minecraft:emerald:20", "create:cogwheel:4")
        )
    );

    /**
     * Loads custom quest templates from config/aeronautics_delivery_quests/custom_quests.json.
     * If the file is missing or outdated (e.g., legacy high emerald counts), it regenerates
     * the file with updated balanced example templates.
     */
    public static synchronized void loadCustomTemplates() {
        customTemplates.clear();
        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("aeronautics_delivery_quests");
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            Path customQuestsPath = configDir.resolve("custom_quests.json");
            boolean needsGen = !Files.exists(customQuestsPath);
            if (Files.exists(customQuestsPath)) {
                try {
                    String content = new String(Files.readAllBytes(customQuestsPath));
                    if (content.contains("minecraft:emerald:300") || content.contains("minecraft:emerald:640")) {
                        needsGen = true;
                        LOGGER.info("[ADQ] Outdated custom_quests.json detected. Overwriting with updated 1.0.2 economy values.");
                    }
                } catch (Exception e) {
                    needsGen = true;
                }
            }

            if (needsGen) {
                try (Writer writer = Files.newBufferedWriter(customQuestsPath)) {
                    GSON.toJson(DEFAULT_TEMPLATES, writer);
                }
                LOGGER.info("[ADQ] Generated example custom_quests.json template.");
            }
            
            try (Reader reader = Files.newBufferedReader(customQuestsPath)) {
                List<CustomQuestTemplate> loaded = GSON.fromJson(reader, new TypeToken<List<CustomQuestTemplate>>(){}.getType());
                if (loaded != null) {
                    customTemplates.addAll(loaded);
                }
                LOGGER.info("[ADQ] Loaded {} custom quest templates from custom_quests.json", customTemplates.size());
            }
        } catch (Exception e) {
            LOGGER.error("[ADQ] Failed to load custom quest templates", e);
        }
    }

    public static List<QuestModel> getAvailableQuests() {
        return availableQuests;
    }

    public static void init(ServerLevel level) {
        try {
            Path rootPath = level.getServer().getWorldPath(LevelResource.ROOT);
            questFilePath = rootPath.resolve("adq_quests.json");
            cooldownFilePath = rootPath.resolve("adq_cooldowns.json");
            loadQuests();
            loadCooldowns();
        } catch (Exception e) {
            LOGGER.error("[ADQ] Failed to initialize quest file path", e);
        }
    }

    public static synchronized void saveQuests() {
        if (questFilePath == null) return;
        try (Writer writer = Files.newBufferedWriter(questFilePath)) {
            GSON.toJson(availableQuests, writer);
            LOGGER.info("[ADQ] Successfully saved {} quests to {}", availableQuests.size(), questFilePath.getFileName());
        } catch (IOException e) {
            LOGGER.error("[ADQ] Failed to save quests to file", e);
        }
    }

    public static synchronized void loadQuests() {
        loadCustomTemplates();

        if (questFilePath == null || !Files.exists(questFilePath)) {
            availableQuests.clear();
            return;
        }
        try (Reader reader = Files.newBufferedReader(questFilePath)) {
            List<QuestModel> loaded = GSON.fromJson(reader, new TypeToken<List<QuestModel>>(){}.getType());
            availableQuests.clear();
            if (loaded != null) {
                availableQuests.addAll(loaded);
            }
            LOGGER.info("[ADQ] Loaded {} quests from {}", availableQuests.size(), questFilePath.getFileName());
        } catch (IOException e) {
            LOGGER.error("[ADQ] Failed to load quests from file", e);
        }
    }

    public static synchronized void saveCooldowns() {
        if (cooldownFilePath == null) return;
        try (Writer writer = Files.newBufferedWriter(cooldownFilePath)) {
            Map<String, Long> stringMap = new HashMap<>();
            for (Map.Entry<UUID, Long> entry : playerCooldowns.entrySet()) {
                stringMap.put(entry.getKey().toString(), entry.getValue());
            }
            GSON.toJson(stringMap, writer);
            LOGGER.info("[ADQ] Successfully saved {} cooldowns to {}", playerCooldowns.size(), cooldownFilePath.getFileName());
        } catch (IOException e) {
            LOGGER.error("[ADQ] Failed to save cooldowns to file", e);
        }
    }

    public static synchronized void loadCooldowns() {
        playerCooldowns.clear();
        if (cooldownFilePath == null || !Files.exists(cooldownFilePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(cooldownFilePath)) {
            Map<String, Long> stringMap = GSON.fromJson(reader, new TypeToken<Map<String, Long>>(){}.getType());
            if (stringMap != null) {
                for (Map.Entry<String, Long> entry : stringMap.entrySet()) {
                    try {
                        playerCooldowns.put(UUID.fromString(entry.getKey()), entry.getValue());
                    } catch (IllegalArgumentException e) {
                        LOGGER.error("[ADQ] Invalid UUID in cooldown file: " + entry.getKey(), e);
                    }
                }
            }
            LOGGER.info("[ADQ] Loaded {} cooldowns from {}", playerCooldowns.size(), cooldownFilePath.getFileName());
        } catch (IOException e) {
            LOGGER.error("[ADQ] Failed to load cooldowns from file", e);
        }
    }

    public static synchronized long getCooldown(UUID playerId) {
        return playerCooldowns.getOrDefault(playerId, 0L);
    }

    public static synchronized void setCooldown(UUID playerId, long timestamp) {
        playerCooldowns.put(playerId, timestamp);
        saveCooldowns();
    }

    /**
     * Triggers the asynchronous generation of a new quest.
     * 
     * Steps performed:
     * 1. Check if the active quest count has already reached the configured maximum capacity.
     * 2. Perform a thread-safe Compare-and-Set check on the 'isGenerating' lock to guarantee
     *    that only one quest generation thread runs at a time.
     * 3. Sync player UI screens immediately to grey out the generation buttons.
     * 4. Spawn a CompletableFuture task running on the background thread pool to locate suitable
     *    pickup and delivery coordinate pairs using structure search registries or random surface coordinates.
     * 5. Fallback/Finalize on the main Server thread: Load chunk data synchronously, calculate the 
     *    surface height, safe-guard coordinates against the world border, assign the rewards based on
     *    the quest weight class and generation mode, and sync the board state.
     */
    public static void generateNewQuestAsync(ServerLevel level) {
        generateNewQuestAsync(level, null);
    }

    public static void generateNewQuestAsync(ServerLevel level, UUID triggerPlayerUuid) {
        if (availableQuests.size() >= ADQConfig.MAX_ACTIVE_QUESTS.get()) {
            return;
        }
        // Acquire concurrency lock to prevent multiple simultaneous background generation threads
        if (!isGenerating.compareAndSet(false, true)) {
            LOGGER.info("[ADQ] Quest generation is already running. Skipping duplicate invocation.");
            return;
        }
 
        // Resync to all players to update the generator button state immediately (greys out generate buttons)
        level.getServer().execute(() -> QuestBoardMenuHandler.resyncToAllPlayers(level.getServer()));
 
        LOGGER.info("[ADQ] Triggering periodic quest generation asynchronously...");

        boolean useCustom = ADQConfig.QUEST_GEN_MODE.get() == ADQConfig.QuestGenerationMode.CUSTOM;
        List<CustomQuestTemplate> templatesSource = customTemplates.isEmpty() ? DEFAULT_TEMPLATES : customTemplates;
        CustomQuestTemplate selectedTemplate = null;
        ParsedCoords customStart = null;
        ParsedCoords customEnd = null;

        if (useCustom && !templatesSource.isEmpty()) {
            net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create();
            selectedTemplate = templatesSource.get(rand.nextInt(templatesSource.size()));
            if (selectedTemplate != null) {
                customStart = parseCoordinates(selectedTemplate.pickupPos);
                customEnd = parseCoordinates(selectedTemplate.dropoffPos);
            }
        }

        if (selectedTemplate != null && customStart != null && customEnd != null) {
            final CustomQuestTemplate finalTemplate = selectedTemplate;
            final ParsedCoords finalCustomStart = customStart;
            final ParsedCoords finalCustomEnd = customEnd;

            level.getServer().execute(() -> {
                try {
                    BlockPos startingPos = resolvePosition(level, finalCustomStart);
                    BlockPos endingPos = resolvePosition(level, finalCustomEnd);

                    if (!isWellWithinBorder(level, startingPos) || !isWellWithinBorder(level, endingPos)) {
                        announceGenerationFailure(level);
                        return;
                    }

                    UUID questId = UUID.randomUUID();
                    String name = finalTemplate.name;
                    String description = finalTemplate.description;
                    String weightClass = finalTemplate.weightClass;
                    double actualWeight = finalTemplate.actualWeight;
                    List<String> rewards = new ArrayList<>(finalTemplate.rewards);
                    String schematicName = finalTemplate.schematicName;

                    QuestModel quest = new QuestModel(questId, name, description, startingPos, endingPos, weightClass, actualWeight, rewards);
                    quest.setCreationTime(System.currentTimeMillis());
                    quest.setSchematicName(schematicName);

                    synchronized (availableQuests) {
                        availableQuests.add(quest);
                    }
                    saveQuests();

                    LOGGER.info("[ADQ] Generated custom coordinates quest: '{}' [{} class, {}kpg, Schematic: {}] from {} to {}", 
                            name, weightClass, (int)actualWeight, quest.getSchematicName(), startingPos.toShortString(), endingPos.toShortString());
                } catch (Exception e) {
                    LOGGER.error("[ADQ] Error finalising custom coords quest on server thread", e);
                } finally {
                    isGenerating.set(false);
                    if (triggerPlayerUuid != null) {
                        ServerPlayer triggerPlayer = level.getServer().getPlayerList().getPlayer(triggerPlayerUuid);
                        if (triggerPlayer != null) {
                            ADQEventHandler.clearActionCooldown(triggerPlayer, "generate");
                            ADQEventHandler.clearActionCooldown(triggerPlayer, "fill");
                        }
                    }
                    QuestBoardMenuHandler.resyncToAllPlayers(level.getServer());
                }
            });
            return;
        }

        final CustomQuestTemplate finalSelectedTemplate = selectedTemplate;
 
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean scheduledFinalization = false;
            try {
                Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                Optional<HolderSet.Named<Structure>> villageHolderSet = registry.getTag(StructureTags.VILLAGE);

                ADQConfig.QuestLocationMode locMode = ADQConfig.QUEST_LOCATION_MODE.get();
                HolderSet<Structure> targetHolderSet = null;

                if (locMode == ADQConfig.QuestLocationMode.VILLAGE) {
                    if (villageHolderSet.isPresent()) {
                        targetHolderSet = villageHolderSet.get();
                    } else {
                        LOGGER.warn("[ADQ] Village structure tag not found in registry! Aborting quest generation.");
                        return;
                    }
                } else if (locMode == ADQConfig.QuestLocationMode.ANY_STRUCTURE) {
                    List<net.minecraft.core.Holder<Structure>> allHolders = new ArrayList<>();
                    for (var ref : registry.holders().toList()) {
                        allHolders.add(ref);
                    }
                    if (allHolders.isEmpty()) {
                        LOGGER.warn("[ADQ] No structures found in registry! Aborting quest generation.");
                        return;
                    }
                    targetHolderSet = HolderSet.direct(allHolders);
                }

                net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create();
                net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();

                // 1. Calculate Search Center and Radius based on active players in the world
                BlockPos searchCenter = null;
                double R = ADQConfig.MIN_PLAYER_RADIUS.get();

                List<ServerPlayer> players = level.players();
                if (!players.isEmpty()) {
                    // Pick a random player as center base
                    ServerPlayer randomPlayer = players.get(rand.nextInt(players.size()));
                    searchCenter = randomPlayer.blockPosition();

                    if (players.size() >= 2) {
                        double maxDist = 0;
                        for (int i = 0; i < players.size(); i++) {
                            for (int j = i + 1; j < players.size(); j++) {
                                double d = Math.sqrt(players.get(i).blockPosition().distSqr(players.get(j).blockPosition()));
                                if (d > maxDist) {
                                    maxDist = d;
                                }
                            }
                        }
                        R = maxDist + ADQConfig.PLAYER_RADIUS_SCALING.get();
                    }
                } else {
                    // Fallback to server spawn if no players are online
                    searchCenter = level.getSharedSpawnPos();
                }

                LOGGER.info("[ADQ] Searching quest origin around center {} with radius {} blocks.", 
                        searchCenter.toShortString(), (int)R);

                BlockPos startPosRaw = null;
                BlockPos endingPosRaw = null;

                // 2. Find Starting spot within player proximity and world border
                for (int attempt = 0; attempt < 20; attempt++) {
                    double angle = rand.nextDouble() * 2 * Math.PI;
                    double dist = rand.nextDouble() * R;
                    int randomX = searchCenter.getX() + (int)(dist * Math.cos(angle));
                    int randomZ = searchCenter.getZ() + (int)(dist * Math.sin(angle));

                    BlockPos targetOrigin = new BlockPos(randomX, 64, randomZ);
                    if (!isWellWithinBorder(level, targetOrigin)) {
                        continue;
                    }

                    if (locMode == ADQConfig.QuestLocationMode.RANDOM) {
                        double distFromCenter = Math.sqrt(targetOrigin.distSqr(searchCenter));
                        if (distFromCenter >= ADQConfig.MIN_START_DISTANCE.get()) {
                            startPosRaw = targetOrigin;
                            break;
                        }
                    } else {
                        var startResult = level.getChunkSource().getGenerator().findNearestMapStructure(
                                level,
                                targetHolderSet,
                                targetOrigin,
                                64,
                                false
                        );
                        if (startResult != null) {
                            BlockPos foundStart = startResult.getFirst();
                            double distFromCenter = Math.sqrt(foundStart.distSqr(searchCenter));
                            if (distFromCenter >= ADQConfig.MIN_START_DISTANCE.get() && isWellWithinBorder(level, foundStart)) {
                                startPosRaw = foundStart;
                                break;
                            }
                        }
                    }

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (startPosRaw == null) {
                    announceGenerationFailure(level);
                    return;
                }

                int minDistance = ADQConfig.MIN_DISTANCE.get();
                int maxDistance = ADQConfig.MAX_DISTANCE.get();

                // 3. Find Ending spot
                for (int attempt = 0; attempt < 20; attempt++) {
                    double angle = rand.nextDouble() * 2 * Math.PI;
                    double distance = minDistance + rand.nextDouble() * (maxDistance - minDistance);
                    BlockPos targetOrigin = startPosRaw.offset(
                            (int) (distance * Math.cos(angle)),
                            0,
                            (int) (distance * Math.sin(angle))
                    );

                    if (!isWellWithinBorder(level, targetOrigin)) {
                        continue;
                    }

                    if (locMode == ADQConfig.QuestLocationMode.RANDOM) {
                        endingPosRaw = targetOrigin;
                        break;
                    } else {
                        var endResult = level.getChunkSource().getGenerator().findNearestMapStructure(
                                level,
                                targetHolderSet,
                                targetOrigin,
                                64,
                                false
                        );

                        if (endResult != null) {
                            BlockPos foundPos = endResult.getFirst();
                            double distBlocks = Math.sqrt(foundPos.distSqr(startPosRaw));
                            if (distBlocks >= minDistance && isWellWithinBorder(level, foundPos)) {
                                endingPosRaw = foundPos;
                                break;
                            }
                        }
                    }

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (endingPosRaw == null) {
                    announceGenerationFailure(level);
                    return;
                }

                final BlockPos finalStartPosRaw = startPosRaw;
                final BlockPos finalEndPosRaw = endingPosRaw;

                // 4. Dispatch back to main server thread for height placement and register
                scheduledFinalization = true;
                level.getServer().execute(() -> {
                    try {
                        level.getChunkAt(finalStartPosRaw);
                        int startY = level.getHeight(Heightmap.Types.WORLD_SURFACE, finalStartPosRaw.getX(), finalStartPosRaw.getZ());
                        if (startY < level.getMinBuildHeight() + 10) {
                            startY = level.getSeaLevel();
                        }
                        BlockPos startingPos = new BlockPos(finalStartPosRaw.getX(), startY, finalStartPosRaw.getZ());

                        if (!isWellWithinBorder(level, startingPos)) {
                            announceGenerationFailure(level);
                            return;
                        }

                        level.getChunkAt(finalEndPosRaw);
                        int endY = level.getHeight(Heightmap.Types.WORLD_SURFACE, finalEndPosRaw.getX(), finalEndPosRaw.getZ());
                        if (endY < level.getMinBuildHeight() + 10) {
                            endY = level.getSeaLevel();
                        }
                        BlockPos endingPos = new BlockPos(finalEndPosRaw.getX(), endY, finalEndPosRaw.getZ());

                        if (!isWellWithinBorder(level, endingPos)) {
                            announceGenerationFailure(level);
                            return;
                        }

                        UUID questId = UUID.randomUUID();
                        String name;
                        String description;
                        String weightClass;
                        double actualWeight;
                        List<String> rewards = new ArrayList<>();
                        String schematicName;


                        // Case A: CUSTOM Mode. Spawns quests exactly as authored.
                        if (useCustom) {
                            CustomQuestTemplate template = finalSelectedTemplate;
                            if (template == null) {
                                List<CustomQuestTemplate> templatesWithoutCoords = new ArrayList<>();
                                for (CustomQuestTemplate t : templatesSource) {
                                    if (parseCoordinates(t.pickupPos) == null || parseCoordinates(t.dropoffPos) == null) {
                                        templatesWithoutCoords.add(t);
                                    }
                                }
                                if (templatesWithoutCoords.isEmpty()) {
                                    templatesWithoutCoords = templatesSource;
                                }
                                template = templatesWithoutCoords.get(rand.nextInt(templatesWithoutCoords.size()));
                            }
                            name = template.name;
                            description = template.description;
                            weightClass = template.weightClass;
                            actualWeight = template.actualWeight;
                            rewards.addAll(template.rewards);
                            schematicName = template.schematicName;
                        }
                        // Case B: PROCEDURAL Mode. Mixes and matches properties (Name, Desc, Schematic, Rewards)
                        // from different templates randomly to create a hybridized quest.
                        else {
                            CustomQuestTemplate nameTemplate = templatesSource.get(rand.nextInt(templatesSource.size()));
                            CustomQuestTemplate descTemplate = templatesSource.get(rand.nextInt(templatesSource.size()));
                            CustomQuestTemplate weightTemplate = templatesSource.get(rand.nextInt(templatesSource.size()));
                            CustomQuestTemplate schematicTemplate = templatesSource.get(rand.nextInt(templatesSource.size()));
                            CustomQuestTemplate rewardTemplate = templatesSource.get(rand.nextInt(templatesSource.size()));

                            name = nameTemplate.name;
                            description = descTemplate.description;
                            weightClass = weightTemplate.weightClass;
                            actualWeight = weightTemplate.actualWeight;
                            rewards.addAll(rewardTemplate.rewards);
                            schematicName = schematicTemplate.schematicName;
                        }

                        QuestModel quest = new QuestModel(questId, name, description, startingPos, endingPos, weightClass, actualWeight, rewards);
                        quest.setCreationTime(System.currentTimeMillis());
                        quest.setSchematicName(schematicName);

                        synchronized (availableQuests) {
                            availableQuests.add(quest);
                        }
                        saveQuests();

                        LOGGER.info("[ADQ] Generated new quest: '{}' [{} class, {}kpg, Schematic: {}] from {} to {}", 
                                name, weightClass, (int)actualWeight, quest.getSchematicName(), startingPos.toShortString(), endingPos.toShortString());
                    } catch (Exception e) {
                        LOGGER.error("[ADQ] Error finalising quest on server thread", e);
                    } finally {
                        isGenerating.set(false);
                        if (triggerPlayerUuid != null) {
                            ServerPlayer triggerPlayer = level.getServer().getPlayerList().getPlayer(triggerPlayerUuid);
                            if (triggerPlayer != null) {
                                ADQEventHandler.clearActionCooldown(triggerPlayer, "generate");
                                ADQEventHandler.clearActionCooldown(triggerPlayer, "fill");
                            }
                        }
                        QuestBoardMenuHandler.resyncToAllPlayers(level.getServer());
                    }
                });

            } catch (Exception e) {
                LOGGER.error("[ADQ] Error in async quest generator thread", e);
            } finally {
                if (!scheduledFinalization) {
                    isGenerating.set(false);
                    level.getServer().execute(() -> QuestBoardMenuHandler.resyncToAllPlayers(level.getServer()));
                }
            }
        });
    }

    private static void announceGenerationFailure(ServerLevel level) {
        LOGGER.warn("[ADQ] Failed to locate suitable trade routes within distance and world border limits.");
        level.getServer().execute(() -> {
            if (ADQConfig.ANNOUNCE_GEN_FAIL.get()) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal("§6§l[ADQ] §cFailed to procedurally generate a new trade contract. No suitable trade routes found within the world border."),
                    false
                );
            }
        });
    }



    public static class ParsedCoords {
        public final int x;
        public final int y;
        public final int z;
        public final boolean hasY;

        public ParsedCoords(int x, int y, int z, boolean hasY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hasY = hasY;
        }
    }

    private static ParsedCoords parseCoordinates(String coordStr) {
        if (coordStr == null || coordStr.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = coordStr.split(",");
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0].trim());
                int z = Integer.parseInt(parts[1].trim());
                return new ParsedCoords(x, 0, z, false);
            } else if (parts.length == 3) {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                return new ParsedCoords(x, y, z, true);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("[ADQ] Failed to parse coordinates: " + coordStr, e);
        }
        return null;
    }

    private static BlockPos resolvePosition(ServerLevel level, ParsedCoords coords) {
        int x = coords.x;
        int z = coords.z;
        int y;
        if (coords.hasY && coords.y != 0) {
            y = coords.y;
        } else {
            BlockPos tempPos = new BlockPos(x, 64, z);
            level.getChunkAt(tempPos);
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (y < level.getMinBuildHeight() + 10) {
                y = level.getSeaLevel();
            }
        }
        return new BlockPos(x, y, z);
    }

    public static boolean isWellWithinBorder(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        double safetyBuffer = 150.0;
        return pos.getX() >= border.getMinX() + safetyBuffer && pos.getX() <= border.getMaxX() - safetyBuffer &&
               pos.getZ() >= border.getMinZ() + safetyBuffer && pos.getZ() <= border.getMaxZ() - safetyBuffer;
    }
}
