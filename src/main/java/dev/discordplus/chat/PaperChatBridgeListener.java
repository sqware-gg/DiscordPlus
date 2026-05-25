package dev.discordplus.chat;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PaperChatBridgeListener implements Listener {
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;

    public PaperChatBridgeListener(DiscordPlusConfig config, DiscordBotService botService) {
        this.config = config;
        this.botService = botService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.minecraftToDiscord()) {
            return;
        }
        botService.sendMinecraftChat(event.getPlayer(), PlainTextComponentSerializer.plainText().serialize(event.message()));
    }
}
