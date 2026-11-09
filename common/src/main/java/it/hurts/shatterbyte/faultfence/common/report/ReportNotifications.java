package it.hurts.shatterbyte.faultfence.common.report;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class ReportNotifications {
    public static void notify(ServerPlayer player) {
        var report = Reports.latest();

        if (!FaultFence.CONFIG.notifyAdmins || report == null || !Commands.LEVEL_ADMINS.check(player.permissions()))
            return;

        var summary = report.summary();

        if (summary.issueCount() == 0 && !summary.outcome().equals("FAILED"))
            return;

        var language = player.clientInformation().language();

        player.sendSystemMessage(ReportMessages.message(language, "faultfence.report.notification",
                ReportMessages.translate(language, "faultfence.report.outcome." + summary.outcome().toLowerCase(Locale.ROOT))
                        .withStyle(summary.outcome().equals("FAILED") ? ChatFormatting.RED : ChatFormatting.GOLD),
                summary.actions().get(ReportAction.SKIPPED_RESOURCE), summary.actions().get(ReportAction.SKIPPED_ENTRY),
                summary.actions().get(ReportAction.USED_EMPTY_ENTRY), summary.actions().get(ReportAction.USED_LOWER_RESOURCE),
                summary.actions().get(ReportAction.BYPASSED_REFERENCE), summary.actions().get(ReportAction.OBSERVED)));
    }
}
