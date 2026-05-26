package dev.discordplus.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

public final class TextSanitizer {
    private static final Pattern HEX_COLOR = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private TextSanitizer() {
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', replaceHex(text == null ? "" : text));
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

    private static String replaceHex(String text) {
        Matcher matcher = HEX_COLOR.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder(String.valueOf(ChatColor.COLOR_CHAR)).append('x');
            for (char character : hex.toCharArray()) {
                replacement.append(ChatColor.COLOR_CHAR).append(character);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
