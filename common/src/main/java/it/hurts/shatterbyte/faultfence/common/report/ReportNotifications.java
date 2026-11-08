package it.hurts.shatterbyte.faultfence.common.report;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.viewer.ReportViewer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

public class ReportNotifications {
    public static void notify(ServerPlayer player) {
        var report = Reports.latest();

        if (!FaultFence.CONFIG.notifyAdmins || report == null || !Commands.LEVEL_ADMINS.check(player.permissions()))
            return;

        var count = report.issueCount();

        if (count == 0)
            return;

        var problems = Component.literal(Integer.toString(count)).withStyle(ChatFormatting.GOLD);

        try {
            var address = ReportViewer.start().resolve("#report=" + report.fileName());

            player.sendSystemMessage(ReportMessages.message("faultfence.report.notification", problems,
                    Component.translatable("faultfence.viewer.open").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                            .withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(address)))));
        } catch (IOException error) {
            FaultFence.LOGGER.error("Couldn't start Fault Fence viewer", error);
            player.sendSystemMessage(ReportMessages.message("faultfence.report.notification", problems,
                    Component.translatable("faultfence.viewer.failed").withStyle(ChatFormatting.RED)));
        }
    }
}
