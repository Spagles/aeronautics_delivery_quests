package com.ladderstar.adq;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Tracks Sable sublevels that split off an active quest's cargo body.
 *
 * When a physics contraption is broken into pieces, Sable keeps the original
 * sublevel UUID on one piece and allocates a brand-new sublevel for the detached
 * piece, tagging it via ServerSubLevel#setSplitFrom. That tag is cleared by
 * Sable's tracking system once the split has been networked to players, so this
 * tracker observes sublevel additions and re-checks the tag every tick (for up
 * to 5 seconds) to catch it while it is still set.
 *
 * Matched fragments are recorded on the quest (QuestModel#addCargoFragmentId) so
 * that block protection covers them and CargoAssembler#removeCargo cleans them
 * up when the quest completes, fails, or is cancelled.
 *
 * This class touches Sable classes directly and must only be called when the
 * "sable" mod is loaded (guarded by callers via ModList).
 */
public class CargoFragmentTracker {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final long PENDING_TIMEOUT_TICKS = 100L; // 5 seconds

    private static final Set<ResourceKey<Level>> observedLevels = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedDeque<PendingSubLevel> pending = new ConcurrentLinkedDeque<>();

    private record PendingSubLevel(dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel, long addedGameTime) {}

    /** Called every Overworld tick from ADQEventHandler while Sable is loaded. */
    public static void tick(ServerLevel overworld) {
        long gameTime = overworld.getGameTime();
        if (gameTime % 20L == 0L) {
            attachObservers(overworld.getServer());
        }
        if (!pending.isEmpty()) {
            processPending(gameTime);
        }
    }

    private static void attachObservers(MinecraftServer server) {
        for (ServerLevel sl : server.getAllLevels()) {
            if (!observedLevels.add(sl.dimension())) {
                continue;
            }
            try {
                dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer.getContainer(sl);
                if (container == null) {
                    // Container not ready yet; allow a retry on a later pass.
                    observedLevels.remove(sl.dimension());
                    continue;
                }
                container.addObserver(new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
                    @Override
                    public void onSubLevelAdded(dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
                        if (subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverSub) {
                            pending.add(new PendingSubLevel(serverSub, currentGameTime(serverSub)));
                        }
                    }
                });
                LOGGER.info("[TNM Quests] Watching Sable sublevel container in dimension {} for cargo splits", sl.dimension().location());
            } catch (Throwable t) {
                LOGGER.error("[TNM Quests] Failed to attach Sable sublevel observer for dimension " + sl.dimension().location(), t);
            }
        }
    }

    private static long currentGameTime(dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel) {
        try {
            return subLevel.getLevel().getGameTime();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static void processPending(long gameTime) {
        Iterator<PendingSubLevel> it = pending.iterator();
        while (it.hasNext()) {
            PendingSubLevel entry = it.next();
            dev.ryanhcode.sable.sublevel.ServerSubLevel sub = entry.subLevel();

            UUID splitFrom = null;
            boolean removed = false;
            try {
                splitFrom = sub.getSplitFromSubLevel();
                removed = sub.isRemoved();
            } catch (Throwable t) {
                LOGGER.error("[TNM Quests] Error inspecting pending Sable sublevel for split origin", t);
                it.remove();
                continue;
            }

            if (splitFrom != null) {
                it.remove();
                registerFragment(sub, splitFrom);
            } else if (removed || gameTime - entry.addedGameTime() > PENDING_TIMEOUT_TICKS) {
                // Not a split (a normal assembly), or we missed the tag before Sable cleared it.
                it.remove();
            }
        }
    }

    private static void registerFragment(dev.ryanhcode.sable.sublevel.ServerSubLevel fragment, UUID splitFrom) {
        for (QuestModel quest : QuestGenerator.getAvailableQuests()) {
            if (quest.getAcceptedBy() != null && !quest.isCompleted() && quest.isCargoSubLevel(splitFrom)) {
                if (quest.addCargoFragmentId(fragment.getUniqueId())) {
                    LOGGER.info("[TNM Quests] Cargo for quest '{}' split! Tracking detached fragment sublevel {} (split from {}).",
                            quest.getName(), fragment.getUniqueId(), splitFrom);
                    QuestGenerator.saveQuests();
                }
                return;
            }
        }
    }
}
