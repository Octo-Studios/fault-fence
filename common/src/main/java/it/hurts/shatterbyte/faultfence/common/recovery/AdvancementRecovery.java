package it.hurts.shatterbyte.faultfence.common.recovery;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.mixin.CompositeLootItemConditionAccessor;
import it.hurts.shatterbyte.faultfence.common.mixin.EntityPredicateAccessor;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.entity.PlayerPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class AdvancementRecovery {
    private static final ThreadLocal<Optional<Identifier>> PARENT = new ThreadLocal<>();

    private static final Map<Exception, Failure> FAILURES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Recovery> RECOVERIES = Collections.synchronizedMap(new WeakHashMap<>());

    public static <T> Either<T, Exception> read(ResourceKey<T> key, ResourceOrigin origin, Supplier<Either<T, Exception>> reader) {
        if (!key.isFor(Registries.ADVANCEMENT) || !RecoveryCategory.ADVANCEMENTS.enabled())
            return reader.get();

        var previous = PARENT.get();

        PARENT.set(Optional.empty());

        try {
            var result = reader.get();

            result.ifRight(error -> {
                if (error instanceof IllegalStateException)
                    FAILURES.put(error, new Failure(origin, PARENT.get()));
            });

            return result;
        } finally {
            if (previous == null)
                PARENT.remove();
            else
                PARENT.set(previous);
        }
    }

    public static void parent(JsonElement json) {
        if (PARENT.get() == null || !json.isJsonObject())
            return;

        var parent = json.getAsJsonObject().get("parent");

        PARENT.set(parent == null ? Optional.empty() : Identifier.CODEC.parse(JsonOps.INSTANCE, parent).result());
    }

    public static void skipped(HolderLookup.RegistryLookup<?> registry, Identifier id, Exception error) {
        var failure = FAILURES.get(error);

        if (failure != null)
            RECOVERIES.computeIfAbsent(registry, key -> new Recovery()).failures.put(id, failure);
    }

    public static ImmutableMap<Identifier, AdvancementHolder> prepare(ServerAdvancementManager manager, HolderLookup.Provider registries, ImmutableMap<Identifier, AdvancementHolder> advancements) {
        var pending = RECOVERIES.get(registries.lookupOrThrow(Registries.ADVANCEMENT));

        if (!RecoveryCategory.ADVANCEMENTS.enabled() || pending == null)
            return advancements;

        var recovery = new Recovery();

        recovery.failures.putAll(pending.failures);

        var repaired = new LinkedHashMap<>(advancements);
        var detached = new LinkedHashMap<Identifier, Identifier>();

        for (var holder : advancements.values()) {
            var parent = holder.value().parent();

            if (parent.isEmpty() || !recovery.failures.containsKey(parent.get()))
                continue;

            var missing = parent.get();
            var visited = new LinkedHashSet<Identifier>();

            visited.add(holder.id());

            while (parent.isPresent() && recovery.failures.containsKey(parent.get())) {
                if (!visited.add(parent.get())) {
                    parent = Optional.empty();

                    break;
                }

                parent = recovery.failures.get(parent.get()).parent();
            }

            if (parent.filter(holder.id()::equals).isPresent())
                parent = Optional.empty();

            repaired.put(holder.id(), withParent(holder, parent));

            if (parent.isEmpty())
                detached.put(holder.id(), missing);

            bypassed(recovery, holder.id(), missing, "Bypassed failed parent; new parent: " + parent.map(Identifier::toString).orElse("root"));
        }

        for (var holder : List.copyOf(repaired.values())) {
            if (holder.value().display().isEmpty())
                continue;

            var parent = holder.value().parent();
            var visited = new LinkedHashSet<Identifier>();

            while (parent.isPresent() && visited.add(parent.get())) {
                var ancestor = repaired.get(parent.get());

                if (ancestor == null || ancestor.value().display().isPresent())
                    break;

                var missing = detached.get(ancestor.id());

                if (missing != null) {
                    repaired.put(holder.id(), withParent(holder, Optional.empty()));
                    bypassed(recovery, holder.id(), missing, "Created visible root below a failed parent");

                    break;
                }

                parent = ancestor.value().parent();
            }
        }

        for (var holder : repaired.values()) {
            holder.value().criteria().forEach((name, criterion) -> {
                if (!(criterion.triggerInstance() instanceof SimpleCriterionTrigger.SimpleInstance instance) || instance.player().isEmpty())
                    return;

                var condition = instance.player().get().value();
                var replacement = condition(recovery, holder.id(), condition, Collections.newSetFromMap(new IdentityHashMap<>()));

                if (replacement != condition)
                    recovery.conditions.put(new PlayerAdvancements.TriggerInstanceKey(holder, name), replacement);
            });
        }

        RECOVERIES.put(manager, recovery);

        return ImmutableMap.copyOf(repaired);
    }

    private static AdvancementHolder withParent(AdvancementHolder holder, Optional<Identifier> parent) {
        var advancement = holder.value();
        var display = advancement.display();

        if (parent.isEmpty() && display.isPresent() && display.get().background().isEmpty()) {
            var info = display.get();

            display = Optional.of(new DisplayInfo(info.icon(), info.title(), info.description(),
                    Optional.of(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("gui/advancements/backgrounds/stone"))),
                    info.type(), info.showToast(), info.announceToChat(), info.hidden()));
        }

        return new AdvancementHolder(holder.id(), new Advancement(parent, display, advancement.rewards(), advancement.criteria(),
                advancement.requirements(), advancement.sendsTelemetryEvent(), advancement.name()));
    }

    private static LootItemCondition condition(Recovery recovery, Identifier owner, LootItemCondition condition, Set<LootItemCondition> path) {
        if (!path.add(condition))
            return condition;

        try {
            if (condition instanceof LootItemEntityPropertyCondition property
                    && property.entityTarget() == LootContext.EntityTarget.THIS && property.predicate().isPresent()) {
                var parts = new LinkedHashMap<>(((EntityPredicateAccessor) (Object) property.predicate().get()).faultfence$parts());

                if (!(parts.get(PlayerPredicate.CODEC) instanceof PlayerPredicate player))
                    return condition;

                var requirements = new LinkedHashMap<>(player.advancements());

                for (var id : recovery.failures.keySet()) {
                    if (requirements.remove(id) != null)
                        bypassed(recovery, owner, id, "Removed requirement for failed advancement");
                }

                if (requirements.size() == player.advancements().size())
                    return condition;

                parts.put(PlayerPredicate.CODEC, new PlayerPredicate(player.level(), player.food(), player.gameType(), player.stats(),
                        player.recipes(), requirements, player.lookingAt(), player.input()));

                return new LootItemEntityPropertyCondition(Optional.of(new EntityPredicate(parts)), property.entityTarget());
            }

            if (!(condition instanceof AllOfCondition) && !(condition instanceof AnyOfCondition))
                return condition;

            var terms = ((CompositeLootItemConditionAccessor) condition).faultfence$terms();
            var replacements = terms.stream().map(term -> {
                var replacement = condition(recovery, owner, term.value(), path);

                return replacement == term.value() ? term : Holder.direct(replacement);
            }).toList();

            if (replacements.equals(terms.stream().toList()))
                return condition;

            if (condition instanceof AllOfCondition)
                return AllOfCondition.allOf(HolderSet.direct(replacements));

            var builder = AnyOfCondition.anyOf();

            replacements.forEach(builder::or);

            return builder.build();
        } finally {
            path.remove(condition);
        }
    }

    private static void bypassed(Recovery recovery, Identifier owner, Identifier missing, String message) {
        var failure = recovery.failures.get(missing);
        var origin = new ResourceOrigin(failure.origin().report(), RecoveryCategory.ADVANCEMENTS,
                Registries.ADVANCEMENT.identifier().toString(), owner.toString(), "", "");
        Reports.record(origin, ReportAction.BYPASSED_REFERENCE, message + " " + missing, null,
                List.of(Registries.ADVANCEMENT.identifier() + " " + missing));
        FaultFence.LOGGER.warn("{} {} in advancement {}", message, missing, owner);
    }

    public static LootItemCondition condition(ServerAdvancementManager manager, PlayerAdvancements.TriggerInstanceKey key, LootItemCondition original) {
        var recovery = RECOVERIES.get(manager);

        return recovery == null ? original : recovery.conditions.getOrDefault(key, original);
    }

    private record Failure(ResourceOrigin origin, Optional<Identifier> parent) {
    }

    private static final class Recovery {
        private final Map<Identifier, Failure> failures = new LinkedHashMap<>();
        private final Map<PlayerAdvancements.TriggerInstanceKey, LootItemCondition> conditions = new LinkedHashMap<>();
    }
}
