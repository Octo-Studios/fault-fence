package it.hurts.shatterbyte.faultfence.common.report;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

public class ReportMessages {
    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();

    public static MutableComponent translate(String language, String key, Object... arguments) {
        var english = TRANSLATIONS.computeIfAbsent(Language.DEFAULT, ReportMessages::load);
        var translations = TRANSLATIONS.computeIfAbsent(language, ReportMessages::load);

        return Component.translatableWithFallback(key, translations.getOrDefault(key, english.get(key)), arguments);
    }

    public static MutableComponent message(String language, String key, Object... arguments) {
        return Component.empty().withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[Fault Fence] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(translate(language, key, arguments));
    }

    private static Map<String, String> load(String language) {
        if (language.contains("/") || language.contains("\\"))
            return Map.of();

        try (var resource = ReportMessages.class.getResourceAsStream("/assets/faultfence/lang/" + language + ".json")) {
            if (resource == null)
                return Map.of();

            var translations = new HashMap<String, String>();

            Language.loadFromJson(resource, translations::put);

            return translations;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
