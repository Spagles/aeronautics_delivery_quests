# 🛠️ Aeronautics Delivery Quests (ADQ) - Developer & Backend Documentation

This document contains backend, compilation, architecture, and deployment information for developers and server administrators wanting to compile or contribute to **Aeronautics Delivery Quests (ADQ)**.

---

## 💻 Environment & Toolchain Specs

*   **Java Runtime:** Microsoft OpenJDK 21
*   **Minecraft Version:** `1.21.1`
*   **Mod Loader:** `NeoForge 21.1.65`
*   **Build System:** Gradle Wrapper (included)
*   **Key Dependencies (placed in `libs/` for local compilation):**
    *   Create Mod (`create-1.21.1-6.0.10.jar`)
    *   Sable Physics (`sable-neoforge-1.21.1-1.2.2.jar`)
    *   Create: Aeronautics (extracted APIs)
    *   Registrate / Flywheel / Ponder / Simulated APIs

---

## 🏗️ Technical Architecture Details

### Asynchronous Operations
To eliminate structure-lookup tick spikes and chunk-loading freezes on the main server thread, ADQ utilizes asynchronous processing:
- **Asynchronous Search:** Finding nearby villages or structures (using `Level.getChunkSource().getGenerator().findNearestMapStructure()`) and parsing heightmaps is offloaded to a background `ForkJoinPool` thread pool.
- **Main Thread Sync:** Once coordinates are safely calculated, checked against the world border, and the target chunks are loaded, the cargo placement and compilation commands are queued to execute on the main server thread.

### Cargo Spawning & Schematics
- Cargo models are compiled from local `.nbt` schematics placed in `config/aeronautics_delivery_quests/schematics/`.
- On startup, `ADQSchematicManager` verifies if default schematics exist and auto-generates them if missing.
- When a quest is accepted, the mod places these blocks in the world and compiles them using Sable Physics APIs:
  ```java
  SubLevelAssemblyHelper.assembleBlocks(level, minPos, maxPos, sublevelUuid, name);
  ```

### Data Persistence
All data is stored in the server's world folder under `data/`:
- **`adq_quests.json`**: Active quest board states, remaining expiry times, accepted quest parameters, and location coordinates.
- **`adq_cooldowns.json`**: Player action cooldown timestamps to prevent command/action spamming.

---

## 🛠️ Compilation & Local Building

To build the mod from source, execute the Gradle build tasks:

```bash
# Clean previous builds and generate a new production JAR
./gradlew clean build
```

The compiled archive will be generated at:
`build/libs/aeronautics_delivery_quests-1.0.2.jar`

---

## 📦 Automated CurseForge & Modrinth Publishing

ADQ is configured with the `mod-publish-plugin` to automate releases and establish metadata relations.

### 1. Credentials Setup
Add your API credentials to your global `gradle.properties` (located at `~/.gradle/gradle.properties`):

```properties
# CurseForge Integration Credentials
curseforge_project_id=YOUR_PROJECT_ID
curseforge_api_key=YOUR_CURSEFORGE_API_KEY
```

### 2. Supported Platform Relations
The publishing plugin automatically configures the uploaded `.jar` with compatibility metadata and registers the following required dependencies on CurseForge:
- `create` (Create Mod)
- `create-aeronautics` (Create: Aeronautics)

### 3. Publishing Commands
To run checks or publish:

*   **Dry Run Upload (Simulation):** Verify credentials, dependencies, and payload structure without doing a live upload:
    ```bash
    ./gradlew publishMods --dry-run
    ```
*   **Live Release:** Build and upload a new stable version to CurseForge:
    ```bash
    ./gradlew publishMods
    ```
