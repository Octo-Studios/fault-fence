package it.hurts.shatterbyte.faultfence.common.recovery;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record MissingLootItemCodec(Codec<LootPoolEntryContainer> delegate) implements Codec<LootPoolEntryContainer> {
    @Override
    public <T> DataResult<Pair<LootPoolEntryContainer, T>> decode(DynamicOps<T> ops, T input) {
        if (!RecoveryCategory.LOOT_ENTRIES.enabled() || FaultFence.CONFIG.removeMissingLootEntries)
            return delegate.decode(ops, input);

        var item = missingItem(ops, input);

        if (item.isEmpty())
            return delegate.decode(ops, input);

        if (PackFallbacks.defer(RecoveryCategory.LOOT_ENTRIES))
            return DataResult.error(() -> "Missing loot item " + item.get());

        return decodeEmpty(ops, input).ifSuccess(result -> {
            Reports.record(Reports.current(RecoveryCategory.LOOT_ENTRIES, "loot_entry", ""), ReportAction.USED_EMPTY_ENTRY,
                    "Missing loot item " + item.get() + "; kept its weight, quality, condition and modifier", null, List.of("element " + item.get()));
            FaultFence.LOGGER.error("Replaced missing loot item {} with an empty entry, preserving its weight and conditions", item.get());
        });
    }

    private static <T> Optional<Identifier> missingItem(DynamicOps<T> ops, T input) {
        var map = ops.getMap(input).result();

        if (map.isEmpty())
            return Optional.empty();

        var type = map.get().get("type");
        var name = map.get().get("name");

        if (type == null || name == null)
            return Optional.empty();

        var entryType = Identifier.CODEC.parse(ops, type).result();
        var item = Identifier.CODEC.parse(ops, name).result();

        if (entryType.isEmpty() || !entryType.get().equals(Identifier.withDefaultNamespace("item"))
                || item.isEmpty() || BuiltInRegistries.ITEM.get(item.get()).isPresent())
            return Optional.empty();

        return item;
    }

    private <T> DataResult<Pair<LootPoolEntryContainer, T>> decodeEmpty(DynamicOps<T> ops, T input) {
        return ops.mergeToMap(input, ops.createString("type"), ops.createString("minecraft:empty"))
                .flatMap(repaired -> delegate.decode(ops, repaired));
    }

    @Override
    public <T> DataResult<T> encode(LootPoolEntryContainer input, DynamicOps<T> ops, T prefix) {
        return delegate.encode(input, ops, prefix);
    }

    @Override
    public Codec<List<LootPoolEntryContainer>> listOf() {
        return new EntriesCodec(this, Codec.super.listOf());
    }

    private record EntriesCodec(MissingLootItemCodec entryCodec, Codec<List<LootPoolEntryContainer>> delegate) implements Codec<List<LootPoolEntryContainer>> {
        @Override
        public <T> DataResult<Pair<List<LootPoolEntryContainer>, T>> decode(DynamicOps<T> ops, T input) {
            if (!RecoveryCategory.LOOT_ENTRIES.enabled() || !FaultFence.CONFIG.removeMissingLootEntries)
                return delegate.decode(ops, input);

            var entries = ops.getStream(input).result();

            if (entries.isEmpty())
                return delegate.decode(ops, input);

            var valid = new ArrayList<T>();
            var missing = new ArrayList<Identifier>();

            for (var entry : entries.get().toList()) {
                var item = missingItem(ops, entry);

                if (item.isEmpty()) {
                    valid.add(entry);

                    continue;
                }

                if (PackFallbacks.defer(RecoveryCategory.LOOT_ENTRIES))
                    return DataResult.error(() -> "Missing loot item " + item.get());

                var error = entryCodec.decodeEmpty(ops, entry).error();

                if (error.isPresent())
                    return DataResult.error(error.get()::message);

                missing.add(item.get());
            }

            return delegate.decode(ops, missing.isEmpty() ? input : ops.createList(valid.stream())).ifSuccess(result -> missing.forEach(item -> {
                Reports.record(Reports.current(RecoveryCategory.LOOT_ENTRIES, "loot_entry", ""), ReportAction.SKIPPED_ENTRY,
                        "Removed missing loot item " + item + "; remaining loot entries keep their weights", null, List.of("element " + item));
                FaultFence.LOGGER.error("Removed missing loot item {}; remaining loot entries keep their weights", item);
            }));
        }

        @Override
        public <T> DataResult<T> encode(List<LootPoolEntryContainer> input, DynamicOps<T> ops, T prefix) {
            return delegate.encode(input, ops, prefix);
        }
    }
}
