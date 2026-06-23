package dev.discordplus.chat;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import dev.discordplus.roles.RoleSyncService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatBridgeListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;
    private final RoleSyncService roleSyncService;

    public ChatBridgeListener(JavaPlugin plugin, DiscordPlusConfig config, DiscordBotService botService,
                              RoleSyncService roleSyncService) {
        this.plugin = plugin;
        this.config = config;
        this.botService = botService;
        this.roleSyncService = roleSyncService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> roleSyncService.sync(player), config.joinSyncDelayTicks());
        if (!player.hasPlayedBefore() && config.firstJoinMessages()) {
            botService.sendFirstJoin(player);
            if (!config.firstJoinSettings().alsoSendJoinMessage()) {
                return;
            }
        }
        botService.sendJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        botService.sendQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Component deathMessage = event.deathMessage();
        botService.sendDeath(player, deathMessage == null ? null : PLAIN.serialize(deathMessage));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        botService.sendAdvancement(event.getPlayer(), event.getAdvancement());
    }
}
