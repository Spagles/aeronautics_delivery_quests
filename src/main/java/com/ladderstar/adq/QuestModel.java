package com.ladderstar.adq;

import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.List;

public class QuestModel {
    private final UUID questId;
    private final String name;
    private final String description;
    private int startX;
    private int startY;
    private int startZ;
    private final int endX;
    private final int endY;
    private final int endZ;
    private final String weightClass;
    private final double actualWeight;
    private final List<String> rewards;
    private UUID acceptedBy;
    private boolean completed;
    private UUID cargoEntityId;
    private boolean cargoPickedUp;
    private long acceptedTime;
    private long creationTime;
    private int originalBlockCount;
    private String schematicName;
    // Sublevels that split off the main cargo body (tracked so they can be cleaned up
    // with the quest). May be null when deserialized from pre-1.1.0 quest files.
    private List<UUID> cargoFragmentIds;

    public QuestModel(UUID questId, String name, String description, BlockPos startingPos, BlockPos endingPos, String weightClass, double actualWeight, List<String> rewards) {
        this.questId = questId;
        this.name = name;
        this.description = description;
        this.startX = startingPos.getX();
        this.startY = startingPos.getY();
        this.startZ = startingPos.getZ();
        this.endX = endingPos.getX();
        this.endY = endingPos.getY();
        this.endZ = endingPos.getZ();
        this.weightClass = weightClass;
        this.actualWeight = actualWeight;
        this.rewards = rewards;
        this.acceptedBy = null;
        this.completed = false;
        this.cargoEntityId = null;
        this.cargoPickedUp = false;
        this.acceptedTime = 0L;
        this.creationTime = System.currentTimeMillis();
        this.originalBlockCount = 0;
        this.schematicName = "";
    }

    public UUID getQuestId() { return questId; }
    public String getName() { return name; }
    public String getDescription() { 
        if (description == null) return "";
        int contractIdx = description.indexOf("Contract details:");
        if (contractIdx != -1) {
            return description.substring(contractIdx);
        }
        String lower = description.toLowerCase();
        if (lower.contains("server_spawn") || lower.contains("biome.") || lower.contains("spawn")) {
            return "Contract details: Safeguard and carry the designated cargo to the neighboring settlement. Physics-certified airship is highly recommended.";
        }
        return description;
    }
    public BlockPos getStartingPos() { return new BlockPos(startX, startY, startZ); }
    public BlockPos getEndingPos() { return new BlockPos(endX, endY, endZ); }
    public String getWeightClass() { return weightClass; }
    public double getActualWeight() { return actualWeight; }
    public List<String> getRewards() {
        return rewards;
    }
    
    public UUID getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(UUID acceptedBy) { this.acceptedBy = acceptedBy; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    
    public UUID getCargoEntityId() { return cargoEntityId; }
    public void setCargoEntityId(UUID cargoEntityId) { this.cargoEntityId = cargoEntityId; }

    public synchronized List<UUID> getCargoFragmentIds() {
        if (cargoFragmentIds == null) {
            cargoFragmentIds = new java.util.ArrayList<>();
        }
        return cargoFragmentIds;
    }

    public synchronized boolean addCargoFragmentId(UUID fragmentId) {
        List<UUID> fragments = getCargoFragmentIds();
        if (fragmentId == null || fragments.contains(fragmentId)) {
            return false;
        }
        fragments.add(fragmentId);
        return true;
    }

    /** True if the given sublevel UUID is the main cargo body or any tracked split fragment. */
    public synchronized boolean isCargoSubLevel(UUID subLevelId) {
        if (subLevelId == null) return false;
        if (subLevelId.equals(cargoEntityId)) return true;
        return cargoFragmentIds != null && cargoFragmentIds.contains(subLevelId);
    }

    public synchronized void clearCargoFragments() {
        if (cargoFragmentIds != null) {
            cargoFragmentIds.clear();
        }
    }

    public boolean isCargoPickedUp() { return cargoPickedUp; }
    public void setCargoPickedUp(boolean cargoPickedUp) { this.cargoPickedUp = cargoPickedUp; }

    public long getAcceptedTime() { return acceptedTime; }
    public void setAcceptedTime(long acceptedTime) { this.acceptedTime = acceptedTime; }

    public long getCreationTime() { return creationTime; }
    public void setCreationTime(long creationTime) { this.creationTime = creationTime; }

    public int getOriginalBlockCount() { return originalBlockCount; }
    public void setOriginalBlockCount(int count) { this.originalBlockCount = count; }

    public String getSchematicName() { return schematicName; }
    public void setSchematicName(String schematicName) { this.schematicName = schematicName; }

    public void setStartingPos(BlockPos pos) {
        this.startX = pos.getX();
        this.startY = pos.getY();
        this.startZ = pos.getZ();
    }

    public double getDistance() {
        double dx = startX - endX;
        double dy = startY - endY;
        double dz = startZ - endZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
