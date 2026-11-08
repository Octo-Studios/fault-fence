package it.hurts.shatterbyte.faultfence.common.report;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ReportMessages {
    public static MutableComponent message(String key, Object... arguments) {
        return Component.empty().withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[Fault Fence] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.translatable(key, arguments));
    }
}
