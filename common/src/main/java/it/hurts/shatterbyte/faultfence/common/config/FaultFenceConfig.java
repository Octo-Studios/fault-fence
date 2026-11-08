package it.hurts.shatterbyte.faultfence.common.config;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.shatterlib.module.config.ShatterConfig;
import it.hurts.shatterbyte.shatterlib.module.config.type.annotation.Comment;

public class FaultFenceConfig extends ShatterConfig {
    @Comment("""
            When a resource fails to load, prefer a valid version or previously loaded layer from a lower-priority pack.
            Only affects enabled recovery categories. false disables this fallback without changing normal pack merging.""")
    public boolean fallbackToLowerPacks = true;

    @Comment("Keep valid tag entries when required references are missing. Report missing references and dependency cycles.")
    public boolean tags = true;

    @Comment("""
            Isolate recipe loading errors so other recipes can still load.
            Reject the entire invalid recipe unless a valid version from a lower-priority pack is selected.""")
    public boolean recipes = true;

    @Comment("""
            Isolate advancement loading errors and bypass references to rejected IDs in supported advancement chains.
            Keep the readable part of damaged progress data. Progress for undefined advancements is discarded; skipped advancements grant no rewards.""")
    public boolean advancements = true;

    @Comment("""
            Recover minecraft:item loot entries with missing items according to removeMissingLootEntries.
            When fallbackToLowerPacks is enabled, a valid whole resource from a lower-priority pack takes precedence.""")
    public boolean lootEntries = true;

    @Comment("""
            When lootEntries is enabled: true removes missing-item entries; false replaces them with minecraft:empty while preserving their weight.
            Removing entries may increase the drop chances of the remaining outcomes.""")
    public boolean removeMissingLootEntries = true;

    @Comment("""
            NeoForge only: an invalid recipe_priorities.json entry does not prevent later valid priorities from loading.
            When fallbackToLowerPacks is enabled, an invalid replace layer may be rejected entirely to preserve lower-priority layers.""")
    public boolean recipePriorities = true;

    @Comment("""
            Write JSON reports to logs/faultfence/.
            When false, normal logging and the in-memory report remain available.""")
    public boolean writeReports = true;

    @Comment("Notify administrators about reported problems when they join and after a server /reload.")
    public boolean notifyAdmins = true;

    @Override
    public String getName() {
        return FaultFence.MODID;
    }

    @Override
    public int getSchemaVersion() {
        return 1;
    }
}
