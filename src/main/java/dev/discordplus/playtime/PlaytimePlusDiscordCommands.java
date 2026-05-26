package dev.discordplus.playtime;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import dev.discordplus.util.TextSanitizer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaytimePlusDiscordCommands {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final LinkManager linkManager;

    public PlaytimePlusDiscordCommands(JavaPlugin plugin, DiscordPlusConfig config, LinkManager linkManager) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
    }

    public boolean handle(MessageReceivedEvent event, String raw) {
        String prefix = config.commandPrefix();
        if (matches(raw, prefix, "playtime")) {
            return handleKnownCommand(event, split(body(raw, prefix, "playtime")));
        }
        if (matches(raw, prefix, "pt")) {
            return handleKnownCommand(event, split(body(raw, prefix, "pt")));
        }
        return false;
    }

    public boolean handleSlash(SlashCommandInteractionEvent event) {
        if (!"playtime".equals(event.getName())) {
            return false;
        }
        if (!config.playtimePlusCommandsEnabled()) {
            event.reply("PlaytimePlus Discord commands are disabled.").setEphemeral(true).queue();
            return true;
        }

        DiscordPlusConfig.PlaytimePlusCommandSettings settings = config.playtimePlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            event.reply("PlaytimePlus Discord commands are disabled in DMs.").setEphemeral(true).queue();
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            event.reply("PlaytimePlus Discord commands are not enabled in this channel.").setEphemeral(true).queue();
            return true;
        }

        event.deferReply(false).queue(hook ->
                Bukkit.getScheduler().runTask(plugin, () -> runSlashCommand(event, hook)));
        return true;
    }

    private boolean handleKnownCommand(MessageReceivedEvent event, List<String> args) {
        if (!config.playtimePlusCommandsEnabled()) {
            reply(event, "PlaytimePlus Discord commands are disabled.");
            return true;
        }

        DiscordPlusConfig.PlaytimePlusCommandSettings settings = config.playtimePlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            reply(event, "PlaytimePlus Discord commands are disabled in DMs.");
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            reply(event, "PlaytimePlus Discord commands are not enabled in this channel.");
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> runCommand(event, args));
        return true;
    }

    private void runCommand(MessageReceivedEvent event, List<String> args) {
        Optional<PlaytimeApi> api = playtimeApi();
        if (api.isEmpty()) {
            reply(event, "PlaytimePlus is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = args.isEmpty() ? "me" : args.get(0).toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "me", "self" -> self(event, api.get());
            case "top", "leaderboard", "lead" -> top(event, api.get(), args);
            case "help" -> help(event);
            default -> player(event, api.get(), args.get(0));
        }
    }

    private void self(MessageReceivedEvent event, PlaytimeApi api) {
        Optional<LinkedAccount> account = linkedSender(event);
        if (account.isEmpty()) {
            return;
        }
        try {
            Optional<View> view = api.snapshot(account.get().playerUuid());
            if (view.isEmpty()) {
                reply(event, "No PlaytimePlus record was found for **" + safe(account.get().playerName()) + "**.");
                return;
            }
            reply(event, statsMessage(view.get(), true));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PlaytimePlus playtime: " + e.getMessage());
            reply(event, "Could not read your PlaytimePlus stats.");
        }
    }

    private void player(MessageReceivedEvent event, PlaytimeApi api, String playerName) {
        try {
            Optional<View> view = api.findByName(playerName);
            if (view.isEmpty()) {
                reply(event, "No PlaytimePlus record was found for **" + safe(playerName) + "**.");
                return;
            }
            reply(event, statsMessage(view.get(), false));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PlaytimePlus player stats: " + e.getMessage());
            reply(event, "Could not read that PlaytimePlus record.");
        }
    }

    private void top(MessageReceivedEvent event, PlaytimeApi api, List<String> args) {
        Metric metric = Metric.ACTIVE;
        int limit = config.playtimePlusCommandSettings().topLimit();
        if (args.size() >= 2) {
            Optional<Metric> parsedMetric = Metric.from(args.get(1));
            if (parsedMetric.isPresent()) {
                metric = parsedMetric.get();
            } else if (isInteger(args.get(1))) {
                limit = Math.max(1, Math.min(limit, Integer.parseInt(args.get(1))));
            } else {
                reply(event, "Metric must be `active`, `total`, or `afk`.");
                return;
            }
        }
        if (args.size() >= 3) {
            if (!isInteger(args.get(2))) {
                reply(event, "Use a number between 1 and " + config.playtimePlusCommandSettings().topLimit() + ".");
                return;
            }
            limit = Math.max(1, Math.min(config.playtimePlusCommandSettings().topLimit(), Integer.parseInt(args.get(2))));
        }

        try {
            List<View> top = api.top(metric, limit);
            if (top.isEmpty()) {
                reply(event, "No PlaytimePlus records have been recorded yet.");
                return;
            }

            StringBuilder builder = new StringBuilder("**Top ")
                    .append(metric.key())
                    .append(" playtime**");
            for (int index = 0; index < top.size(); index++) {
                View view = top.get(index);
                builder.append('\n')
                        .append(index + 1)
                        .append(". **")
                        .append(safe(view.name()))
                        .append("** - ")
                        .append(formatDuration(view.metricMillis(metric)));
            }
            reply(event, builder.toString());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PlaytimePlus leaderboard: " + e.getMessage());
            reply(event, "Could not read the PlaytimePlus leaderboard.");
        }
    }

    private void help(MessageReceivedEvent event) {
        String prefix = config.commandPrefix();
        reply(event, "PlaytimePlus commands:"
                + "\n`" + prefix + "playtime` - your linked playtime"
                + "\n`" + prefix + "playtime <player>` - another player's playtime"
                + "\n`" + prefix + "playtime top [active|total|afk] [limit]` - leaderboard"
                + "\nAlias: `" + prefix + "pt`");
    }

    private void runSlashCommand(SlashCommandInteractionEvent event, InteractionHook hook) {
        Optional<PlaytimeApi> api = playtimeApi();
        if (api.isEmpty()) {
            slashReply(hook, "PlaytimePlus is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = event.getSubcommandName() == null ? "me" : event.getSubcommandName();
        switch (subcommand) {
            case "me" -> slashSelf(event, hook, api.get());
            case "player" -> slashPlayer(event, hook, api.get());
            case "top" -> slashTop(event, hook, api.get());
            default -> slashReply(hook, "Unknown PlaytimePlus command.");
        }
    }

    private void slashSelf(SlashCommandInteractionEvent event, InteractionHook hook, PlaytimeApi api) {
        Optional<LinkedAccount> account = linkedSender(event, hook);
        if (account.isEmpty()) {
            return;
        }
        try {
            Optional<View> view = api.snapshot(account.get().playerUuid());
            if (view.isEmpty()) {
                slashReply(hook, "No PlaytimePlus record was found for **" + safe(account.get().playerName()) + "**.");
                return;
            }
            slashReply(hook, statsMessage(view.get(), true));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PlaytimePlus playtime: " + e.getMessage());
            slashReply(hook, "Could not read your PlaytimePlus stats.");
        }
    }

    private void slashPlayer(SlashCommandInteractionEvent event, InteractionHook hook, PlaytimeApi api) {
        String playerName = event.getOption("name", "", OptionMapping::getAsString).trim();
        if (playerName.isBlank()) {
            slashReply(hook, "Enter a player name.");
            return;
        }
        try {
            Optional<View> view = api.findByName(playerName);
            if (view.isEmpty()) {
                slashReply(hook, "No PlaytimePlus record was found for **" + safe(playerName) + "**.");
                return;
            }
            slashReply(hook, statsMessage(view.get(), false));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PlaytimePlus player stats: " + e.getMessage());
            slashReply(hook, "Could not read that PlaytimePlus record.");
        }
    }

    private void slashTop(SlashCommandInteractionEvent event, InteractionHook hook, PlaytimeApi api) {
        Metric metric = Metric.from(event.getOption("metric", "active", OptionMapping::getAsString))
                .orElse(Metric.ACTIVE);
        int configuredLimit = config.playtimePlusCommandSettings().topLimit();
        int limit = event.getOption("limit", configuredLimit, OptionMapping::getAsInt);
        limit = Math.max(1, Math.min(configuredLimit, limit));

        try {
            List<View> top = api.top(metric, limit);
            if (top.isEmpty()) {
                slashReply(hook, "No PlaytimePlus records have been recorded yet.");
                return;
            }

            StringBuilder builder = new StringBuilder("**Top ")
                    .append(metric.key())
                    .append(" playtime**");
            for (int index = 0; index < top.size(); index++) {
                View view = top.get(index);
                builder.append('\n')
                        .append(index + 1)
                        .append(". **")
                        .append(safe(view.name()))
                        .append("** - ")
                        .append(formatDuration(view.metricMillis(metric)));
            }
            slashReply(hook, builder.toString());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PlaytimePlus leaderboard: " + e.getMessage());
            slashReply(hook, "Could not read the PlaytimePlus leaderboard.");
        }
    }

    private Optional<LinkedAccount> linkedSender(MessageReceivedEvent event) {
        Optional<LinkedAccount> account = linkManager.findByDiscordId(event.getAuthor().getId());
        if (account.isEmpty()) {
            reply(event, "Your Discord account is not linked. Run `/discord link` in-game, then `"
                    + config.commandPrefix() + "link <code>` in Discord.");
        }
        return account;
    }

    private Optional<LinkedAccount> linkedSender(SlashCommandInteractionEvent event, InteractionHook hook) {
        Optional<LinkedAccount> account = linkManager.findByDiscordId(event.getUser().getId());
        if (account.isEmpty()) {
            slashReply(hook, "Your Discord account is not linked. Run `/discord link` in-game, then `/discord link code:<code>` in Discord.");
        }
        return account;
    }

    private Optional<PlaytimeApi> playtimeApi() {
        Plugin source = Bukkit.getPluginManager().getPlugin("PlaytimePlus");
        if (source == null || !source.isEnabled()) {
            return Optional.empty();
        }
        try {
            Class<?> apiClass = Class.forName("dev.playtimeplus.api.PlaytimePlusApi", true,
                    source.getClass().getClassLoader());
            Class<?> metricClass = Class.forName("dev.playtimeplus.time.TimeMetric", true,
                    source.getClass().getClassLoader());
            return Optional.of(new PlaytimeApi(
                    metricClass,
                    apiClass.getMethod("snapshot", UUID.class),
                    apiClass.getMethod("findByName", String.class),
                    apiClass.getMethod("top", metricClass, int.class)
            ));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            plugin.getLogger().warning("Could not hook PlaytimePlus API: " + e.getMessage());
            return Optional.empty();
        }
    }

    private String statsMessage(View view, boolean self) throws ReflectiveOperationException {
        String status = view.afk()
                ? "AFK" + (view.afkReason().isBlank() ? "" : " - " + safe(view.afkReason()))
                : view.online() ? "online" : "offline";
        return (self ? "Your PlaytimePlus stats" : "**" + safe(view.name()) + "** PlaytimePlus stats")
                + "\nTotal: **" + formatDuration(view.totalMillis()) + "**"
                + "\nActive: **" + formatDuration(view.activeMillis()) + "**"
                + "\nAFK: **" + formatDuration(view.afkMillis()) + "**"
                + "\nSession: **" + formatDuration(view.sessionMillis()) + "**"
                + "\nStatus: **" + status + "**";
    }

    private boolean matches(String raw, String prefix, String command) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalized = (prefix + command).toLowerCase(Locale.ROOT);
        return lower.equals(normalized) || lower.startsWith(normalized + " ");
    }

    private String body(String raw, String prefix, String command) {
        return raw.substring(prefix.length() + command.length()).trim();
    }

    private List<String> split(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return List.of(body.trim().split("\\s+"));
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append("d ");
        }
        if (hours > 0L) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0L) {
            builder.append(minutes).append("m ");
        }
        if (builder.isEmpty() || seconds > 0L) {
            builder.append(seconds).append("s");
        }
        return builder.toString().trim();
    }

    private String safe(String value) {
        return TextSanitizer.safeDiscord(value == null ? "" : value);
    }

    private void reply(MessageReceivedEvent event, String message) {
        event.getMessage().reply(TextSanitizer.truncate(message, 1900)).queue();
    }

    private void slashReply(InteractionHook hook, String message) {
        hook.editOriginal(TextSanitizer.truncate(message, 1900)).queue();
    }

    private enum Metric {
        ACTIVE("active"),
        TOTAL("total"),
        AFK("afk");

        private final String key;

        Metric(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }

        private static Optional<Metric> from(String value) {
            if (value == null) {
                return Optional.empty();
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            for (Metric metric : values()) {
                if (metric.key.equals(normalized)) {
                    return Optional.of(metric);
                }
            }
            return Optional.empty();
        }
    }

    private record View(Object raw) {
        private UUID uuid() throws ReflectiveOperationException {
            return (UUID) invoke("uuid");
        }

        private String name() throws ReflectiveOperationException {
            return (String) invoke("name");
        }

        private long activeMillis() throws ReflectiveOperationException {
            return (Long) invoke("activeMillis");
        }

        private long afkMillis() throws ReflectiveOperationException {
            return (Long) invoke("afkMillis");
        }

        private long totalMillis() throws ReflectiveOperationException {
            return (Long) invoke("totalMillis");
        }

        private boolean online() throws ReflectiveOperationException {
            return (Boolean) invoke("online");
        }

        private boolean afk() throws ReflectiveOperationException {
            return (Boolean) invoke("afk");
        }

        private long sessionMillis() throws ReflectiveOperationException {
            return (Long) invoke("sessionMillis");
        }

        private String afkReason() throws ReflectiveOperationException {
            return (String) invoke("afkReason");
        }

        private long metricMillis(Metric metric) throws ReflectiveOperationException {
            return switch (metric) {
                case ACTIVE -> activeMillis();
                case TOTAL -> totalMillis();
                case AFK -> afkMillis();
            };
        }

        private Object invoke(String methodName) throws ReflectiveOperationException {
            try {
                return raw.getClass().getMethod(methodName).invoke(raw);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                throw new InvocationTargetException(cause);
            }
        }
    }

    private record PlaytimeApi(Class<?> metricClass, Method snapshotMethod, Method findByNameMethod, Method topMethod) {
        private Optional<View> snapshot(UUID uuid) throws ReflectiveOperationException {
            return optionalView(invoke(snapshotMethod, uuid));
        }

        private Optional<View> findByName(String playerName) throws ReflectiveOperationException {
            return optionalView(invoke(findByNameMethod, playerName));
        }

        private List<View> top(Metric metric, int limit) throws ReflectiveOperationException {
            Object result = invoke(topMethod, metricEnum(metric), limit);
            if (!(result instanceof List<?> rawViews)) {
                return List.of();
            }
            java.util.ArrayList<View> views = new java.util.ArrayList<>();
            for (Object rawView : rawViews) {
                views.add(new View(rawView));
            }
            return List.copyOf(views);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object metricEnum(Metric metric) {
            return Enum.valueOf((Class<Enum>) metricClass, metric.name());
        }

        private Optional<View> optionalView(Object result) {
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                return Optional.of(new View(optional.get()));
            }
            return Optional.empty();
        }

        private static Object invoke(Method method, Object... args) throws ReflectiveOperationException {
            try {
                return method.invoke(null, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                throw new InvocationTargetException(cause);
            }
        }
    }
}
