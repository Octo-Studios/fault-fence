package it.hurts.shatterbyte.faultfence.common.recovery;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Lifecycle;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.ReportedResource;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PackFallbacks {
    private static final ThreadLocal<Attempt> ATTEMPT = new ThreadLocal<>();
    private static final ThreadLocal<Selection> SELECTED = new ThreadLocal<>();

    public static <T> Either<T, Exception> read(ResourceKey<T> key, Resource resource, Function<Resource, Either<T, Exception>> reader) {
        SELECTED.remove();

        if (!FaultFence.CONFIG.fallbackToLowerPacks)
            return reader.apply(resource);

        var tracked = (ReportedResource) resource;
        var manager = tracked.faultfence$manager();

        if (manager == null)
            return reader.apply(resource);

        var primary = attempt(resource, reader, true);
        var category = RecoveryCategory.registry(key);

        if (primary.terminal() || (!category.enabled() && primary.recovery() == RecoveryCategory.OTHER))
            return primary.value();

        if (!category.enabled())
            category = primary.recovery();

        var stack = manager.getResourceStack(Identifier.parse(tracked.faultfence$origin().file()));
        var position = -1;

        for (var i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).source() == resource.source()) {
                position = i;

                break;
            }
        }

        if (position <= 0)
            return primary.recovery() == RecoveryCategory.OTHER ? primary.value() : attempt(resource, reader, false).value();

        var repairable = new ArrayList<Resource>();

        if (primary.recovery() != RecoveryCategory.OTHER)
            repairable.add(resource);

        for (var candidate : stack.subList(0, position).reversed()) {
            var replacement = attempt(candidate, reader, true);

            if (replacement.terminal()) {
                if (replacement.value().left().isPresent())
                    select(key, category, resource, candidate, primary.value().right().orElseThrow(), false);
                else {
                    Reports.record(Reports.origin(resource, category, key.registry().toString(), key.identifier().toString(), ""),
                            ReportAction.SKIPPED_RESOURCE, primary.value().right().orElseThrow());
                    FaultFence.LOGGER.error("Skipped invalid resource {} from pack {}", key.identifier(), resource.sourcePackId(),
                            primary.value().right().orElseThrow());
                }

                return replacement.value();
            }

            Reports.record(Reports.origin(candidate, category, key.registry().toString(), key.identifier().toString(), ""),
                    ReportAction.SKIPPED_RESOURCE, replacement.value().right().orElseThrow());

            if (replacement.recovery() != RecoveryCategory.OTHER)
                repairable.add(candidate);
        }

        for (var candidate : repairable) {
            var replacement = attempt(candidate, reader, false);

            if (replacement.terminal()) {
                if (candidate != resource && replacement.value().left().isPresent())
                    select(key, category, resource, candidate, primary.value().right().orElseThrow(), true);

                return replacement.value();
            }
        }

        return primary.value();
    }

    private static <T> Candidate<T> attempt(Resource resource, Function<Resource, Either<T, Exception>> reader, boolean strict) {
        var previous = ATTEMPT.get();
        var attempt = new Attempt(strict);

        ATTEMPT.set(attempt);

        try {
            return new Candidate<>(reader.apply(resource), attempt.decoded, attempt.recovery);
        } finally {
            if (previous == null)
                ATTEMPT.remove();
            else
                ATTEMPT.set(previous);
        }
    }

    public static boolean defer(RecoveryCategory category) {
        var attempt = ATTEMPT.get();

        if (attempt == null || !attempt.strict)
            return false;

        attempt.recovery = category;

        return true;
    }

    public static void decoded(boolean success) {
        var attempt = ATTEMPT.get();

        if (attempt != null)
            attempt.decoded = success;
    }

    private static void select(ResourceKey<?> key, RecoveryCategory category, Resource rejected, Resource replacement, Exception error, boolean repaired) {
        used(Reports.origin(rejected, category, key.registry().toString(), key.identifier().toString(), ""),
                Reports.origin(replacement, category, key.registry().toString(), key.identifier().toString(), ""), error);

        var knownPack = replacement.knownPackInfo();

        if (repaired)
            knownPack = Optional.empty();

        SELECTED.set(new Selection(key, new RegistrationInfo(knownPack,
                knownPack.filter(pack -> pack.isVanilla()).isPresent() ? Lifecycle.stable() : Lifecycle.experimental())));
    }

    public static RegistrationInfo registrationInfo(ResourceKey<?> key, RegistrationInfo original) {
        var selection = SELECTED.get();

        SELECTED.remove();

        return selection != null && selection.key().equals(key) ? selection.info() : original;
    }

    public static void used(ResourceOrigin rejected, ResourceOrigin replacement, Throwable error) {
        replacement.report().describe(replacement);

        var message = "Used lower pack " + replacement.pack() + " instead of broken pack " + rejected.pack();

        Reports.record(rejected, ReportAction.USED_LOWER_RESOURCE, message, error, List.of());
        FaultFence.LOGGER.warn("{} for {}", message, rejected.node());
    }

    private static final class Attempt {
        private final boolean strict;
        private boolean decoded;
        private RecoveryCategory recovery = RecoveryCategory.OTHER;

        private Attempt(boolean strict) {
            this.strict = strict;
        }
    }

    private record Candidate<T>(Either<T, Exception> value, boolean decoded, RecoveryCategory recovery) {
        private boolean terminal() {
            return value.left().isPresent() || decoded || !(value.right().orElse(null) instanceof IllegalStateException);
        }
    }

    private record Selection(ResourceKey<?> key, RegistrationInfo info) {
    }
}
