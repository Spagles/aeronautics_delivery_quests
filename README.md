# 🗺️ Aeronautics Delivery Quests (ADQ)
**A high-stakes trade expansion for Create: Aeronautics & Sable**

**Aeronautics Delivery Quests (ADQ)** is a lightweight, high-performance NeoForge 1.21.1 mod that adds procedural hauling contracts to your server. Players can accept quests, travel to remote locations to secure physical cargo, and pilot their custom-built airships to transport these heavy rigid bodies to destination settlements for massive rewards.

Best of all: **ADQ is 100% server-side optional.** Clients do not need to download or install the mod to join, view the custom Quest Board UI, or navigate using calibrated Quest Compasses!

---

## 🚀 Key Features

*   **📦 Dynamic & Custom Quests:** Accessible to players via the **Delivery Quests Table** block (and `/adq` for administrators), players browse available contracts in **Light**, **Medium**, and **Heavy** weight classes with dynamically calculated routes and tailored rewards. Creators can design custom quest entries in `custom_quests.json` using custom NBT templates!
*   **⚖️ Sable Physics Integration:** Dynamically loads schematic templates (such as crates, pallets, and secure containers) and compiles them into **Sable dynamic physics rigid bodies** when secured.
*   **🧭 Virtual Calibration Compasses:** Issues vanilla Quest Compasses that point dynamically to cargo pickups or destination locations. Lost your compass? Retrieve a replacement instantly using the board's GUI control panel inside the table's menu.
*   **🔒 Damage Penalties & Shielding:** Configurable invulnerability (`enableCargoInvulnerability`). When off (default), cargo blocks can break, and any missing mass proportionally reduces your reward payout! When on, cargo is fully shielded from breakages, explosions, and mob griefing.
*   **⏳ Board Expirations & Player Cooldowns:** Unaccepted contracts on the board cycle out after 60 minutes. A 1-hour player cooldown prevents quest-spamming (automatically bypassed for admins/operators).

---

## 🔍 For the Skeptics (FAQ)

### 💻 Does this require client-side installs?
**No.** All menus, tooltips, custom Quest Compasses, and waypoints are driven by server-side vanilla components, custom lore styling, and standard packets. Clients running completely vanilla (or without the mod installed) can join, accept quests, navigate, and claim rewards with zero issues.

### 📉 Will this cause tick lag or TPS spikes during generation?
**No.** To prevent the classic "structure-lookup tick spikes," all village searches and pathfinding calculations are run on **fully asynchronous background threads** (`ForkJoinPool`). Only the final block placement is scheduled on the main thread, keeping your server running at a solid 20 TPS.

### 🛡️ Will this block players from modifying their own airships?
**No.** The block protection is highly targeted. It strictly intercepts block-breaking inside matching Sable SubLevel UUIDs or Overworld pickup coordinates corresponding to an *active delivery quest*, and only triggers if `enableCargoInvulnerability` is enabled in the config. Normal airships, blocks, and structures remain 100% destructible.

### 🌀 What happens if cargo spawns outside the world border, underground, or inside buildings?
*   **Safe Air-Column Spawning**: The mod scans a horizontal $33\times33$ area around the start coordinates to find a solid, flat footprint. It checks that the entire spawn space (and the air blocks below it) is 100% empty air, spawning the cargo 3 blocks in the air so it falls cleanly under gravity without clipping or breaking blocks.
*   **Vertical Safety Fallback**: If no flat terrain is found nearby, the mod safely spawns the cargo 4 blocks above the highest solid block in the original target coordinates, guaranteeing it never clips inside ground structures or houses.
*   **Border Buffer**: The engine enforces a strict **150-block safety buffer** from your world border. If a potential spawn is too close to or outside the border, it is aborted instantly.

### 💾 Does it survive server restarts?
**Yes.** All active quests, accepted states, cargo positions, and player cooldowns are persisted in GSON JSON registries (`adq_quests.json` and `adq_cooldowns.json`) inside the server's world save folder. Stale compasses are also automatically purged on player login to handle offline quest expirations.

---

## 🛠️ Command Reference

> [!NOTE]
> All `/adq` commands require operator permissions (Level 2+). Regular players must use the physical **Delivery Quests Table** block to open the board, cancel contracts, or reissue compasses.

*   `/adq` - `[Admin]` Open the virtual Aeronautics Quest Board & command control panel.
*   `/adq cancel` - `[Admin]` Abandon your current contract (recalls physical cargo and clears compasses).
*   `/adq compass` - `[Admin]` Re-issue your Quest Delivery Compass (only if on an active quest and you don't already have one).
*   `/adq generate` - `[Admin]` Force-triggers a background procedural quest generation.
*   `/adq complete` - `[Admin]` Force-completes your active delivery contract.
*   `/adq delete <index>` - `[Admin]` Cleanly force-deletes a specific board quest.
*   `/adq deleteall` - `[Admin]` Purge all quests from the board.
*   `/adq reload` - `[Admin]` Reload quests, player cooldowns, and custom quest templates from disk.


