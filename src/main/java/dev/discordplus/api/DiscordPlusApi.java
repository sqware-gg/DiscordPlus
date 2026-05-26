package dev.discordplus.api;

import dev.discordplus.discord.DiscordBotService;

public final class DiscordPlusApi {
    private static DiscordBotService botService;

    private DiscordPlusApi() {
    }

    public static void register(DiscordBotService service) {
        botService = service;
    }

    public static void unregister() {
        botService = null;
    }

    public static boolean available() {
        return botService != null && botService.isReady();
    }

    public static boolean sendBroadcast(String styleKey, String playerName, String message) {
        DiscordBotService service = botService;
        return service != null && service.sendBroadcast(styleKey, playerName, message);
    }

    public static boolean sendAnnouncement(String message) {
        return sendBroadcast("announcement", "", message);
    }
}
