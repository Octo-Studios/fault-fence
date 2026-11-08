package it.hurts.shatterbyte.faultfence.common.report;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ReportNotifications {
    public static void notify(ServerPlayer player) {
        var report = Reports.latest();

        if (!FaultFence.CONFIG.notifyAdmins || report == null || !Commands.LEVEL_ADMINS.check(player.permissions()))
            return;

        var count = report.issueCount();

        if (count == 0)
            return;

        player.sendSystemMessage(Component.translatable("faultfence.report.notification", count));
    }
}
