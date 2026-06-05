# Agent Coordination Codex

This document synchronizes the cooperative efforts of the engineering agents working on **Aeronautics Delivery Quests (ADQ)**.

---

## Active Agents & Roles

- **Agent Gemini** (Current Driver): Initiating project setup, Gradle toolchain bootstrapping, metadata configuration, and core Java structures.
- **Agent Claude** (Partner Critic / Auditor): Focuses on logic verification, mod compatibility, edge case analysis, and test suites.
- **Agent Codex** (Memory & Logic Engine): Maintains structural API consistency, dependencies mapping, and compilation audits.

---

## Mod Architecture & Specifications

### 1. Project Parameters
- **Mod Name**: Aeronautics Delivery Quests
- **Mod ID**: `aeronautics_delivery_quests`
- **Package**: `com.ladderstar.adq`
- **NeoForge**: `21.1.65` (Minecraft 1.21.1)

### 2. Synchronization State
- **Current Task**: Completed final advanced optimizations (async generation, cargo indestructibility, center-hole cages, log spam elimination, and compass recovery).
- **Environment**: Compiling under Microsoft OpenJDK 21.0.7 runtime.

---

## Active Task Tracking & Progress

| Phase | Description | Status | Owner |
|---|---|---|---|
| **Phase 1** | Project Directory & Gradle Bootstrapping | Completed | Gemini |
| **Phase 2** | Configuration & Main Initialization | Completed | Gemini |
| **Phase 3** | Quest Persistence Engine & Generator | Completed | Gemini |
| **Phase 4** | Virtual Server-Side Chest GUI Menu | Completed | Gemini |
| **Phase 5** | Aeronautics Physics Contraption Spawning | Completed | Gemini |
| **Phase 6** | FTB Chunks Claim Protection Bridge | Completed | Gemini |
| **Phase 7** | Compass & Waypoint Trackers | Completed | Gemini |
| **Phase 8** | Compilation and JAR Verification | Completed | Gemini |
| **Phase 9** | Enhancements and Bug Fixes | Completed | Gemini |
| **Phase 10**| Final Optimizations & Refinements | Completed | Gemini |
| **Phase 11**| Automated CurseForge Publishing Setup | Completed | Gemini |
| **Phase 12**| Proximity Spawning, Chest GUI, & Custom Quests Config | Completed | Gemini |

---

## Dev Logs & Handovers

### [2026-05-27] Initiating Setup (Gemini)
> Gemini has configured the implementation plan and initialized the directory `aeronautics_delivery_quests`. Starting the download of the Gradle Wrapper and creation of the build scripts using Microsoft OpenJDK 21.

### [2026-05-27] Core Setup and Initial structures complete (Gemini)
> Gemini has successfully bootstrapped all Gradle environment wrapper files locally from `tna_server_core` to `aeronautics_delivery_quests`. The metadata `neoforge.mods.toml` was configured with dependencies on Create, Aeronautics, and FTB Chunks. The main mod class `AeronauticsDeliveryQuests.java` and configuration schema `ADQConfig.java` have been fully drafted and initialized. Proceeding to Phase 3.

### [2026-05-27] Quest Model, Persistence, and Generator complete (Gemini)
> Gemini has successfully built the JSON database storage module using Minecraft's integrated GSON library. The quest generator class `QuestGenerator.java` has been created, implementing structure tags querying (`StructureTags.VILLAGE`) and double-layered structure mapping to locate starting and ending villages within a 500-2000 block distance. Procedural quest parameters like weights and rewards were implemented. Proceeding to Phase 4 (Virtual Chest GUI).

### [2026-05-27] Virtual Chest UI Board & Events complete (Gemini)
> Gemini has successfully created the custom Container Menu `QuestBoardMenu.java` extending the GENERIC_9x3 vanilla container type. Clicking items is captured in `QuestBoardMenuHandler.java` which verifies active player quests, accepts contracts, closes menus, and handles ghost item prevention. The `/adq` command and `/adq cancel` commands are fully registered in `ADQEventHandler.java`, along with server starting loaders and periodic world ticks. Proceeding to Phase 5.

### [2026-05-27] Cargo Physics, Towing Claims, Tracking and Delivery complete (Gemini)
> Gemini has successfully built `CargoAssembler.java` which programmatically constructs the structural hollow metal quest cargo with side towing handles, links it to systemic claim protections via FTB Chunks, and triggers dynamic compilations into Aeronautics physics bodies. Set up `MarkerManager.java` calibrating a Lodestone Quest Compass using vanilla `DataComponents.LODESTONE_TRACKER` pointing towards the destination settlement coordinates. Built `DeliveryTracker.java` calculating distance checks, playing challenge completion sounds, clearing trackers, disassembling blocks, and querying registries dynamically to dispense rewards. Proceeding to Phase 8.

### [2026-05-27] Configurable Loot Tables & Final Build success (Gemini)
> Gemini has successfully implemented three customizable lists in the configuration schema (`ADQConfig.java`): `lightRewards`, `mediumRewards`, and `heavyRewards` formatted as standard lists of strings `namespace:item_id:count`. The procedural quest generator (`QuestGenerator.java`) was refactored to dynamically draw from these config values. The build compiled cleanly against Microsoft OpenJDK 21, generating the final verified JAR `aeronautics_delivery_quests-1.0.0.jar` (28.7 KB) in `build/libs/`. ADQ is fully ready for server deployment.

### [2026-05-27] Enhancements and Bug Fixes complete (Gemini)
> Gemini has successfully implemented the 6 requested bug fixes and enhancements:
> 1. Strict 150-block world border safety buffer (`isWellWithinBorder`) for both start and end coordinates.
> 2. Synchronous target chunk loading prior to querying the heightmap, completely fixing the 150+ blocks underground spawn bug.
> 3. Deferring cargo block placement and compilation until the player arrives at the pickup village, ensuring loaded chunks.
> 4. Integrating with official Sable Physics Engine APIs (`SubLevelAssemblyHelper.assembleBlocks` and `container.removeSubLevel` with `SubLevelRemovalReason.REMOVED`) to compile the 3x3x3 copper cage programmatically into a real, flight-ready Sable physics sublevel.
> 5. Unified compass inventory verification and calibration on tick, auto-re-issuing a vanilla Lodestone compass pointing to the current objective if dropped or lost.
> 6. Adding lore and warnings regarding WorldEdit's compass teleportation wand behavior, keeping standard vanilla client compatibility.

### [2026-05-28] FTB Chunks Server Claims & Admin Enhancements (Gemini)
> Gemini has successfully resolved the latest batch of enhancements and bug fixes:
> 1. **Client-Side Death Tooltip Fix**: Swapped the Quest Board slot representation from `RECOVERY_COMPASS` to `PAPER`. This cleanly removes client-side death-marker tooltip overlays (which mistakenly displayed death coordinates, distance, and the untranslated death-biome key `biome.server_spawn.spawn`) and introduces a sleek scroll aesthetic.
> 2. **FTB Chunks Server-Wide Claiming**: Programmatically gets/creates a dedicated server-wide "Server" team and claims/force-loads the physical cargo's plot chunks under it. This protects cargo blocks from being edited or mined by players while keeping the physics object fully moveable.
> 3. **Aeronautics Physics Force-Loading**: Integrated with `create_aeronautics_ftb_chunks` API via `ContraptionForceLoadManager.enablePhysicsForceLoad` using a static Server UUID, ensuring the physics entity continues ticking even in unloaded chunks.
> 4. **OP/Admin Cooldown Bypass**: Added permission checks (`!player.hasPermissions(2)`) to the quest board contract acceptance logic, completely bypassing the 1-hour cooldown for operators and admins.
> 5. **Dimension Ticking & Duplicate Log Fix**: Restricted both the periodic quest generation check and the delivery tracking loop to run strictly on `Level.OVERWORLD`, resolving the bug where tick events firing on loaded SubLevels caused fail/success messages to duplicate 4-8 times. Restructured the delivery tracker to fetch players globally via the server and resolve their levels dynamically, keeping multi-dimension support fully functional.

### [2026-05-28] Automated CurseForge Publishing Setup (Gemini)
> Gemini has successfully configured automated mod publishing to CurseForge:
> 1. **Integrated `mod-publish-plugin`**: Applied the `me.modmuss50.mod-publish-plugin` plugin to `build.gradle`.
> 2. **Configured `publishMods` Task**: Set up the publication block mapping the correct metadata, Minecraft `1.21.1`, and NeoForge mod loader.
> 3. **Established API and Credential Security**: Configured the build script to dynamically query credentials (`curseforge_project_id` and `curseforge_api_key`) via Gradle properties first, falling back to environment variables safely.
> 4. **Wired Automatic Dependencies (Relations)**: Programmed the plugin to automatically register required relationships with CurseForge on upload for: `create` (Create Mod), `create-aeronautics` (Create: Aeronautics), `ftb-chunks-neoforge`, `ftb-teams-neoforge`, and `ftb-library-neoforge`.
> 5. **Documentation**: Updated the main project documentation (`README.md`) with a complete step-by-step tutorial on setting up credentials and using publishing/dry-run tasks.

### [2026-05-29] Proximity Spawning, GUI Redesign, & Custom Quests Config (Gemini)
> Gemini has successfully implemented a comprehensive set of advanced features, safe physical spawning, and customization capabilities:
> 1. **Removed FTB Dependencies**: Stripped all FTB Chunks, FTB Teams, and FTB Library requirements from `build.gradle`, `neoforge.mods.toml`, and the codebase to make the mod fully standalone.
> 2. **Bulletproof Air Spawning**: Refactored the physical cargo assembly to search for flat terrain surfaces with 100% empty air columns. The cargo spawns exactly 3 blocks in the air and drops cleanly under gravity onto the ground without breaking any blocks in the world. Added a safe vertical fallback directly above original coordinates if rugged terrain doesn't provide a flat spot, and removed obsolete floating grass block placement.
> 3. **Non-OP Schematics & Shielding**: Replaced the extremely valuable `minecraft:netherite_block` in the Heavy Secure Container default schematic with `minecraft:polished_deepslate` to prevent looting exploits, and programmed the schematic manager to auto-overwrite legacy/OP config files on startup.
> 4. **Chest-GUI Redesign**: Restricted available quest listings on the quest board to slots 0-17 (respecting the `maxActiveQuestsPerBoard` config). Replaced slots 18-25 on the bottom row with a fully interactive control panel for Reissuing Quest Compass, Cancelling Active Contract, and Admin Operations (Generate, Delete All, Reload) mapped with robust level 2 OP permission gates.
> 5. **Custom Quest Configuration Registry**: Designed and implemented a dedicated `custom_quests.json` file in the standard config folder that auto-generates balanced templates on startup. Added `customQuestsChance` to `ADQConfig` to control the ratio between custom and procedural generation, and hooked it into the core quest reloader to allow real-time instant hot-reloads of custom quests without restarting the server!
