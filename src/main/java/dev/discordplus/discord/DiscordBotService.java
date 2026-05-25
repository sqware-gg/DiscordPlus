package dev.discordplus.discord;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.config.DiscordPlusConfig.AdvancementSettings;
import dev.discordplus.config.DiscordPlusConfig.ActivityStyle;
import dev.discordplus.config.DiscordPlusConfig.DeathSettings;
import dev.discordplus.config.DiscordPlusConfig.DiscordEventStyle;
import dev.discordplus.config.DiscordPlusConfig.EmbedField;
import dev.discordplus.config.DiscordPlusConfig.EmbedStyle;
import dev.discordplus.config.DiscordPlusConfig.LifecycleSettings;
import dev.discordplus.config.DiscordPlusConfig.MinecraftChatStyle;
import dev.discordplus.link.LinkManager;
import dev.discordplus.roles.RoleSyncService;
import dev.discordplus.util.PlaceholderFormatter;
import dev.discordplus.util.PlaceholderFormatter.AdvancementSnapshot;
import dev.discordplus.util.PlaceholderFormatter.BroadcastSnapshot;
import dev.discordplus.util.PlaceholderFormatter.DeathSnapshot;
import dev.discordplus.util.PlaceholderFormatter.EventContext;
import dev.discordplus.util.PlaceholderFormatter.LifecycleSnapshot;
import dev.discordplus.util.PlaceholderFormatter.OnlineSnapshot;
import dev.discordplus.util.TextSanitizer;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class DiscordBotService {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final LinkManager linkManager;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicInteger lifecycle = new AtomicInteger();
    private volatile JDA jda;
    private volatile RoleSyncService roleSyncService;
    private volatile BukkitTask startupTask;
    private volatile StartupReason startupReason = StartupReason.SERVER_START;

    public DiscordBotService(JavaPlugin plugin, DiscordPlusConfig config, LinkManager linkManager) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
    }

    public void start() {
        start(StartupReason.SERVER_START);
    }

    public void startForReload() {
        start(StartupReason.RELOAD);
    }

    private void start(StartupReason reason) {
        if (config.botToken().isBlank()) {
            plugin.getLogger().warning("Discord bot token is empty. Set bot.token in config.yml.");
            return;
        }
        if (!starting.compareAndSet(false, true)) {
            return;
        }
        startupReason = reason;
        int generation = lifecycle.incrementAndGet();

        startupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> connect(generation)),
                config.botConnectDelayTicks());
    }

    private void connect(int generation) {
        if (lifecycle.get() != generation) {
            starting.set(false);
            return;
        }
        try {
            JDA built = JDABuilder.createDefault(config.botToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .setActivity(buildActivity())
                    .addEventListeners(new DiscordMessageListener(plugin, config, this, linkManager, roleSyncService))
                    .build();
            if (lifecycle.get() != generation) {
                built.shutdownNow();
                return;
            }
            jda = built;
            plugin.getLogger().info("Discord bot is starting.");
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not start Discord bot: " + e.getMessage());
        } finally {
            starting.set(false);
        }
    }

    public void setRoleSyncService(RoleSyncService roleSyncService) {
        this.roleSyncService = roleSyncService;
    }

    private Activity buildActivity() {
        ActivityStyle style = config.activityStyle();
        if (!style.enabled()) {
            return null;
        }

        String text = style.text().isBlank() ? "Minecraft" : style.text();
        return switch (style.type().toLowerCase(Locale.ROOT)) {
            case "watching" -> Activity.watching(text);
            case "listening" -> Activity.listening(text);
            case "competing" -> Activity.competing(text);
            case "streaming" -> streamingActivity(text, style.url());
            case "custom", "custom-status", "custom_status" -> Activity.customStatus(text);
            case "none", "off", "disabled" -> null;
            default -> Activity.playing(text);
        };
    }

    private Activity streamingActivity(String text, String url) {
        if (Activity.isValidStreamingUrl(url)) {
            return Activity.streaming(text, url);
        }
        plugin.getLogger().warning("bot.activity.url must be a valid Twitch or YouTube URL for streaming activity. Using playing activity instead.");
        return Activity.playing(text);
    }

    public void stop() {
        lifecycle.incrementAndGet();
        BukkitTask pendingStartup = startupTask;
        startupTask = null;
        if (pendingStartup != null) {
            pendingStartup.cancel();
        }
        JDA current = jda;
        jda = null;
        starting.set(false);
        if (current != null) {
            try {
                current.shutdown();
                if (!current.awaitShutdown(10, TimeUnit.SECONDS)) {
                    current.shutdownNow();
                    current.awaitShutdown(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                current.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                plugin.getLogger().fine("Discord bot shutdown failed: " + e.getMessage());
                current.shutdownNow();
            }
        }
    }

    public boolean isReady() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public JDA jda() {
        return jda;
    }

    public Guild guild() {
        JDA current = jda;
        if (current == null) {
            return null;
        }
        if (!config.guildId().isBlank()) {
            return current.getGuildById(config.guildId());
        }
        List<Guild> guilds = current.getGuilds();
        return guilds.size() == 1 ? guilds.get(0) : null;
    }

    public boolean isConfiguredGuild(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return false;
        }
        if (!config.guildId().isBlank()) {
            return config.guildId().equals(guildId);
        }
        Guild guild = guild();
        return guild != null && guild.getId().equals(guildId);
    }

    public TextChannel chatChannel() {
        return channelById(config.chatChannelId());
    }

    public TextChannel eventChannel() {
        TextChannel configured = channelById(config.eventsChannelId());
        return configured == null ? chatChannel() : configured;
    }

    public TextChannel staffChannel() {
        TextChannel configured = channelById(config.staffChannelId());
        return configured == null ? eventChannel() : configured;
    }

    private TextChannel channelById(String channelId) {
        JDA current = jda;
        if (current == null || channelId.isBlank()) {
            return null;
        }
        TextChannel channel = current.getTextChannelById(channelId);
        if (channel == null || !isConfiguredGuild(channel.getGuild().getId())) {
            return null;
        }
        return channel;
    }

    public boolean sendChatMessage(String message) {
        TextChannel channel = chatChannel();
        if (channel == null) {
            return false;
        }
        channel.sendMessage(TextSanitizer.truncate(message, 1900)).queue(null,
                error -> plugin.getLogger().fine("Could not send Discord message: " + error.getMessage()));
        return true;
    }

    public boolean sendMinecraftChat(Player player, String rawMessage) {
        MinecraftChatStyle style = config.minecraftChatStyle();
        String content = PlaceholderFormatter.discord(style.content(), plugin, config, player, rawMessage);
        if (content.isBlank()) {
            return false;
        }

        JDA current = jda;
        if (style.useWebhook() && !style.webhookUrl().isBlank() && current != null) {
            try {
                IncomingWebhookClient webhook = WebhookClient.createClient(current, style.webhookUrl());
                var action = webhook.sendMessage(TextSanitizer.truncate(content, 1900))
                        .setUsername(TextSanitizer.truncate(PlaceholderFormatter.raw(style.username(), plugin, config, player, rawMessage), 80));
                String avatarUrl = PlaceholderFormatter.raw(style.avatarUrl(), plugin, config, player, rawMessage);
                if (!avatarUrl.isBlank()) {
                    action.setAvatarUrl(avatarUrl);
                }
                action.queue(null, error -> plugin.getLogger().fine("Could not send Discord webhook message: " + error.getMessage()));
                return true;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not use Discord webhook. Falling back to bot message: " + e.getMessage());
            }
        }

        return sendChatMessage(content);
    }

    public boolean sendJoin(Player player) {
        if (!config.joinLeave() || config.joinLeaveSettings().ignoresWorld(player.getWorld().getName())) {
            return false;
        }
        return sendStyledEvent(eventChannel(), config.joinStyle(), player, null);
    }

    public boolean sendFirstJoin(Player player) {
        if (!config.firstJoinMessages() || config.firstJoinSettings().ignoresWorld(player.getWorld().getName())) {
            return false;
        }
        return sendStyledEvent(eventChannel(), config.firstJoinStyle(), player, null,
                OnlineSnapshot.current(), EventContext.basic("first_join", "First Join", "#00D166"));
    }

    public boolean sendQuit(Player player) {
        if (!config.joinLeave() || config.joinLeaveSettings().ignoresWorld(player.getWorld().getName())) {
            return false;
        }
        return sendStyledEvent(eventChannel(), config.quitStyle(), player, null, OnlineSnapshot.excluding(player));
    }

    public boolean sendAdvancement(Player player, Advancement advancement) {
        if (!config.advancementMessages()) {
            return false;
        }
        if (config.advancementSettings().ignoresWorld(player.getWorld().getName())) {
            return false;
        }

        AdvancementSnapshot advancementSnapshot = advancementSnapshot(advancement);
        if (advancementSnapshot == null) {
            return false;
        }

        TextChannel channel = eventChannel();
        if (channel == null) {
            return false;
        }
        return sendStyledEvent(channel, config.advancementStyle(), player, advancementSnapshot.title(),
                OnlineSnapshot.current(), EventContext.advancement(advancementSnapshot));
    }

    public boolean sendDeath(Player player, String rawDeathMessage) {
        if (!config.deathMessages()) {
            return false;
        }
        DeathSnapshot deathSnapshot = deathSnapshot(player, rawDeathMessage);
        if (deathSnapshot == null) {
            return false;
        }
        return sendStyledEvent(eventChannel(), config.deathStyle(), player, deathSnapshot.deathMessage(),
                OnlineSnapshot.current(), EventContext.death(deathSnapshot));
    }

    public boolean sendServerStart() {
        if (!config.lifecycleMessages() || !config.lifecycleSettings().sendStartup()) {
            return false;
        }
        return sendLifecycle(config.serverStartStyle(), new LifecycleSnapshot("startup", "Server Online",
                serverName() + " is online.", "#3BA55D"));
    }

    public boolean sendServerStop() {
        if (!config.lifecycleMessages() || !config.lifecycleSettings().sendShutdown()) {
            return false;
        }
        return sendLifecycle(config.serverStopStyle(), new LifecycleSnapshot("shutdown", "Server Offline",
                serverName() + " is offline.", "#ED4245"), true);
    }

    public boolean sendReload() {
        if (!config.lifecycleMessages() || !config.lifecycleSettings().sendReload()) {
            return false;
        }
        return sendLifecycle(config.reloadStyle(), new LifecycleSnapshot("reload", "DiscordPlus Reloaded",
                "DiscordPlus reloaded on " + serverName() + ".", "#FEE75C"));
    }

    public void onReady() {
        StartupReason reason = startupReason;
        startupReason = StartupReason.SERVER_START;
        if (reason == StartupReason.RELOAD) {
            sendReload();
            return;
        }
        sendServerStart();
    }

    public boolean sendTestAdvancement(Player player) {
        AdvancementSnapshot snapshot = new AdvancementSnapshot(
                "discordplus:test/advancement",
                "discordplus",
                "test/advancement",
                "Test Advancement",
                "This is a preview of the configured advancement style.",
                "task",
                config.advancementSettings().labelFor("task"),
                config.advancementSettings().colorFor("task"),
                false,
                true
        );
        return sendStyledEvent(eventChannel(), config.advancementStyle(), player, snapshot.title(),
                OnlineSnapshot.current(), EventContext.advancement(snapshot));
    }

    public boolean sendTestDeath(Player player) {
        DeathSnapshot snapshot = new DeathSnapshot(
                player.getName() + " was slain during a DiscordPlus test.",
                "test",
                "DiscordPlus",
                "",
                "DiscordPlus",
                "",
                true
        );
        return sendStyledEvent(eventChannel(), config.deathStyle(), player, snapshot.deathMessage(),
                OnlineSnapshot.current(), EventContext.death(snapshot));
    }

    public boolean sendTestJoin(Player player) {
        return sendStyledEvent(eventChannel(), config.joinStyle(), player, null);
    }

    public boolean sendTestQuit(Player player) {
        return sendStyledEvent(eventChannel(), config.quitStyle(), player, null, OnlineSnapshot.excluding(player));
    }

    public boolean sendTestFirstJoin(Player player) {
        return sendStyledEvent(eventChannel(), config.firstJoinStyle(), player, null,
                OnlineSnapshot.current(), EventContext.basic("first_join", "First Join", "#00D166"));
    }

    public boolean sendTestServerStart() {
        return sendLifecycle(config.serverStartStyle(), new LifecycleSnapshot("startup", "Server Online",
                "This is a preview of the configured server-start style.", "#3BA55D"));
    }

    public boolean sendTestServerStop() {
        return sendLifecycle(config.serverStopStyle(), new LifecycleSnapshot("shutdown", "Server Offline",
                "This is a preview of the configured server-stop style.", "#ED4245"));
    }

    public boolean sendTestReload() {
        return sendLifecycle(config.reloadStyle(), new LifecycleSnapshot("reload", "DiscordPlus Reloaded",
                "This is a preview of the configured reload style.", "#FEE75C"));
    }

    public boolean sendStatus(MessageChannel channel) {
        return sendStyledEvent(channel, config.statusStyle(), null, null);
    }

    public boolean sendBroadcast(String styleKey, String playerName, String message) {
        DiscordEventStyle style = config.broadcastStyle(styleKey);
        if (style == null || message == null || message.isBlank()) {
            return false;
        }

        String normalizedPlayer = "-".equals(playerName) ? "" : TextSanitizer.safeDiscord(playerName);
        Player player = normalizedPlayer.isBlank() ? null : Bukkit.getPlayerExact(normalizedPlayer);
        BroadcastSnapshot snapshot = new BroadcastSnapshot(
                styleKey.toLowerCase(Locale.ROOT),
                labelFromKey(styleKey),
                message,
                normalizedPlayer,
                normalizedPlayer.isBlank() ? "" : "https://mc-heads.net/avatar/" + normalizedPlayer + "/64"
        );
        return sendStyledEvent(eventChannel(), style, player, message, OnlineSnapshot.current(),
                EventContext.broadcast(snapshot));
    }

    private boolean sendStyledEvent(DiscordEventStyle style, Player player) {
        return sendStyledEvent(style, player, OnlineSnapshot.current());
    }

    private boolean sendStyledEvent(DiscordEventStyle style, Player player, OnlineSnapshot onlineSnapshot) {
        return sendStyledEvent(eventChannel(), style, player, null, onlineSnapshot);
    }

    private boolean sendStyledEvent(MessageChannel channel, DiscordEventStyle style, Player player, String message) {
        return sendStyledEvent(channel, style, player, message, OnlineSnapshot.current(), EventContext.empty());
    }

    private boolean sendStyledEvent(MessageChannel channel, DiscordEventStyle style, Player player, String message,
                                    OnlineSnapshot onlineSnapshot) {
        return sendStyledEvent(channel, style, player, message, onlineSnapshot, EventContext.empty());
    }

    private boolean sendStyledEvent(MessageChannel channel, DiscordEventStyle style, Player player, String message,
                                    OnlineSnapshot onlineSnapshot, EventContext eventContext) {
        if (channel == null) {
            return false;
        }
        if (style.embedEnabled()) {
            MessageEmbed embed = buildEmbed(style.embed(), player, message, onlineSnapshot, eventContext);
            if (embed != null) {
                channel.sendMessageEmbeds(embed).queue(null,
                        error -> plugin.getLogger().fine("Could not send Discord embed: " + error.getMessage()));
                return true;
            }
        }

        String text = PlaceholderFormatter.discord(style.text(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        if (text.isBlank()) {
            return false;
        }
        channel.sendMessage(TextSanitizer.truncate(text, 1900)).queue(null,
                error -> plugin.getLogger().fine("Could not send Discord message: " + error.getMessage()));
        return true;
    }

    private boolean sendStyledEventBlocking(MessageChannel channel, DiscordEventStyle style, Player player,
                                            String message, OnlineSnapshot onlineSnapshot,
                                            EventContext eventContext) {
        if (channel == null) {
            return false;
        }
        try {
            if (style.embedEnabled()) {
                MessageEmbed embed = buildEmbed(style.embed(), player, message, onlineSnapshot, eventContext);
                if (embed != null) {
                    channel.sendMessageEmbeds(embed).submit().get(3, TimeUnit.SECONDS);
                    return true;
                }
            }

            String text = PlaceholderFormatter.discord(style.text(), plugin, config, player, message,
                    onlineSnapshot, eventContext);
            if (text.isBlank()) {
                return false;
            }
            channel.sendMessage(TextSanitizer.truncate(text, 1900)).submit().get(3, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            plugin.getLogger().fine("Could not send Discord message before shutdown: " + e.getMessage());
            return false;
        }
    }

    private MessageEmbed buildEmbed(EmbedStyle style, Player player, String message, OnlineSnapshot onlineSnapshot,
                                    EventContext eventContext) {
        EmbedBuilder embed = new EmbedBuilder();
        String title = PlaceholderFormatter.raw(style.title(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String description = PlaceholderFormatter.raw(style.description(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String authorName = PlaceholderFormatter.raw(style.authorName(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String authorUrl = PlaceholderFormatter.raw(style.authorUrl(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String authorIconUrl = PlaceholderFormatter.raw(style.authorIconUrl(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String footer = PlaceholderFormatter.raw(style.footer(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String thumbnail = PlaceholderFormatter.raw(style.thumbnailUrl(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String image = PlaceholderFormatter.raw(style.imageUrl(), plugin, config, player, message,
                onlineSnapshot, eventContext);
        String color = PlaceholderFormatter.raw(style.color(), plugin, config, player, message,
                onlineSnapshot, eventContext);

        if (!authorName.isBlank()) {
            try {
                embed.setAuthor(TextSanitizer.truncate(authorName, 256),
                        authorUrl.isBlank() ? null : authorUrl,
                        authorIconUrl.isBlank() ? null : authorIconUrl);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid Discord embed author URL for: " + authorName);
                embed.setAuthor(TextSanitizer.truncate(authorName, 256));
            }
        }
        if (!title.isBlank()) {
            embed.setTitle(TextSanitizer.truncate(title, 256));
        }
        if (!description.isBlank()) {
            embed.setDescription(TextSanitizer.truncate(description, 4096));
        }
        int fieldCount = 0;
        for (EmbedField field : style.fields()) {
            if (fieldCount >= 25) {
                break;
            }
            String name = PlaceholderFormatter.raw(field.name(), plugin, config, player, message,
                    onlineSnapshot, eventContext);
            String value = PlaceholderFormatter.raw(field.value(), plugin, config, player, message,
                    onlineSnapshot, eventContext);
            if (!name.isBlank() && !value.isBlank()) {
                embed.addField(TextSanitizer.truncate(name, 256), TextSanitizer.truncate(value, 1024), field.inline());
                fieldCount++;
            }
        }
        if (!footer.isBlank()) {
            embed.setFooter(TextSanitizer.truncate(footer, 2048));
        }
        if (!thumbnail.isBlank()) {
            try {
                embed.setThumbnail(thumbnail);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid Discord embed thumbnail URL: " + thumbnail);
            }
        }
        if (!image.isBlank()) {
            try {
                embed.setImage(image);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid Discord embed image URL: " + image);
            }
        }
        if (style.timestamp()) {
            embed.setTimestamp(Instant.now());
        }
        embed.setColor(parseColor(color));

        if (embed.isEmpty()) {
            return null;
        }
        return embed.build();
    }

    private boolean sendLifecycle(DiscordEventStyle style, LifecycleSnapshot lifecycleSnapshot) {
        return sendLifecycle(style, lifecycleSnapshot, false);
    }

    private boolean sendLifecycle(DiscordEventStyle style, LifecycleSnapshot lifecycleSnapshot, boolean waitForDelivery) {
        LifecycleSettings settings = config.lifecycleSettings();
        MessageChannel channel = settings.useStaffChannel() ? staffChannel() : eventChannel();
        OnlineSnapshot onlineSnapshot = OnlineSnapshot.current();
        EventContext eventContext = EventContext.lifecycle(lifecycleSnapshot);
        if (waitForDelivery) {
            return sendStyledEventBlocking(channel, style, null, lifecycleSnapshot.description(),
                    onlineSnapshot, eventContext);
        }
        return sendStyledEvent(channel, style, null, lifecycleSnapshot.description(), onlineSnapshot, eventContext);
    }

    private DeathSnapshot deathSnapshot(Player player, String rawDeathMessage) {
        if (player == null) {
            return null;
        }
        DeathSettings settings = config.deathSettings();
        if (settings.ignoresWorld(player.getWorld().getName())) {
            return null;
        }

        EntityDamageEvent damageEvent = player.getLastDamageCause();
        String cause = damageEvent == null ? "unknown" : damageEvent.getCause().name().toLowerCase(Locale.ROOT);
        if (settings.ignoresCause(cause)) {
            return null;
        }

        Player killer = player.getKiller();
        boolean pvp = killer != null;
        if (settings.pvpOnly() && !pvp) {
            return null;
        }

        String deathMessage = rawDeathMessage == null || rawDeathMessage.isBlank()
                ? player.getName() + " died."
                : rawDeathMessage;
        return new DeathSnapshot(
                deathMessage,
                cause,
                pvp ? killer.getName() : "None",
                pvp ? killer.getUniqueId().toString() : "",
                pvp ? TextSanitizer.stripMinecraftColor(killer.getDisplayName()) : "None",
                pvp ? "https://mc-heads.net/avatar/" + killer.getName() + "/64" : "",
                pvp
        );
    }

    private AdvancementSnapshot advancementSnapshot(Advancement advancement) {
        if (advancement == null) {
            return null;
        }

        NamespacedKey key = advancement.getKey();
        String keyString = key.toString();
        AdvancementSettings settings = config.advancementSettings();
        if (settings.ignoredKeys().contains(keyString)) {
            return null;
        }
        for (String prefix : settings.ignoredKeyPrefixes()) {
            if (keyString.startsWith(prefix)) {
                return null;
            }
        }

        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            return null;
        }
        if (settings.announceChatOnly() && !display.doesAnnounceToChat()) {
            return null;
        }
        if (!settings.includeHidden() && display.isHidden()) {
            return null;
        }

        String type = display.frame() == null ? "task" : display.frame().name().toLowerCase(Locale.ROOT);
        String title = plain(display.title());
        if (title.isBlank()) {
            title = key.getKey();
        }

        return new AdvancementSnapshot(
                keyString,
                key.getNamespace(),
                key.getKey(),
                title,
                plain(display.description()),
                type,
                settings.labelFor(type),
                settings.colorFor(type),
                display.isHidden(),
                display.doesAnnounceToChat()
        );
    }

    private String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String serverName() {
        return config.serverName().isBlank() ? plugin.getServer().getName() : config.serverName();
    }

    private String labelFromKey(String key) {
        String normalized = key == null ? "" : key.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return "Broadcast";
        }
        StringBuilder label = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return label.toString();
    }

    private Color parseColor(String value) {
        if (value == null || value.isBlank()) {
            return new Color(0x5865F2);
        }
        try {
            return new Color(Integer.parseInt(value.replace("#", ""), 16));
        } catch (NumberFormatException ignored) {
            return new Color(0x5865F2);
        }
    }

    public String onlinePlayersList() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return "No players online.";
        }
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    private enum StartupReason {
        SERVER_START,
        RELOAD
    }
}
