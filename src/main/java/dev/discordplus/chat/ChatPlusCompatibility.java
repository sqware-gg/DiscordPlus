package dev.discordplus.chat;

import dev.discordplus.config.DiscordPlusConfig;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ChatPlusCompatibility {
    private ChatPlusCompatibility() {
    }

    public static String renderDiscordMessage(
            DiscordPlusConfig config,
            Player player,
            String originalMessage,
            String fallbackMessage
    ) {
        if (!config.chatPlusInteractivePlaceholders()
                || player == null
                || originalMessage == null
                || originalMessage.isBlank()
                || !Bukkit.getPluginManager().isPluginEnabled("ChatPlus")) {
            return fallbackMessage;
        }

        try {
            Class<?> api = Class.forName("dev.chatplus.api.ChatPlusApi");
            Method detector = api.getMethod("hasInteractivePlaceholders", String.class);
            Object detected = detector.invoke(null, originalMessage);
            if (!(detected instanceof Boolean hasPlaceholders) || !hasPlaceholders) {
                return fallbackMessage;
            }
            Method renderer = api.getMethod("renderDiscordChat", Player.class, String.class);
            Object result = renderer.invoke(null, player, originalMessage);
            if (result instanceof String rendered && !rendered.isBlank()) {
                return rendered;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return fallbackMessage;
        }
        return fallbackMessage;
    }
}
