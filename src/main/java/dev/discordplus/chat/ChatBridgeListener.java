package dev.discordplus.chat;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import dev.discordplus.roles.RoleSyncService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatBridgeListener implements Listener {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;
    private final RoleSyncService roleSyncService;
    private final boolean legacyChatEnabled;

    public ChatBridgeListener(JavaPlugin plugin, DiscordPlusConfig config, DiscordBotService botService,
                              RoleSyncService roleSyncService, boolean legacyChatEnabled) {
        this.plugin = plugin;
        this.config = config;
        this.botService = botService;
        this.roleSyncService = roleSyncService;
        this.legacyChatEnabled = legacyChatEnabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!legacyChatEnabled || !config.minecraftToDiscord()) {
            return;
        }
        botService.sendMinecraftChat(event.getPlayer(), event.getMessage());
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
        botService.sendDeath(player, event.getDeathMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        botService.sendAdvancement(event.getPlayer(), event.getAdvancement());
    }
}
