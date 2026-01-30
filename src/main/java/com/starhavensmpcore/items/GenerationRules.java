package com.starhavensmpcore.items;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GenerationRules {
    private final Set<World.Environment> environments;
    private final int minY;
    private final int maxY;
    private final double chunkChance;
    private final double surfaceChance;
    private final int surfaceAttempts;
    private final int buriedAttempts;
    private final int maxVeinSize;
    private final double extraVeinChance;
    private final Set<Material> replaceableBlocks;

    public GenerationRules(Set<World.Environment> environments,
                           int minY,
                           int maxY,
                           double chunkChance,
                           double surfaceChance,
                           int surfaceAttempts,
                           int buriedAttempts,
                           int maxVeinSize,
                           double extraVeinChance,
                           Set<Material> replaceableBlocks) {
        this.environments = environments == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(environments));
        this.minY = minY;
        this.maxY = maxY;
        this.chunkChance = Math.max(0.0, chunkChance);
        this.surfaceChance = Math.max(0.0, surfaceChance);
        this.surfaceAttempts = Math.max(0, surfaceAttempts);
        this.buriedAttempts = Math.max(0, buriedAttempts);
        this.maxVeinSize = Math.max(1, maxVeinSize);
        this.extraVeinChance = Math.max(0.0, extraVeinChance);
        this.replaceableBlocks = replaceableBlocks == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(replaceableBlocks));
    }

    public Set<World.Environment> getEnvironments() {
        return environments;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public double getChunkChance() {
        return chunkChance;
    }

    public double getSurfaceChance() {
        return surfaceChance;
    }

    public int getSurfaceAttempts() {
        return surfaceAttempts;
    }

    public int getBuriedAttempts() {
        return buriedAttempts;
    }

    public int getMaxVeinSize() {
        return maxVeinSize;
    }

    public double getExtraVeinChance() {
        return extraVeinChance;
    }

    public Set<Material> getReplaceableBlocks() {
        return replaceableBlocks;
    }

    public boolean isWorldAllowed(World world) {
        if (world == null) {
            return false;
        }
        if (environments.isEmpty()) {
            return true;
        }
        return environments.contains(world.getEnvironment());
    }

    public boolean isInYRange(int y) {
        if (minY != -1 && y < minY) {
            return false;
        }
        if (maxY != -1 && y > maxY) {
            return false;
        }
        return true;
    }
}
