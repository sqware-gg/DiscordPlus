package dev.discordplus.chat;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class LegacyChatBridgeListener implements Listener {
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;

    public LegacyChatBridgeListener(DiscordPlusConfig config, DiscordBotService botService) {
        this.config = config;
        this.botService = botService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!config.minecraftToDiscord()) {
            return;
        }
        botService.sendMinecraftChat(event.getPlayer(), ChatPlusCompatibility.renderDiscordMessageRich(
                config, event.getPlayer(), event.getMessage(), event.getMessage()));
    }
}
