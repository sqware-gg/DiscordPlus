package dev.discordplus.util;

import org.bukkit.ChatColor;

public final class TextSanitizer {
    private TextSanitizer() {
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static String stripMinecraftColor(String text) {
        return ChatColor.stripColor(color(text == null ? "" : text));
    }

    public static String safeDiscord(String text) {
        String sanitized = stripMinecraftColor(text).replace("\r", " ").replace("\n", " ");
        sanitized = sanitized.replace("@", "@\u200B");
        return truncate(sanitized, 1800);
    }

    public static String safeMinecraft(String text) {
        String sanitized = ChatColor.stripColor(text == null ? "" : text);
        sanitized = sanitized == null ? "" : sanitized.replace("\r", " ").replace("\n", " ");
        return truncate(sanitized, 300);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
