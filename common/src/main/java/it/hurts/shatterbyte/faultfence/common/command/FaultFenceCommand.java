package it.hurts.shatterbyte.faultfence.common.command;

import com.mojang.brigadier.CommandDispatcher;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.report.ReportMessages;
import it.hurts.shatterbyte.faultfence.common.viewer.ReportViewer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class FaultFenceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faultfence")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("viewer").executes(context -> viewer(context.getSource()))));
    }

    private static int viewer(CommandSourceStack source) {
        try {
            var address = ReportViewer.start();

            source.sendSuccess(() -> ReportMessages.message("faultfence.viewer.link",
                    Component.translatable("faultfence.viewer.open").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                            .withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(address)))), false);

            return 1;
        } catch (IOException error) {
            FaultFence.LOGGER.error("Couldn't start Fault Fence viewer", error);
            source.sendFailure(ReportMessages.message("faultfence.viewer.failed").withStyle(ChatFormatting.RED));

            return 0;
        }
    }
}
