package dev.discordplus.points;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import dev.discordplus.util.TextSanitizer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PointsPlusDiscordCommands {
    private static final Pattern DISCORD_MENTION = Pattern.compile("<@!?(\\d{17,20})>");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final LinkManager linkManager;

    public PointsPlusDiscordCommands(JavaPlugin plugin, DiscordPlusConfig config, LinkManager linkManager) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
    }

    public boolean handle(MessageReceivedEvent event, String raw) {
        String prefix = config.commandPrefix();
        if (matches(raw, prefix, "balance")) {
            return handleKnownCommand(event, List.of("balance"));
        }
        if (matches(raw, prefix, "pay")) {
            return handleKnownCommand(event, withCommand("pay", body(raw, prefix, "pay")));
        }
        if (matches(raw, prefix, "points")) {
            return handleKnownCommand(event, split(body(raw, prefix, "points")));
        }
        return false;
    }

    public boolean handleSlash(SlashCommandInteractionEvent event) {
        if (!"points".equals(event.getName())) {
            return false;
        }
        if (!config.pointsPlusCommandsEnabled()) {
            event.reply("PointsPlus Discord commands are disabled.").setEphemeral(true).queue();
            return true;
        }

        DiscordPlusConfig.PointsPlusCommandSettings settings = config.pointsPlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            event.reply("PointsPlus Discord commands are disabled in DMs.").setEphemeral(true).queue();
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            event.reply("PointsPlus Discord commands are not enabled in this channel.").setEphemeral(true).queue();
            return true;
        }

        event.deferReply(true).queue(hook ->
                Bukkit.getScheduler().runTask(plugin, () -> runSlashCommand(event, hook)));
        return true;
    }

    private boolean handleKnownCommand(MessageReceivedEvent event, List<String> args) {
        if (!config.pointsPlusCommandsEnabled()) {
            replyPublic(event, "PointsPlus Discord commands are disabled.");
            return true;
        }

        DiscordPlusConfig.PointsPlusCommandSettings settings = config.pointsPlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            replyPublic(event, "PointsPlus Discord commands are disabled in DMs.");
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            replyPublic(event, "PointsPlus Discord commands are not enabled in this channel.");
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> runCommand(event, args));
        return true;
    }

    private void runCommand(MessageReceivedEvent event, List<String> args) {
        Optional<PointsApi> api = pointsApi();
        if (api.isEmpty()) {
            replyPublic(event, "PointsPlus is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = args.isEmpty() ? "balance" : args.get(0).toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "balance", "bal", "me" -> balance(event, api.get());
            case "pay", "send" -> pay(event, api.get(), args);
            case "top", "leaderboard", "lead" -> top(event, api.get(), args);
            case "help" -> help(event);
            default -> replyPublic(event, "Usage: `" + config.commandPrefix() + "points [balance|pay|top|help]`");
        }
    }

    private void balance(MessageReceivedEvent event, PointsApi api) {
        Optional<LinkedAccount> account = linkedSender(event);
        if (account.isEmpty()) {
            return;
        }

        try {
            long balance = api.balanceOrZero(account.get().playerUuid());
            int rank = api.rank(account.get().playerUuid());
            String rankText = rank > 0 ? " Rank: **#" + rank + "**." : "";
            replyCommandResult(event, "**" + account.get().playerName() + "** balance: **"
                    + points(balance) + "**." + rankText);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PointsPlus balance: " + e.getMessage());
            replyPublic(event, "Could not read your PointsPlus balance.");
        }
    }

    private void pay(MessageReceivedEvent event, PointsApi api, List<String> args) {
        Optional<LinkedAccount> source = linkedSender(event);
        if (source.isEmpty()) {
            return;
        }
        if (args.size() != 3) {
            replyPublic(event, "Usage: `" + config.commandPrefix() + "points pay <@user|player> <amount>`");
            return;
        }

        OptionalLong amount = parseAmount(args.get(2));
        if (amount.isEmpty()) {
            replyPublic(event, "Use a positive whole amount like `100`, `1k`, or `1.5m`.");
            return;
        }
        long maxPayment = config.pointsPlusCommandSettings().maxPayment();
        if (maxPayment > 0L && amount.getAsLong() > maxPayment) {
            replyPublic(event, "The Discord payment limit is **" + points(maxPayment) + "**.");
            return;
        }

        Optional<TargetAccount> target;
        try {
            target = resolveTarget(api, args.get(1));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not resolve PointsPlus payment target: " + e.getMessage());
            replyPublic(event, "Could not resolve that PointsPlus account.");
            return;
        }
        if (target.isEmpty()) {
            replyPublic(event, config.pointsPlusCommandSettings().requireLinkedTargets()
                    ? "That target is not linked to DiscordPlus."
                    : "That PointsPlus account was not found.");
            return;
        }
        if (source.get().playerUuid().equals(target.get().uuid())) {
            replyPublic(event, "You cannot send points to yourself.");
            return;
        }

        try {
            if (!api.canPay()) {
                replyPublic(event, "Update PointsPlus to use Discord payments.");
                return;
            }
            boolean paid = api.pay(source.get().playerUuid(), source.get().playerName(),
                    target.get().uuid(), target.get().name(), amount.getAsLong());
            long nextBalance = api.balanceOrZero(source.get().playerUuid());
            if (!paid) {
                replyCommandResult(event, "Payment failed. Balance: **" + points(nextBalance) + "**.");
                return;
            }

            replyCommandResult(event, "Sent **" + points(amount.getAsLong()) + "** to **"
                    + target.get().name() + "**. Balance: **" + points(nextBalance) + "**.");
            target.get().linkedAccount().ifPresent(linked -> notifyTarget(event, linked,
                    "**" + source.get().playerName() + "** sent you **" + points(amount.getAsLong()) + "**."));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not complete PointsPlus payment: " + e.getMessage());
            replyPublic(event, "Could not complete that PointsPlus payment.");
        }
    }

    private void top(MessageReceivedEvent event, PointsApi api, List<String> args) {
        Optional<LinkedAccount> account = linkedSender(event);
        if (account.isEmpty()) {
            return;
        }

        int limit = config.pointsPlusCommandSettings().topLimit();
        if (args.size() >= 2) {
            try {
                limit = Math.max(1, Math.min(limit, Integer.parseInt(args.get(1))));
            } catch (NumberFormatException ignored) {
                replyPublic(event, "Use a number between 1 and " + config.pointsPlusCommandSettings().topLimit() + ".");
                return;
            }
        }

        try {
            List<Balance> top = api.top(limit);
            if (top.isEmpty()) {
                replyCommandResult(event, "No PointsPlus balances have been recorded yet.");
                return;
            }

            StringBuilder builder = new StringBuilder("**Top Points**");
            for (int index = 0; index < top.size(); index++) {
                Balance balance = top.get(index);
                builder.append('\n')
                        .append(index + 1)
                        .append(". **")
                        .append(TextSanitizer.safeDiscord(balance.name()))
                        .append("** - ")
                        .append(points(balance.balance()));
            }
            replyCommandResult(event, builder.toString());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PointsPlus leaderboard: " + e.getMessage());
            replyPublic(event, "Could not read the PointsPlus leaderboard.");
        }
    }

    private void help(MessageReceivedEvent event) {
        String prefix = config.commandPrefix();
        replyCommandResult(event, "PointsPlus commands:"
                + "\n`" + prefix + "balance`"
                + "\n`" + prefix + "points pay <@user|player> <amount>`"
                + "\n`" + prefix + "pay <@user|player> <amount>`"
                + "\n`" + prefix + "points top [limit]`");
    }

    private void runSlashCommand(SlashCommandInteractionEvent event, InteractionHook hook) {
        Optional<PointsApi> api = pointsApi();
        if (api.isEmpty()) {
            slashReply(hook, "PointsPlus is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = event.getSubcommandName() == null ? "balance" : event.getSubcommandName();
        switch (subcommand) {
            case "balance" -> slashBalance(event, hook, api.get());
            case "pay" -> slashPay(event, hook, api.get());
            case "top" -> slashTop(event, hook, api.get());
            default -> slashReply(hook, "Unknown PointsPlus command.");
        }
    }

    private void slashBalance(SlashCommandInteractionEvent event, InteractionHook hook, PointsApi api) {
        Optional<LinkedAccount> account = linkedSender(event, hook);
        if (account.isEmpty()) {
            return;
        }

        try {
            long balance = api.balanceOrZero(account.get().playerUuid());
            int rank = api.rank(account.get().playerUuid());
            String rankText = rank > 0 ? " Rank: **#" + rank + "**." : "";
            slashReply(hook, "**" + account.get().playerName() + "** balance: **"
                    + points(balance) + "**." + rankText);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PointsPlus balance: " + e.getMessage());
            slashReply(hook, "Could not read your PointsPlus balance.");
        }
    }

    private void slashPay(SlashCommandInteractionEvent event, InteractionHook hook, PointsApi api) {
        Optional<LinkedAccount> source = linkedSender(event, hook);
        if (source.isEmpty()) {
            return;
        }

        User targetUser = event.getOption("user", null, OptionMapping::getAsUser);
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        if (targetUser == null) {
            slashReply(hook, "Choose a linked Discord user.");
            return;
        }
        if (amount <= 0L) {
            slashReply(hook, "Use a positive whole amount.");
            return;
        }
        long maxPayment = config.pointsPlusCommandSettings().maxPayment();
        if (maxPayment > 0L && amount > maxPayment) {
            slashReply(hook, "The Discord payment limit is **" + points(maxPayment) + "**.");
            return;
        }

        Optional<LinkedAccount> linkedTarget = linkManager.findByDiscordId(targetUser.getId());
        if (linkedTarget.isEmpty()) {
            slashReply(hook, "That Discord user is not linked to DiscordPlus.");
            return;
        }
        if (source.get().playerUuid().equals(linkedTarget.get().playerUuid())) {
            slashReply(hook, "You cannot send points to yourself.");
            return;
        }

        try {
            if (!api.canPay()) {
                slashReply(hook, "Update PointsPlus to use Discord payments.");
                return;
            }
            boolean paid = api.pay(source.get().playerUuid(), source.get().playerName(),
                    linkedTarget.get().playerUuid(), linkedTarget.get().playerName(), amount);
            long nextBalance = api.balanceOrZero(source.get().playerUuid());
            if (!paid) {
                slashReply(hook, "Payment failed. Balance: **" + points(nextBalance) + "**.");
                return;
            }

            slashReply(hook, "Sent **" + points(amount) + "** to **"
                    + linkedTarget.get().playerName() + "**. Balance: **" + points(nextBalance) + "**.");
            notifyTarget(event, linkedTarget.get(),
                    "**" + source.get().playerName() + "** sent you **" + points(amount) + "**.");
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not complete PointsPlus payment: " + e.getMessage());
            slashReply(hook, "Could not complete that PointsPlus payment.");
        }
    }

    private void slashTop(SlashCommandInteractionEvent event, InteractionHook hook, PointsApi api) {
        Optional<LinkedAccount> account = linkedSender(event, hook);
        if (account.isEmpty()) {
            return;
        }

        int configuredLimit = config.pointsPlusCommandSettings().topLimit();
        int limit = event.getOption("limit", configuredLimit, OptionMapping::getAsInt);
        limit = Math.max(1, Math.min(configuredLimit, limit));

        try {
            List<Balance> top = api.top(limit);
            if (top.isEmpty()) {
                slashReply(hook, "No PointsPlus balances have been recorded yet.");
                return;
            }

            StringBuilder builder = new StringBuilder("**Top Points**");
            for (int index = 0; index < top.size(); index++) {
                Balance balance = top.get(index);
                builder.append('\n')
                        .append(index + 1)
                        .append(". **")
                        .append(TextSanitizer.safeDiscord(balance.name()))
                        .append("** - ")
                        .append(points(balance.balance()));
            }
            slashReply(hook, builder.toString());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not read PointsPlus leaderboard: " + e.getMessage());
            slashReply(hook, "Could not read the PointsPlus leaderboard.");
        }
    }

    private Optional<LinkedAccount> linkedSender(MessageReceivedEvent event) {
        Optional<LinkedAccount> account = linkManager.findByDiscordId(event.getAuthor().getId());
        if (account.isEmpty()) {
            replyPublic(event, "Your Discord account is not linked. Run `/discord link` in-game, then `"
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

    private Optional<TargetAccount> resolveTarget(PointsApi api, String input) throws ReflectiveOperationException {
        Optional<String> discordId = discordId(input);
        if (discordId.isPresent()) {
            return linkManager.findByDiscordId(discordId.get())
                    .map(account -> new TargetAccount(account.playerUuid(), account.playerName(), Optional.of(account)));
        }

        Optional<LinkedAccount> linkedByName = linkManager.findByPlayerName(input);
        if (linkedByName.isPresent()) {
            LinkedAccount account = linkedByName.get();
            return Optional.of(new TargetAccount(account.playerUuid(), account.playerName(), Optional.of(account)));
        }

        Optional<Balance> balance = api.findByName(input);
        if (balance.isEmpty()) {
            return Optional.empty();
        }

        Optional<LinkedAccount> linked = linkManager.findByPlayer(balance.get().uuid());
        if (config.pointsPlusCommandSettings().requireLinkedTargets() && linked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TargetAccount(balance.get().uuid(), balance.get().name(), linked));
    }

    private Optional<PointsApi> pointsApi() {
        Plugin source = Bukkit.getPluginManager().getPlugin("PointsPlus");
        if (source == null || !source.isEnabled()) {
            return Optional.empty();
        }
        try {
            Class<?> apiClass = Class.forName("dev.pointsplus.api.PointsPlusApi", true,
                    source.getClass().getClassLoader());
            return Optional.of(new PointsApi(
                    apiClass.getMethod("balanceOrZero", UUID.class),
                    apiClass.getMethod("findByName", String.class),
                    apiClass.getMethod("top", int.class),
                    apiClass.getMethod("rank", UUID.class),
                    payMethod(apiClass)
            ));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            plugin.getLogger().warning("Could not hook PointsPlus API: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Method payMethod(Class<?> apiClass) {
        try {
            return apiClass.getMethod("pay", UUID.class, String.class, UUID.class, String.class, long.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Optional<String> discordId(String input) {
        Matcher matcher = DISCORD_MENTION.matcher(input);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private OptionalLong parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }
        String normalized = input.trim().replace(",", "").replace("_", "").toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        char last = normalized.charAt(normalized.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b' || last == 't') {
            multiplier = switch (last) {
                case 'k' -> 1_000L;
                case 'm' -> 1_000_000L;
                case 'b' -> 1_000_000_000L;
                default -> 1_000_000_000_000L;
            };
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            BigDecimal value = new BigDecimal(normalized).multiply(BigDecimal.valueOf(multiplier));
            long amount = value.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return amount > 0L ? OptionalLong.of(amount) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private boolean matches(String raw, String prefix, String command) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalized = (prefix + command).toLowerCase(Locale.ROOT);
        return lower.equals(normalized) || lower.startsWith(normalized + " ");
    }

    private String body(String raw, String prefix, String command) {
        return raw.substring(prefix.length() + command.length()).trim();
    }

    private List<String> withCommand(String command, String body) {
        List<String> bodyParts = split(body);
        if (bodyParts.isEmpty()) {
            return List.of(command);
        }
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(command), bodyParts.stream()).toList();
    }

    private List<String> split(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return List.of(body.trim().split("\\s+"));
    }

    private String points(long amount) {
        return NUMBER_FORMAT.format(amount) + " " + (amount == 1L ? "point" : "points");
    }

    private void replyPublic(MessageReceivedEvent event, String message) {
        event.getMessage().reply(TextSanitizer.truncate(message, 1900)).queue();
    }

    private void replyCommandResult(MessageReceivedEvent event, String message) {
        replyPublic(event, message);
    }

    private void notifyTarget(MessageReceivedEvent event, LinkedAccount target, String message) {
        if (target.discordId().equals(event.getAuthor().getId())) {
            return;
        }
        event.getJDA().retrieveUserById(target.discordId()).queue(user ->
                user.openPrivateChannel().queue(privateChannel ->
                        privateChannel.sendMessage(TextSanitizer.truncate(message, 1900)).queue(), ignored -> {
                        }), ignored -> {
        });
    }

    private void notifyTarget(SlashCommandInteractionEvent event, LinkedAccount target, String message) {
        if (target.discordId().equals(event.getUser().getId())) {
            return;
        }
        event.getJDA().retrieveUserById(target.discordId()).queue(user ->
                user.openPrivateChannel().queue(privateChannel ->
                        privateChannel.sendMessage(TextSanitizer.truncate(message, 1900)).queue(), ignored -> {
                        }), ignored -> {
        });
    }

    private void slashReply(InteractionHook hook, String message) {
        hook.editOriginal(TextSanitizer.truncate(message, 1900)).queue();
    }

    private record TargetAccount(UUID uuid, String name, Optional<LinkedAccount> linkedAccount) {
    }

    private record Balance(UUID uuid, String name, long balance) {
    }

    private record PointsApi(Method balanceOrZeroMethod, Method findByNameMethod, Method topMethod, Method rankMethod,
                             Method payMethod) {
        long balanceOrZero(UUID uuid) throws ReflectiveOperationException {
            return (Long) invoke(balanceOrZeroMethod, uuid);
        }

        Optional<Balance> findByName(String name) throws ReflectiveOperationException {
            Object result = invoke(findByNameMethod, name);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                return Optional.of(balance(optional.get()));
            }
            return Optional.empty();
        }

        List<Balance> top(int limit) throws ReflectiveOperationException {
            Object result = invoke(topMethod, limit);
            if (!(result instanceof List<?> rawBalances)) {
                return List.of();
            }
            java.util.ArrayList<Balance> balances = new java.util.ArrayList<>();
            for (Object rawBalance : rawBalances) {
                balances.add(balance(rawBalance));
            }
            return List.copyOf(balances);
        }

        int rank(UUID uuid) throws ReflectiveOperationException {
            return (Integer) invoke(rankMethod, uuid);
        }

        boolean canPay() {
            return payMethod != null;
        }

        boolean pay(UUID sourceId, String sourceName, UUID targetId, String targetName, long amount)
                throws ReflectiveOperationException {
            return (Boolean) invoke(payMethod, sourceId, sourceName, targetId, targetName, amount);
        }

        private static Balance balance(Object rawBalance) throws ReflectiveOperationException {
            Class<?> type = rawBalance.getClass();
            UUID uuid = (UUID) type.getMethod("uuid").invoke(rawBalance);
            String name = (String) type.getMethod("name").invoke(rawBalance);
            long balance = (Long) type.getMethod("balance").invoke(rawBalance);
            return new Balance(uuid, name, balance);
        }

        private static Object invoke(Method method, Object... args) throws ReflectiveOperationException {
            if (method == null) {
                throw new NoSuchMethodException("PointsPlus pay API is unavailable.");
            }
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
