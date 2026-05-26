package dev.discordplus.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordPlusConfig {
    private final JavaPlugin plugin;

    private String botToken;
    private String guildId;
    private long botConnectDelayTicks;
    private ActivityStyle activityStyle;
    private String serverName;
    private String inviteUrl;
    private String chatChannelId;
    private String eventsChannelId;
    private String staffChannelId;
    private boolean minecraftToDiscord;
    private boolean discordToMinecraft;
    private boolean joinLeave;
    private boolean advancementMessages;
    private boolean firstJoinMessages;
    private boolean deathMessages;
    private boolean lifecycleMessages;
    private boolean discordCommands;
    private boolean slashCommandsEnabled;
    private boolean slashCommandsRefreshOnStart;
    private boolean clearGlobalCommandsWhenGuildSet;
    private boolean integrationsEnabled;
    private boolean linkingEnabled;
    private String commandPrefix;
    private int codeExpiryMinutes;
    private String linkedRoleId;
    private boolean roleSyncEnabled;
    private int roleSyncIntervalMinutes;
    private long joinSyncDelayTicks;
    private boolean removeUnmatchedMappedRoles;
    private List<RoleMapping> roleMappings;
    private JoinLeaveSettings joinLeaveSettings;
    private FirstJoinSettings firstJoinSettings;
    private AdvancementSettings advancementSettings;
    private DeathSettings deathSettings;
    private LifecycleSettings lifecycleSettings;
    private MinecraftChatStyle minecraftChatStyle;
    private DiscordEventStyle joinStyle;
    private DiscordEventStyle quitStyle;
    private DiscordEventStyle firstJoinStyle;
    private DiscordEventStyle advancementStyle;
    private DiscordEventStyle deathStyle;
    private DiscordEventStyle serverStartStyle;
    private DiscordEventStyle serverStopStyle;
    private DiscordEventStyle reloadStyle;
    private DiscordEventStyle statusStyle;
    private Map<String, DiscordEventStyle> broadcastStyles;
    private Map<String, IntegrationSettings> integrations;
    private AuctionsPlusCommandSettings auctionsPlusCommandSettings;
    private PointsPlusCommandSettings pointsPlusCommandSettings;
    private PlaytimePlusCommandSettings playtimePlusCommandSettings;
    private String discordToMinecraftFormat;
    private String messagePrefix;

    public DiscordPlusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        botToken = readString("bot.token");
        guildId = readString("bot.guild-id");
        botConnectDelayTicks = Math.max(0, plugin.getConfig().getInt("bot.connect-delay-seconds", 5)) * 20L;
        activityStyle = readActivityStyle();
        serverName = readString("server.name", "Minecraft");
        inviteUrl = readString("server.invite-url");
        chatChannelId = readString("channels.chat-channel-id");
        eventsChannelId = readString("channels.events-channel-id");
        staffChannelId = readString("channels.staff-channel-id");
        minecraftToDiscord = readBoolean("features.minecraft-to-discord-chat", "channels.minecraft-to-discord", true);
        discordToMinecraft = readBoolean("features.discord-to-minecraft-chat", "channels.discord-to-minecraft", true);
        joinLeave = readBoolean("features.join-leave-messages", "channels.join-leave", true);
        advancementMessages = plugin.getConfig().getBoolean("features.advancement-messages", true);
        firstJoinMessages = plugin.getConfig().getBoolean("features.first-join-messages", true);
        deathMessages = plugin.getConfig().getBoolean("features.death-messages", true);
        lifecycleMessages = plugin.getConfig().getBoolean("features.lifecycle-messages", true);
        discordCommands = plugin.getConfig().getBoolean("features.discord-commands", true);
        slashCommandsEnabled = plugin.getConfig().getBoolean("slash-commands.enabled", true);
        slashCommandsRefreshOnStart = plugin.getConfig().getBoolean("slash-commands.refresh-on-start", true);
        clearGlobalCommandsWhenGuildSet = plugin.getConfig().getBoolean("slash-commands.clear-global-commands-when-guild-set", true);
        integrationsEnabled = plugin.getConfig().contains("integrations.enabled")
                && plugin.getConfig().getBoolean("integrations.enabled", true);
        linkingEnabled = plugin.getConfig().getBoolean("linking.enabled", true);
        commandPrefix = readString("linking.command-prefix", "!");
        if (commandPrefix.isBlank()) {
            commandPrefix = "!";
        }
        codeExpiryMinutes = Math.max(1, plugin.getConfig().getInt("linking.code-expiry-minutes", 10));
        linkedRoleId = readString("linking.linked-role-id");
        roleSyncEnabled = plugin.getConfig().getBoolean("role-sync.enabled", true);
        roleSyncIntervalMinutes = Math.max(1, plugin.getConfig().getInt("role-sync.interval-minutes", 5));
        joinSyncDelayTicks = Math.max(1, plugin.getConfig().getInt("role-sync.join-sync-delay-seconds", 3)) * 20L;
        removeUnmatchedMappedRoles = plugin.getConfig().getBoolean("role-sync.remove-unmatched-mapped-roles", false);
        roleMappings = readRoleMappings();
        joinLeaveSettings = readJoinLeaveSettings();
        firstJoinSettings = readFirstJoinSettings();
        advancementSettings = readAdvancementSettings();
        deathSettings = readDeathSettings();
        lifecycleSettings = readLifecycleSettings();
        minecraftChatStyle = readMinecraftChatStyle();
        joinStyle = readEventStyle("discord-style.join", "format.join",
                "**{player}** joined **{server}** - {online}/{max_players} online",
                "embed", defaultJoinEmbed());
        quitStyle = readEventStyle("discord-style.quit", "format.quit",
                "**{player}** left **{server}** - {online}/{max_players} online",
                "embed", defaultQuitEmbed());
        firstJoinStyle = readEventStyle("discord-style.first-join", null,
                "**{player}** joined **{server}** for the first time",
                "embed", defaultFirstJoinEmbed());
        advancementStyle = readEventStyle("discord-style.advancement", null,
                "**{player}** completed **{advancement_title}**",
                "embed", defaultAdvancementEmbed());
        deathStyle = readEventStyle("discord-style.death", null, "{death_message}",
                "embed", defaultDeathEmbed());
        serverStartStyle = readEventStyle("discord-style.server-start", null,
                "**{server}** is online.",
                "embed", defaultServerStartEmbed());
        serverStopStyle = readEventStyle("discord-style.server-stop", null,
                "**{server}** is offline.",
                "embed", defaultServerStopEmbed());
        reloadStyle = readEventStyle("discord-style.reload", null,
                "**DiscordPlus reloaded on {server}.**",
                "embed", defaultReloadEmbed());
        statusStyle = readEventStyle("discord-style.status", null,
                "**{server}**: {online}/{max_players} online",
                "embed", defaultStatusEmbed());
        broadcastStyles = readBroadcastStyles();
        integrations = readIntegrations();
        auctionsPlusCommandSettings = readAuctionsPlusCommandSettings();
        pointsPlusCommandSettings = readPointsPlusCommandSettings();
        playtimePlusCommandSettings = readPlaytimePlusCommandSettings();
        discordToMinecraftFormat = readString("minecraft-style.discord-chat", readString("format.discord-to-minecraft", "&#2b98fdDiscord &8› &f{user}&8: &7{message}"));
        messagePrefix = readString("messages.prefix", "&#2b98fdDiscordPlus &8› &7");
    }

    private MinecraftChatStyle readMinecraftChatStyle() {
        return new MinecraftChatStyle(
                plugin.getConfig().getBoolean("discord-style.minecraft-chat.use-webhook", false),
                readString("discord-style.minecraft-chat.webhook-url"),
                readString("discord-style.minecraft-chat.username", "{player}"),
                readString("discord-style.minecraft-chat.avatar-url"),
                readString("discord-style.minecraft-chat.content", readString("format.minecraft-to-discord", "**{player}**: {message}"))
        );
    }

    private ActivityStyle readActivityStyle() {
        if (plugin.getConfig().isConfigurationSection("bot.activity")) {
            return new ActivityStyle(
                    plugin.getConfig().getBoolean("bot.activity.enabled", true),
                    readString("bot.activity.type", "playing"),
                    readString("bot.activity.text", "Minecraft"),
                    readString("bot.activity.url")
            );
        }

        String legacyActivity = readString("bot.activity", "Minecraft");
        return new ActivityStyle(!legacyActivity.isBlank(), "playing", legacyActivity, "");
    }

    private DiscordEventStyle readEventStyle(String path, String legacyTextPath, String fallbackText) {
        return readEventStyle(path, legacyTextPath, fallbackText, "text", EmbedStyle.empty());
    }

    private DiscordEventStyle readEventStyle(String path, String legacyTextPath, String fallbackText,
                                             String fallbackStyle, EmbedStyle fallbackEmbed) {
        String legacyText = legacyTextPath == null ? fallbackText : readString(legacyTextPath, fallbackText);
        return new DiscordEventStyle(
                readString(path + ".style", fallbackStyle),
                readString(path + ".text", legacyText),
                readEmbedStyle(path + ".embed", fallbackEmbed)
        );
    }

    private EmbedStyle readEmbedStyle(String path, EmbedStyle fallback) {
        return new EmbedStyle(
                readString(path + ".author-name", fallback.authorName()),
                readString(path + ".author-url", fallback.authorUrl()),
                readString(path + ".author-icon-url", fallback.authorIconUrl()),
                readString(path + ".title", fallback.title()),
                readString(path + ".description", fallback.description()),
                readString(path + ".color", fallback.color()),
                readString(path + ".footer", fallback.footer()),
                readString(path + ".thumbnail-url", fallback.thumbnailUrl()),
                readString(path + ".image-url", fallback.imageUrl()),
                readEmbedFields(path + ".fields", fallback.fields()),
                plugin.getConfig().getBoolean(path + ".timestamp", fallback.timestamp())
        );
    }

    private List<EmbedField> readEmbedFields(String path, List<EmbedField> fallback) {
        if (!plugin.getConfig().contains(path)) {
            return fallback;
        }
        List<EmbedField> fields = new ArrayList<>();
        for (Map<?, ?> field : plugin.getConfig().getMapList(path)) {
            String name = stringValue(field.get("name"));
            String value = stringValue(field.get("value"));
            if (name.isBlank() || value.isBlank()) {
                continue;
            }
            fields.add(new EmbedField(name, value, booleanValue(field.get("inline"))));
        }
        return List.copyOf(fields);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean booleanValue
                ? booleanValue
                : Boolean.parseBoolean(stringValue(value));
    }

    private EmbedStyle defaultJoinEmbed() {
        return new EmbedStyle(
                "Player Activity",
                "",
                "{player_avatar_url}",
                "{player} joined",
                "**{server}** now has **{online}/{max_players}** players online.",
                "#2b98fd",
                "{world} | {x}, {y}, {z}",
                "",
                "",
                List.of(),
                true
        );
    }

    private EmbedStyle defaultQuitEmbed() {
        return new EmbedStyle(
                "Player Activity",
                "",
                "{player_avatar_url}",
                "{player} left",
                "**{server}** now has **{online}/{max_players}** players online.",
                "#8ecbff",
                "{world} | {x}, {y}, {z}",
                "",
                "",
                List.of(),
                true
        );
    }

    private EmbedStyle defaultFirstJoinEmbed() {
        return new EmbedStyle(
                "New Player",
                "",
                "{player_avatar_url}",
                "{player} joined for the first time",
                "Welcome to **{server}**. There are now **{online}/{max_players}** players online.",
                "#57F287",
                "{uuid}",
                "",
                "",
                List.of(new EmbedField("World", "{world}", true),
                        new EmbedField("Location", "{x}, {y}, {z}", true)),
                true
        );
    }

    private EmbedStyle defaultAdvancementEmbed() {
        return new EmbedStyle(
                "{advancement_type_label} | {player}",
                "",
                "{player_avatar_url}",
                "{advancement_title}",
                "{advancement_description}",
                "{advancement_color}",
                "{server} | {advancement_key}",
                "",
                "",
                List.of(new EmbedField("Player", "{player}", true),
                        new EmbedField("Type", "{advancement_type}", true)),
                true
        );
    }

    private EmbedStyle defaultDeathEmbed() {
        return new EmbedStyle(
                "Player Death",
                "",
                "{player_avatar_url}",
                "{player} died",
                "{death_message}",
                "#ED4245",
                "{world} | {x}, {y}, {z}",
                "",
                "",
                List.of(new EmbedField("Cause", "{death_cause}", true),
                        new EmbedField("Killer", "{killer}", true)),
                true
        );
    }

    private EmbedStyle defaultServerStartEmbed() {
        return new EmbedStyle(
                "Server Lifecycle",
                "",
                "",
                "{server} online",
                "{lifecycle_description}",
                "{lifecycle_color}",
                "Discord+",
                "",
                "",
                List.of(new EmbedField("Players", "{online}/{max_players}", true)),
                true
        );
    }

    private EmbedStyle defaultServerStopEmbed() {
        return new EmbedStyle(
                "Server Lifecycle",
                "",
                "",
                "{server} offline",
                "{lifecycle_description}",
                "{lifecycle_color}",
                "Discord+",
                "",
                "",
                List.of(),
                true
        );
    }

    private EmbedStyle defaultReloadEmbed() {
        return new EmbedStyle(
                "Server Lifecycle",
                "",
                "",
                "DiscordPlus reloaded",
                "{lifecycle_description}",
                "{lifecycle_color}",
                "{server}",
                "",
                "",
                List.of(new EmbedField("Players", "{online}/{max_players}", true)),
                true
        );
    }

    private EmbedStyle defaultStatusEmbed() {
        return new EmbedStyle(
                "Server Status",
                "",
                "",
                "{server}",
                "**{online}/{max_players}** players online.",
                "#2b98fd",
                "Discord+",
                "",
                "",
                List.of(new EmbedField("Players", "{players}", false)),
                true
        );
    }

    private Map<String, DiscordEventStyle> readBroadcastStyles() {
        Map<String, DiscordEventStyle> styles = new LinkedHashMap<>();
        styles.put("purchase", readEventStyle("discord-style.broadcasts.purchase", null,
                "**{broadcast_player}** purchased **{broadcast_message}**.",
                "embed", defaultPurchaseBroadcastEmbed()));
        styles.put("announcement", readEventStyle("discord-style.broadcasts.announcement", null,
                "**{broadcast_message}**",
                "embed", defaultAnnouncementBroadcastEmbed()));
        for (String key : List.of(
                "auction-listing",
                "auction-sale",
                "auction-bid",
                "auction-won",
                "auction-cancel",
                "auction-expire",
                "playtime-afk",
                "playtime-reward",
                "advancementplus-progress",
                "advancementplus-completion",
                "skinsplus-change",
                "parcel-delivery",
                "parcel-queued",
                "parcel-failed",
                "chatplus-broadcast"
        )) {
            styles.put(key, readEventStyle("discord-style.broadcasts." + key, null,
                    "**{broadcast_message}**", "embed", defaultAnnouncementBroadcastEmbed()));
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("discord-style.broadcasts");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String normalized = normalizeKey(key);
                if (normalized.isBlank()) {
                    continue;
                }
                styles.put(normalized, readEventStyle("discord-style.broadcasts." + key, null,
                        "**{broadcast_message}**", "embed", defaultAnnouncementBroadcastEmbed()));
            }
        }
        return Map.copyOf(styles);
    }

    private Map<String, IntegrationSettings> readIntegrations() {
        Map<String, IntegrationSettings> values = new LinkedHashMap<>();
        values.put("auctionsplus", readIntegration("auctionsplus", defaults(Map.of(
                "listing-created", true,
                "listing-sold", true,
                "bid-placed", true,
                "auction-won", true,
                "listing-cancelled", false,
                "listing-expired", false,
                "commands", true
        ))));
        values.put("playtimeplus", readIntegration("playtimeplus", defaults(Map.of(
                "afk", false,
                "reward", true,
                "commands", true
        ))));
        values.put("advancementplus", readIntegration("advancementplus", defaults(Map.of(
                "progress", false,
                "completion", false
        ))));
        values.put("skinsplus", readIntegration("skinsplus", defaults(Map.of(
                "change", false
        ))));
        values.put("parcel", readIntegration("parcel", defaults(Map.of(
                "queued", true,
                "delivered", true,
                "failed", true
        ))));
        values.put("chatplus", readIntegration("chatplus", defaults(Map.of(
                "broadcast", true
        ))));
        values.put("pointsplus", readIntegration("pointsplus", defaults(Map.of(
                "commands", true
        ))));
        return Map.copyOf(values);
    }

    private AuctionsPlusCommandSettings readAuctionsPlusCommandSettings() {
        String path = "integrations.auctionsplus.commands";
        return new AuctionsPlusCommandSettings(
                plugin.getConfig().getBoolean(path + ".allow-direct-messages", false),
                readStringList(path + ".allowed-channel-ids", List.of()),
                plugin.getConfig().getBoolean(path + ".require-player-online", false),
                Math.max(0, plugin.getConfig().getInt(path + ".cooldown-seconds", 3)),
                Math.max(0.0D, plugin.getConfig().getDouble(path + ".max-bid", 0.0D)),
                Math.max(1, Math.min(25, plugin.getConfig().getInt(path + ".list-limit", 10)))
        );
    }

    private PointsPlusCommandSettings readPointsPlusCommandSettings() {
        String path = "integrations.pointsplus.commands";
        return new PointsPlusCommandSettings(
                plugin.getConfig().getBoolean(path + ".direct-message-results", false),
                plugin.getConfig().getBoolean(path + ".allow-direct-messages", true),
                readStringList(path + ".allowed-channel-ids", List.of()),
                plugin.getConfig().getBoolean(path + ".require-linked-targets", true),
                Math.max(0L, plugin.getConfig().getLong(path + ".max-payment", 1_000_000L)),
                Math.max(1, Math.min(25, plugin.getConfig().getInt(path + ".top-limit", 10)))
        );
    }

    private PlaytimePlusCommandSettings readPlaytimePlusCommandSettings() {
        String path = "integrations.playtimeplus.commands";
        return new PlaytimePlusCommandSettings(
                plugin.getConfig().getBoolean(path + ".allow-direct-messages", true),
                readStringList(path + ".allowed-channel-ids", List.of()),
                Math.max(1, Math.min(25, plugin.getConfig().getInt(path + ".top-limit", 10)))
        );
    }

    private IntegrationSettings readIntegration(String key, Map<String, Boolean> fallbackEvents) {
        String path = "integrations." + key;
        boolean enabled = plugin.getConfig().getBoolean(path + ".enabled", true);
        Map<String, Boolean> events = new LinkedHashMap<>(fallbackEvents);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path + ".events");
        if (section != null) {
            for (String eventKey : section.getKeys(false)) {
                String normalized = normalizeKey(eventKey);
                if (!normalized.isBlank()) {
                    events.put(normalized, section.getBoolean(eventKey, events.getOrDefault(normalized, false)));
                }
            }
        }
        return new IntegrationSettings(enabled, Map.copyOf(events));
    }

    private Map<String, Boolean> defaults(Map<String, Boolean> values) {
        return new LinkedHashMap<>(values);
    }

    private EmbedStyle defaultPurchaseBroadcastEmbed() {
        return new EmbedStyle(
                "Purchase",
                "",
                "{broadcast_player_avatar_url}",
                "New Purchase",
                "**{broadcast_player}** purchased **{broadcast_message}**.",
                "#f5c542",
                "{server}",
                "",
                "",
                List.of(new EmbedField("Player", "{broadcast_player}", true),
                        new EmbedField("Item", "{broadcast_message}", true)),
                true
        );
    }

    private EmbedStyle defaultAnnouncementBroadcastEmbed() {
        return new EmbedStyle(
                "Broadcast",
                "",
                "",
                "{broadcast_label}",
                "{broadcast_message}",
                "#2b98fd",
                "{server}",
                "",
                "",
                List.of(),
                true
        );
    }

    private List<RoleMapping> readRoleMappings() {
        List<RoleMapping> mappings = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("role-sync.mappings");
        if (section == null) {
            return mappings;
        }
        for (String key : section.getKeys(false)) {
            String permission = section.getString(key + ".permission", "").trim();
            String roleId = section.getString(key + ".role-id", "").trim();
            if (!permission.isEmpty() && !roleId.isEmpty()) {
                mappings.add(new RoleMapping(permission, roleId));
            }
        }
        return List.copyOf(mappings);
    }

    private JoinLeaveSettings readJoinLeaveSettings() {
        return new JoinLeaveSettings(
                readStringList("join-leave.ignored-worlds", List.of())
        );
    }

    private FirstJoinSettings readFirstJoinSettings() {
        return new FirstJoinSettings(
                plugin.getConfig().getBoolean("first-join.also-send-join-message", false),
                readStringList("first-join.ignored-worlds", List.of())
        );
    }

    private AdvancementSettings readAdvancementSettings() {
        return new AdvancementSettings(
                plugin.getConfig().getBoolean("advancements.announce-chat-only", true),
                plugin.getConfig().getBoolean("advancements.include-hidden", false),
                readStringList("advancements.ignored-worlds", List.of()),
                readStringList("advancements.ignored-key-prefixes", List.of("minecraft:recipes/")),
                readStringList("advancements.ignored-keys", List.of()),
                readString("advancements.type-labels.task", "Advancement Made"),
                readString("advancements.type-labels.goal", "Goal Reached"),
                readString("advancements.type-labels.challenge", "Challenge Complete"),
                readString("advancements.type-colors.task", "#8ecbff"),
                readString("advancements.type-colors.goal", "#2b98fd"),
                readString("advancements.type-colors.challenge", "#b642ff")
        );
    }

    private DeathSettings readDeathSettings() {
        return new DeathSettings(
                plugin.getConfig().getBoolean("death-messages.pvp-only", false),
                readStringList("death-messages.ignored-worlds", List.of()),
                readStringList("death-messages.ignored-causes", List.of())
        );
    }

    private LifecycleSettings readLifecycleSettings() {
        return new LifecycleSettings(
                plugin.getConfig().getBoolean("lifecycle.send-startup", true),
                plugin.getConfig().getBoolean("lifecycle.send-shutdown", true),
                plugin.getConfig().getBoolean("lifecycle.send-reload", true),
                plugin.getConfig().getBoolean("lifecycle.use-staff-channel", false)
        );
    }

    private List<String> readStringList(String path, List<String> fallback) {
        List<String> source = plugin.getConfig().contains(path)
                ? plugin.getConfig().getStringList(path)
                : fallback;
        return source.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String readString(String path) {
        return readString(path, "");
    }

    private String readString(String path, String fallback) {
        String value = plugin.getConfig().getString(path, fallback);
        return value == null ? fallback : value.trim();
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean readBoolean(String path, String fallbackPath, boolean fallback) {
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getBoolean(path, fallback);
        }
        return plugin.getConfig().getBoolean(fallbackPath, fallback);
    }

    public void logWarnings() {
        if (botToken.isBlank()) {
            return;
        }
        if (!guildId.isBlank() && !isSnowflake(guildId)) {
            plugin.getLogger().warning("bot.guild-id should be a Discord numeric ID.");
        }
        if (guildId.isBlank()) {
            plugin.getLogger().warning("bot.guild-id is empty. Set it if the bot is in more than one Discord server.");
        }
        if ((minecraftToDiscord || discordToMinecraft) && chatChannelId.isBlank()) {
            plugin.getLogger().warning("channels.chat-channel-id is required for chat relay.");
        }
        if ((joinLeave || firstJoinMessages || advancementMessages || deathMessages || lifecycleMessages)
                && chatChannelId.isBlank() && eventsChannelId.isBlank()) {
            plugin.getLogger().warning("channels.chat-channel-id or channels.events-channel-id is required for event relay.");
        }
        if (!chatChannelId.isBlank() && !isSnowflake(chatChannelId)) {
            plugin.getLogger().warning("channels.chat-channel-id should be a Discord numeric ID.");
        }
        if (!eventsChannelId.isBlank() && !isSnowflake(eventsChannelId)) {
            plugin.getLogger().warning("channels.events-channel-id should be a Discord numeric ID.");
        }
        if (!staffChannelId.isBlank() && !isSnowflake(staffChannelId)) {
            plugin.getLogger().warning("channels.staff-channel-id should be a Discord numeric ID.");
        }
        if (!linkedRoleId.isBlank() && !isSnowflake(linkedRoleId)) {
            plugin.getLogger().warning("linking.linked-role-id should be a Discord numeric ID.");
        }
        if (roleSyncEnabled && roleMappings.isEmpty() && linkedRoleId.isBlank()) {
            plugin.getLogger().warning("role-sync is enabled, but no role IDs are configured.");
        }
        if (removeUnmatchedMappedRoles) {
            plugin.getLogger().warning("role-sync.remove-unmatched-mapped-roles is enabled. Ensure permissions load before DiscordPlus syncs.");
        }
        if (minecraftChatStyle.useWebhook() && minecraftChatStyle.webhookUrl().isBlank()) {
            plugin.getLogger().warning("discord-style.minecraft-chat.use-webhook is enabled, but webhook-url is empty.");
        }
        for (String channelId : pointsPlusCommandSettings.allowedChannelIds()) {
            if (!isSnowflake(channelId)) {
                plugin.getLogger().warning("integrations.pointsplus.commands.allowed-channel-ids contains an invalid Discord channel ID.");
            }
        }
        for (String channelId : auctionsPlusCommandSettings.allowedChannelIds()) {
            if (!isSnowflake(channelId)) {
                plugin.getLogger().warning("integrations.auctionsplus.commands.allowed-channel-ids contains an invalid Discord channel ID.");
            }
        }
        for (String channelId : playtimePlusCommandSettings.allowedChannelIds()) {
            if (!isSnowflake(channelId)) {
                plugin.getLogger().warning("integrations.playtimeplus.commands.allowed-channel-ids contains an invalid Discord channel ID.");
            }
        }
        for (RoleMapping mapping : roleMappings) {
            if (!isSnowflake(mapping.roleId())) {
                plugin.getLogger().warning("Role mapping for permission '" + mapping.permission() + "' has an invalid Discord role ID.");
            }
        }
    }

    private boolean isSnowflake(String value) {
        return value != null && value.matches("\\d{17,20}");
    }

    public String botToken() {
        return botToken;
    }

    public String guildId() {
        return guildId;
    }

    public long botConnectDelayTicks() {
        return botConnectDelayTicks;
    }

    public ActivityStyle activityStyle() {
        return activityStyle;
    }

    public String serverName() {
        return serverName;
    }

    public String inviteUrl() {
        return inviteUrl;
    }

    public String chatChannelId() {
        return chatChannelId;
    }

    public String eventsChannelId() {
        return eventsChannelId;
    }

    public String staffChannelId() {
        return staffChannelId;
    }

    public boolean minecraftToDiscord() {
        return minecraftToDiscord;
    }

    public boolean discordToMinecraft() {
        return discordToMinecraft;
    }

    public boolean joinLeave() {
        return joinLeave;
    }

    public boolean advancementMessages() {
        return advancementMessages;
    }

    public boolean firstJoinMessages() {
        return firstJoinMessages;
    }

    public boolean deathMessages() {
        return deathMessages;
    }

    public boolean lifecycleMessages() {
        return lifecycleMessages;
    }

    public boolean discordCommands() {
        return discordCommands;
    }

    public boolean slashCommandsEnabled() {
        return slashCommandsEnabled;
    }

    public boolean slashCommandsRefreshOnStart() {
        return slashCommandsRefreshOnStart;
    }

    public boolean clearGlobalCommandsWhenGuildSet() {
        return clearGlobalCommandsWhenGuildSet;
    }

    public boolean integrationEnabled(String integrationKey, String eventKey) {
        if (!integrationsEnabled) {
            return false;
        }
        IntegrationSettings settings = integrations.get(normalizeKey(integrationKey));
        return settings != null && settings.enabled()
                && settings.events().getOrDefault(normalizeKey(eventKey), false);
    }

    public boolean pointsPlusCommandsEnabled() {
        return integrationEnabled("pointsplus", "commands");
    }

    public boolean auctionsPlusCommandsEnabled() {
        return integrationEnabled("auctionsplus", "commands");
    }

    public AuctionsPlusCommandSettings auctionsPlusCommandSettings() {
        return auctionsPlusCommandSettings;
    }

    public PointsPlusCommandSettings pointsPlusCommandSettings() {
        return pointsPlusCommandSettings;
    }

    public boolean playtimePlusCommandsEnabled() {
        return integrationEnabled("playtimeplus", "commands");
    }

    public PlaytimePlusCommandSettings playtimePlusCommandSettings() {
        return playtimePlusCommandSettings;
    }

    public boolean linkingEnabled() {
        return linkingEnabled;
    }

    public String commandPrefix() {
        return commandPrefix;
    }

    public int codeExpiryMinutes() {
        return codeExpiryMinutes;
    }

    public String linkedRoleId() {
        return linkedRoleId;
    }

    public boolean roleSyncEnabled() {
        return roleSyncEnabled;
    }

    public int roleSyncIntervalMinutes() {
        return roleSyncIntervalMinutes;
    }

    public long joinSyncDelayTicks() {
        return joinSyncDelayTicks;
    }

    public boolean removeUnmatchedMappedRoles() {
        return removeUnmatchedMappedRoles;
    }

    public List<RoleMapping> roleMappings() {
        return roleMappings;
    }

    public JoinLeaveSettings joinLeaveSettings() {
        return joinLeaveSettings;
    }

    public FirstJoinSettings firstJoinSettings() {
        return firstJoinSettings;
    }

    public AdvancementSettings advancementSettings() {
        return advancementSettings;
    }

    public DeathSettings deathSettings() {
        return deathSettings;
    }

    public LifecycleSettings lifecycleSettings() {
        return lifecycleSettings;
    }

    public MinecraftChatStyle minecraftChatStyle() {
        return minecraftChatStyle;
    }

    public String discordToMinecraftFormat() {
        return discordToMinecraftFormat;
    }

    public DiscordEventStyle joinStyle() {
        return joinStyle;
    }

    public DiscordEventStyle quitStyle() {
        return quitStyle;
    }

    public DiscordEventStyle firstJoinStyle() {
        return firstJoinStyle;
    }

    public DiscordEventStyle advancementStyle() {
        return advancementStyle;
    }

    public DiscordEventStyle deathStyle() {
        return deathStyle;
    }

    public DiscordEventStyle serverStartStyle() {
        return serverStartStyle;
    }

    public DiscordEventStyle serverStopStyle() {
        return serverStopStyle;
    }

    public DiscordEventStyle reloadStyle() {
        return reloadStyle;
    }

    public DiscordEventStyle statusStyle() {
        return statusStyle;
    }

    public DiscordEventStyle broadcastStyle(String key) {
        return broadcastStyles.get(normalizeKey(key));
    }

    public Set<String> broadcastStyleKeys() {
        return broadcastStyles.keySet();
    }

    public String messagePrefix() {
        return messagePrefix;
    }

    public record RoleMapping(String permission, String roleId) {
    }

    public record IntegrationSettings(boolean enabled, Map<String, Boolean> events) {
    }

    public record AuctionsPlusCommandSettings(boolean allowDirectMessages, List<String> allowedChannelIds,
                                              boolean requirePlayerOnline, int cooldownSeconds, double maxBid,
                                              int listLimit) {
        public boolean allowsChannel(String channelId) {
            return allowedChannelIds.isEmpty() || allowedChannelIds.contains(channelId);
        }
    }

    public record PointsPlusCommandSettings(boolean directMessageResults, boolean allowDirectMessages,
                                            List<String> allowedChannelIds, boolean requireLinkedTargets,
                                            long maxPayment, int topLimit) {
        public boolean allowsChannel(String channelId) {
            return allowedChannelIds.isEmpty() || allowedChannelIds.contains(channelId);
        }
    }

    public record PlaytimePlusCommandSettings(boolean allowDirectMessages, List<String> allowedChannelIds,
                                              int topLimit) {
        public boolean allowsChannel(String channelId) {
            return allowedChannelIds.isEmpty() || allowedChannelIds.contains(channelId);
        }
    }

    public record JoinLeaveSettings(List<String> ignoredWorlds) {
        public boolean ignoresWorld(String worldName) {
            return ignoredWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
        }
    }

    public record FirstJoinSettings(boolean alsoSendJoinMessage, List<String> ignoredWorlds) {
        public boolean ignoresWorld(String worldName) {
            return ignoredWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
        }
    }

    public record AdvancementSettings(boolean announceChatOnly, boolean includeHidden,
                                      List<String> ignoredWorlds, List<String> ignoredKeyPrefixes, List<String> ignoredKeys,
                                      String taskLabel, String goalLabel, String challengeLabel,
                                      String taskColor, String goalColor, String challengeColor) {
        public boolean ignoresWorld(String worldName) {
            return ignoredWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
        }

        public String labelFor(String type) {
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "goal" -> goalLabel;
                case "challenge" -> challengeLabel;
                default -> taskLabel;
            };
        }

        public String colorFor(String type) {
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "goal" -> goalColor;
                case "challenge" -> challengeColor;
                default -> taskColor;
            };
        }
    }

    public record DeathSettings(boolean pvpOnly, List<String> ignoredWorlds, List<String> ignoredCauses) {
        public boolean ignoresWorld(String worldName) {
            return ignoredWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
        }

        public boolean ignoresCause(String cause) {
            return ignoredCauses.stream().anyMatch(ignored -> ignored.equalsIgnoreCase(cause));
        }
    }

    public record LifecycleSettings(boolean sendStartup, boolean sendShutdown, boolean sendReload,
                                    boolean useStaffChannel) {
    }

    public record ActivityStyle(boolean enabled, String type, String text, String url) {
    }

    public record MinecraftChatStyle(boolean useWebhook, String webhookUrl, String username, String avatarUrl, String content) {
    }

    public record DiscordEventStyle(String style, String text, EmbedStyle embed) {
        public boolean embedEnabled() {
            return "embed".equalsIgnoreCase(style);
        }
    }

    public record EmbedStyle(String authorName, String authorUrl, String authorIconUrl, String title,
                             String description, String color, String footer, String thumbnailUrl,
                             String imageUrl, List<EmbedField> fields, boolean timestamp) {
        public static EmbedStyle empty() {
            return new EmbedStyle("", "", "", "", "", "#2b98fd", "", "", "", List.of(), true);
        }
    }

    public record EmbedField(String name, String value, boolean inline) {
    }
}
