package dev.discordplus.chat;

import dev.discordplus.config.DiscordPlusConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
        return renderDiscordMessageRich(config, player, originalMessage, fallbackMessage).content();
    }

    public static DiscordRelayMessage renderDiscordMessageRich(
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
            return DiscordRelayMessage.plain(fallbackMessage);
        }

        try {
            Class<?> api = Class.forName("dev.chatplus.api.ChatPlusApi");
            Method detector = api.getMethod("hasInteractivePlaceholders", String.class);
            Object detected = detector.invoke(null, originalMessage);
            if (!(detected instanceof Boolean hasPlaceholders) || !hasPlaceholders) {
                return DiscordRelayMessage.plain(fallbackMessage);
            }
            DiscordRelayMessage rich = renderRich(api, player, originalMessage);
            if (rich != null && !rich.content().isBlank()) {
                return rich;
            }
            Method renderer = api.getMethod("renderDiscordChat", Player.class, String.class);
            Object result = renderer.invoke(null, player, originalMessage);
            if (result instanceof String rendered && !rendered.isBlank()) {
                return DiscordRelayMessage.plain(rendered);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return DiscordRelayMessage.plain(fallbackMessage);
        }
        return DiscordRelayMessage.plain(fallbackMessage);
    }

    private static DiscordRelayMessage renderRich(Class<?> api, Player player, String message)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method renderer = api.getMethod("renderDiscordChatRich", Player.class, String.class);
        Object result = renderer.invoke(null, player, message);
        if (result == null) {
            return null;
        }
        String content = readString(result, "content");
        Object previews = invoke(result, "itemPreviews");
        if (!(previews instanceof List<?> previewList) || previewList.isEmpty()) {
            return DiscordRelayMessage.plain(content);
        }
        List<DiscordRelayMessage.ItemPreview> items = new ArrayList<>();
        for (Object preview : previewList) {
            if (preview == null) {
                continue;
            }
            items.add(new DiscordRelayMessage.ItemPreview(
                    readString(preview, "placeholder"),
                    readString(preview, "hand"),
                    readString(preview, "ownerName"),
                    readString(preview, "name"),
                    readString(preview, "materialKey"),
                    readString(preview, "materialName"),
                    readString(preview, "imageKey"),
                    readInt(preview, "amount"),
                    readString(preview, "durability"),
                    readInt(preview, "customModelData"),
                    readStringList(preview, "enchantments"),
                    readStringList(preview, "lore")
            ));
        }
        return new DiscordRelayMessage(content, List.copyOf(items));
    }

    private static Object invoke(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static String readString(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object value = invoke(target, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private static int readInt(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object value = invoke(target, methodName);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static List<String> readStringList(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object value = invoke(target, methodName);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry != null) {
                result.add(String.valueOf(entry));
            }
        }
        return List.copyOf(result);
    }
}
