package it.hurts.shatterbyte.faultfence.common.config;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public enum RecoveryCategory {
    TAGS,
    RECIPES,
    ADVANCEMENTS,
    LOOT_ENTRIES,
    RECIPE_PRIORITIES,
    OTHER;

    public boolean enabled() {
        return switch (this) {
            case TAGS -> FaultFence.CONFIG.tags;
            case RECIPES -> FaultFence.CONFIG.recipes;
            case ADVANCEMENTS -> FaultFence.CONFIG.advancements;
            case LOOT_ENTRIES -> FaultFence.CONFIG.lootEntries;
            case RECIPE_PRIORITIES -> FaultFence.CONFIG.recipePriorities;
            case OTHER -> false;
        };
    }

    public static RecoveryCategory registry(ResourceKey<?> key) {
        if (key.equals(Registries.RECIPE) || key.isFor(Registries.RECIPE))
            return RECIPES;

        if (key.equals(Registries.ADVANCEMENT) || key.isFor(Registries.ADVANCEMENT))
            return ADVANCEMENTS;

        return OTHER;
    }
}
