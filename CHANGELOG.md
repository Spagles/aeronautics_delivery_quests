### TNM Aeronautics Quests - v1.1.0 Changelog

#### TNM Rebranding
- **New Name**: *Aeronautics Delivery Quests (ADQ)* is now **TNM Aeronautics Quests**. The display name, mod description, in-game chat prefix (`[ADQ]` → `[TNM Quests]`), and all documentation have been updated.
- **Full Save Compatibility**: The mod ID remains `aeronautics_delivery_quests`, so existing worlds, block registries, `aeronautics_delivery_quests.toml` config, `custom_quests.json`, and saved quest/cooldown data all carry over untouched. The `/adq` command is unchanged.

#### Cargo Protection Overhaul
- **True Invulnerability**: With `enableCargoInvulnerability` on, cargo is now protected from *all* destruction sources in both the Sable sublevel dimension and the Overworld spawn region — player breaks, explosions (previously unprotected in the Overworld), and mob block-destruction. The Overworld protection region is now correctly sized from the quest's schematic instead of a hardcoded 3×3×3 box.
- **No Cargo Item Drops — Ever**: Regardless of the invulnerability setting, destroyed cargo blocks never drop their items. In breakable mode the block is removed (and still reduces the delivery payout), but yields no loot.
- **Split Fragment Tracking**: When a cargo contraption is fractured into multiple Sable physics bodies, the detached pieces are now detected (via Sable's split-from marker) and recorded on the quest. Fragments inherit full cargo block protection, and are removed together with the main cargo body when the quest completes, fails, or is cancelled — no more permanent debris. The quest continues to target the main body, and blocks lost to split-off pieces count as missing mass for reward scaling.

#### Build System & Metadata Updates
- **NeoForge Build Target**: Bumped the build target from NeoForge 21.1.65 to 21.1.236 (minimum supported version remains 21.1.65).
- **Modernized Dependency Declarations**: Replaced the legacy Forge-style `mandatory=true` dependency syntax in `neoforge.mods.toml` with NeoForge's `type="required"`.
- **Publishing Plugin**: Updated `me.modmuss50.mod-publish-plugin` from `2.0.0-beta.1` to stable `2.1.1`.
- **Portable Builds**: Removed a machine-specific `org.gradle.java.home` path from the committed `gradle.properties` (set it in your local `~/.gradle/gradle.properties` if needed).
- **Deprecation Cleanup**: Removed the deprecated-for-removal `bus = ...` parameter from `@EventBusSubscriber` annotations (NeoForge now infers the correct bus automatically), producing a warning-free compile.

---

### Aeronautics Delivery Quests (ADQ) - v1.0.3 Changelog

#### GUI Polish & Interface Polish
- **Ledger Cooldown Rename**: Changed the clipboard label "Cooldown" to "Next Quest In" to make cooldown status clearer.
- **Route & Mass Line Splitting**: Formatted the quest card text so that Mass and Route are on separate lines per quest card, removing the vertical pipe (`|`) character.
- **Flight Manual Simplification**: Simplified the Flight Manual screen to show a unified description help section instead of the multi-step gameplay rules.

#### Predefined Coordinate Support (custom_quests.json)
- **pickupPos and dropoffPos Parameter**: Added optional `pickupPos` and `dropoffPos` string parameters (blank by default on all default quests).
- **Coordinate Formats**: Supports `x,y,z` or `x,z` formats (queries the surface heightmap when Y is omitted or 0). If valid coordinates are specified, quest generation skips the async structure search and generates the quest at the exact location.

#### Aeronautics Recipe Integration & Bug Fixes
- **Table Crafting Recipe**: Updated the crafting recipe for the delivery quests table to require a Contraption Diagram (`simulated:contraption_diagram`) at the top, a Compass (`minecraft:compass`) in the middle, and any wood slab (`#minecraft:wooden_slabs` tag) at the bottom.
- **Occlusion Fix**: Added `.noOcclusion()` properties to the delivery quests table block, fixing the see-through ground bug beneath it.

---

### Aeronautics Delivery Quests (ADQ) - v1.0.2 Changelog

#### Quest & Location Generation Configuration Toggles
- **Generation Mode Toggle**: Replaced the sliding scale `customQuestsChance` with an enum-based `questGenerationMode` config toggle (`CUSTOM` or `PROCEDURAL`).
  - *CUSTOM*: Spawns structured quests exactly as defined in `custom_quests.json`.
  - *PROCEDURAL*: Dynamically mixes names, descriptions, mass classes, schematics, and rewards from across the pool of valid templates in `custom_quests.json`.
- **Location Mode Selector**: Replaced `randomQuestGen` with an enum-based `questLocationMode` config toggle (`VILLAGE`, `ANY_STRUCTURE`, or `RANDOM`).
  - *ANY_STRUCTURE*: Performs an Overworld-wide search using the registries to locate any valid registered structure (top level only) for cargo pickup and delivery.

#### Expanded Default Assets & Balanced Economy
- **6 Default Cargo Schematics**: Expanded the programmatically generated cargo NBT schematics list to 6, adding `light_food_crate`, `medium_ore_crate`, and `heavy_industrial_boiler`.
- **6 Default Packaged Quests**: Expanded example templates to 6 default quests in `custom_quests.json`.
- **Balanced Emerald Economy**: Reduced default emerald payouts for all 6 example quests to a balanced range of 10–50 emeralds. Scaled default config payouts for procedural generation to 15 (Light), 30 (Medium), and 50 (Heavy).

#### Command Cooldowns & Concurrency Safety
- **5-Second Command Cooldown**: Enforces a 5-second cooldown on all player `/adq` command executions.
- **Quest Generation Lock**: Implemented an execution lock (`AtomicBoolean`) to prevent starting quest generation twice concurrently, with dynamic play button greying out on the client UI.

#### Config Auto-Healing
- **Auto-Healing Configuration**: If an outdated version of `custom_quests.json` is detected (with old legacy emerald payouts of `300` or `640`), it is automatically upgraded and rewritten with the balanced 1.0.2 rewards on server startup.

#### Quest Board Clipboard UI Polish
- **Multi-Reward Display**: Enhanced the Quest Board clipboard to display all reward items. Details are parsed into a clean comma-separated list and wrapped dynamically to fit within ledger slots and the active quest details view.
- **Ledger Spacing Refactor**: Reduced quest items displayed per page from 3 to 2, scaling card heights to 68px (originally 48px) to cleanly prevent reward text from spilling over.
- **Dynamic State Updates**: Accept buttons refresh active states dynamically when a player's quest cooldown ticks down to zero.

#### C2ME Concurrency & Thread Safety
- **Asynchronous Random Safety**: Replaced thread-bound `level.getRandom()` with isolated `RandomSource.create()` inside the background quest generation thread. This completely resolves threading crashes and `ConcurrentModificationException` conflicts when running alongside the **C2ME (Concurrent Chunk Management Engine)** mod.

---

### Aeronautics Delivery Quests (ADQ) - v1.0.1 Changelog

#### Core Mechanics & Standalone Architecture
- **Standalone Mod**: Stripped all FTB Chunks, Teams, and Library requirements from compilation, config, and runtime scripts, making ADQ 100% independent.
- **Safe Air Spawning**: Re-engineered physical spawning to check solid surface footprints and ensure 100% empty air columns. Cargo spawns 3 blocks in the air and drops cleanly under gravity.
- **Vertical Spawning Safety Fallback**: Added a sky fallback (spawns cargo 4 blocks above solid ground) if flat ground is unavailable, preventing clipping into village structures/ground. Removed legacy floating grass block placements.

#### Custom Quest JSON Configurations
- **Admins JSON Pool (`custom_quests.json`)**: Added a dedicated GSON database at `config/aeronautics_delivery_quests/custom_quests.json` (auto-generates example templates on startup) allowing custom names, descriptions, mass classes, NBT templates, and rewards.
- **Mix Ratio Tuning**: Added `customQuestsChance` (default 0.5) double config parameter under `ADQConfig` to set the percentage ratio of custom JSON quests versus procedural generation.
- **Instant Hot-Reloads**: Integrated JSON reloader directly into the `/adq reload` command and chest-GUI reload button for real-time refreshes without a server restart.

#### Exploit Protection & Chest-GUI Command Panel
- **Anti-Loot Balance**: Swapped `minecraft:netherite_block` in the Heavy schematic with `minecraft:polished_deepslate` and programmed the manager to overwrite legacy/OP config files on startup. Guaranteed all default schematics use exactly `simulated:rope_connector`.
- **Chest-GUI Redesign**: Restructured the board to limit quest maps to slots 0–17 (respecting `maxActiveQuestsPerBoard`). Converted the bottom row (slots 18–25) into a command dashboard for Reissuing Compass, Cancelling Contract, and OP-level 2 Admin Commands (Generate, Delete All, Reload).
