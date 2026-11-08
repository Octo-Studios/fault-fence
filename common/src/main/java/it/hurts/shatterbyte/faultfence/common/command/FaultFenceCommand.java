package it.hurts.shatterbyte.faultfence.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

public class FaultFenceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faultfence")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("report").executes(context -> report(context.getSource())))
                .then(Commands.literal("explain")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    var report = Reports.latest();

                                    return SharedSuggestionProvider.suggest(report == null ? List.of() : report.ids(), builder);
                                })
                                .executes(context -> explain(context.getSource(), StringArgumentType.getString(context, "id"))))));
    }

    private static int report(CommandSourceStack source) {
        var report = Reports.latest();

        if (report == null) {
            source.sendFailure(Component.translatable("faultfence.report.none"));

            return 0;
        }

        var issueCount = report.issueCount();

        source.sendSuccess(() -> Component.translatable("faultfence.report.summary", report.status(), issueCount), false);
        report.counts().forEach((category, count) -> source.sendSuccess(() -> Component.literal(category + ": " + count), false));

        for (var id : report.ids())
            source.sendSuccess(() -> Component.literal(id), false);

        if (FaultFence.CONFIG.writeReports)
            source.sendSuccess(() -> Component.translatable("faultfence.report.file", report.jsonFile().toString()), false);

        return issueCount;
    }

    private static int explain(CommandSourceStack source, String id) {
        var report = Reports.latest();

        if (report == null) {
            source.sendFailure(Component.translatable("faultfence.report.none"));

            return 0;
        }

        var lines = report.explain(id);

        if (lines.isEmpty()) {
            source.sendFailure(Component.translatable("faultfence.report.unknown", id));

            return 0;
        }

        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));

        return lines.size();
    }
}
