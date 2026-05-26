package dev.discordplus.auctions;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import dev.discordplus.util.TextSanitizer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionsPlusDiscordCommands {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final LinkManager linkManager;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public AuctionsPlusDiscordCommands(JavaPlugin plugin, DiscordPlusConfig config, LinkManager linkManager) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
    }

    public boolean handle(MessageReceivedEvent event, String raw) {
        String prefix = config.commandPrefix();
        for (String command : List.of("ah", "auc", "auction", "auctions")) {
            if (matches(raw, prefix, command)) {
                return handleKnownCommand(event, split(body(raw, prefix, command)));
            }
        }
        return false;
    }

    public boolean handleSlash(SlashCommandInteractionEvent event) {
        if (!"auction".equals(event.getName())) {
            return false;
        }
        if (!config.auctionsPlusCommandsEnabled()) {
            event.reply("Auctions+ Discord commands are disabled.").setEphemeral(true).queue();
            return true;
        }

        DiscordPlusConfig.AuctionsPlusCommandSettings settings = config.auctionsPlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            event.reply("Auctions+ Discord commands are disabled in DMs.").setEphemeral(true).queue();
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            event.reply("Auctions+ Discord commands are not enabled in this channel.").setEphemeral(true).queue();
            return true;
        }

        event.deferReply(true).queue(hook ->
                Bukkit.getScheduler().runTask(plugin, () -> runSlashCommand(event, hook)));
        return true;
    }

    private boolean handleKnownCommand(MessageReceivedEvent event, List<String> args) {
        if (!config.auctionsPlusCommandsEnabled()) {
            reply(event, "Auctions+ Discord commands are disabled.");
            return true;
        }

        DiscordPlusConfig.AuctionsPlusCommandSettings settings = config.auctionsPlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            reply(event, "Auctions+ Discord commands are disabled in DMs.");
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            reply(event, "Auctions+ Discord commands are not enabled in this channel.");
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> runCommand(event, args));
        return true;
    }

    private void runCommand(MessageReceivedEvent event, List<String> args) {
        Optional<AuctionsApi> api = auctionsApi();
        if (api.isEmpty()) {
            reply(event, "Auctions+ is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = args.isEmpty() ? "help" : args.get(0).toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "list", "auctions", "active", "browse" -> list(event, api.get(), args);
            case "bid" -> bid(event, api.get(), args);
            case "help", "commands", "?" -> help(event);
            default -> reply(event, "Usage: `" + config.commandPrefix() + "ah list` or `"
                    + config.commandPrefix() + "ah bid <id> <amount>`");
        }
    }

    private void list(MessageReceivedEvent event, AuctionsApi api, List<String> args) {
        int limit = config.auctionsPlusCommandSettings().listLimit();
        if (args.size() >= 2) {
            try {
                limit = Math.max(1, Math.min(limit, Integer.parseInt(args.get(1))));
            } catch (NumberFormatException e) {
                reply(event, "Use a number between 1 and " + config.auctionsPlusCommandSettings().listLimit() + ".");
                return;
            }
        }

        try {
            List<ListingView> listings = api.activeAuctions(limit);
            reply(event, formatListings(listings));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not list Auctions+ Discord auctions: " + e.getMessage());
            reply(event, "Could not list active Auctions+ auctions.");
        }
    }

    private void bid(MessageReceivedEvent event, AuctionsApi api, List<String> args) {
        Optional<LinkedAccount> account = linkedSender(event);
        if (account.isEmpty()) {
            return;
        }
        if (args.size() != 3) {
            reply(event, "Usage: `" + config.commandPrefix() + "ah bid <id> <amount>`");
            return;
        }

        Optional<Long> listingId = parseListingId(args.get(1));
        if (listingId.isEmpty()) {
            reply(event, "Use a positive listing ID from `/ah`.");
            return;
        }
        OptionalDouble bid = parseBid(args.get(2));
        if (bid.isEmpty()) {
            reply(event, "Use a positive bid amount like `500`, `1.5k`, or `2m`.");
            return;
        }
        if (!withinDiscordLimit(bid.getAsDouble())) {
            reply(event, "Discord bids are limited to **" + formatNumber(config.auctionsPlusCommandSettings().maxBid()) + "**.");
            return;
        }
        if (config.auctionsPlusCommandSettings().requirePlayerOnline()
                && Bukkit.getPlayer(account.get().playerUuid()) == null) {
            reply(event, "Your linked Minecraft player must be online to bid from Discord.");
            return;
        }
        Optional<Long> cooldown = cooldownRemaining(event.getAuthor().getId());
        if (cooldown.isPresent()) {
            reply(event, "Wait **" + cooldown.get() + "s** before bidding again from Discord.");
            return;
        }
        markCooldown(event.getAuthor().getId());

        try {
            AuctionResult result = api.bid(account.get().playerUuid(), account.get().playerName(),
                    listingId.get(), bid.getAsDouble());
            reply(event, formatResult(result));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not complete Auctions+ Discord bid: " + e.getMessage());
            reply(event, "Could not complete that Auctions+ bid.");
        }
    }

    private void help(MessageReceivedEvent event) {
        String prefix = config.commandPrefix();
        reply(event, "Auctions+ commands:"
                + "\n`" + prefix + "ah list [limit]` - show active bid auctions and IDs"
                + "\n`" + prefix + "ah bid <id> <amount>` - bid as your linked Minecraft account"
                + "\nAliases: `" + prefix + "auc`, `" + prefix + "auction`");
    }

    private void runSlashCommand(SlashCommandInteractionEvent event, InteractionHook hook) {
        Optional<AuctionsApi> api = auctionsApi();
        if (api.isEmpty()) {
            slashReply(hook, "Auctions+ is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = event.getSubcommandName() == null ? "" : event.getSubcommandName();
        switch (subcommand) {
            case "list" -> slashList(event, hook, api.get());
            case "bid" -> slashBid(event, hook, api.get());
            default -> slashReply(hook, "Unknown Auctions+ command.");
        }
    }

    private void slashList(SlashCommandInteractionEvent event, InteractionHook hook, AuctionsApi api) {
        int configuredLimit = config.auctionsPlusCommandSettings().listLimit();
        int limit = event.getOption("limit", configuredLimit, OptionMapping::getAsInt);
        limit = Math.max(1, Math.min(configuredLimit, limit));

        try {
            List<ListingView> listings = api.activeAuctions(limit);
            slashReply(hook, formatListings(listings));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not list Auctions+ Discord slash auctions: " + e.getMessage());
            slashReply(hook, "Could not list active Auctions+ auctions.");
        }
    }

    private void slashBid(SlashCommandInteractionEvent event, InteractionHook hook, AuctionsApi api) {
        Optional<LinkedAccount> account = linkedSender(event, hook);
        if (account.isEmpty()) {
            return;
        }

        long listingId = event.getOption("id", 0L, OptionMapping::getAsLong);
        String amount = event.getOption("amount", "", OptionMapping::getAsString);
        if (listingId <= 0L) {
            slashReply(hook, "Use a positive listing ID from `/ah`.");
            return;
        }
        OptionalDouble bid = parseBid(amount);
        if (bid.isEmpty()) {
            slashReply(hook, "Use a positive bid amount like `500`, `1.5k`, or `2m`.");
            return;
        }
        if (!withinDiscordLimit(bid.getAsDouble())) {
            slashReply(hook, "Discord bids are limited to **" + formatNumber(config.auctionsPlusCommandSettings().maxBid()) + "**.");
            return;
        }
        if (config.auctionsPlusCommandSettings().requirePlayerOnline()
                && Bukkit.getPlayer(account.get().playerUuid()) == null) {
            slashReply(hook, "Your linked Minecraft player must be online to bid from Discord.");
            return;
        }
        Optional<Long> cooldown = cooldownRemaining(event.getUser().getId());
        if (cooldown.isPresent()) {
            slashReply(hook, "Wait **" + cooldown.get() + "s** before bidding again from Discord.");
            return;
        }
        markCooldown(event.getUser().getId());

        try {
            AuctionResult result = api.bid(account.get().playerUuid(), account.get().playerName(), listingId, bid.getAsDouble());
            slashReply(hook, formatResult(result));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not complete Auctions+ Discord slash bid: " + e.getMessage());
            slashReply(hook, "Could not complete that Auctions+ bid.");
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

    private Optional<AuctionsApi> auctionsApi() {
        Plugin source = Bukkit.getPluginManager().getPlugin("AuctionsPlus");
        if (source == null || !source.isEnabled()) {
            return Optional.empty();
        }
        try {
            Class<?> apiClass = Class.forName("dev.auctionsplus.api.AuctionsPlusApi", true,
                    source.getClass().getClassLoader());
            return Optional.of(new AuctionsApi(
                    apiClass.getMethod("bid", UUID.class, String.class, long.class, double.class),
                    apiClass.getMethod("activeAuctions", int.class)
            ));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            plugin.getLogger().warning("Could not hook Auctions+ API: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Long> parseListingId(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0L ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private OptionalDouble parseBid(String input) {
        if (input == null || input.isBlank()) {
            return OptionalDouble.empty();
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
        if (normalized.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            BigDecimal decimal = new BigDecimal(normalized).multiply(BigDecimal.valueOf(multiplier));
            double value = decimal.doubleValue();
            return Double.isFinite(value) && value > 0.0D ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private boolean withinDiscordLimit(double bid) {
        double maxBid = config.auctionsPlusCommandSettings().maxBid();
        return maxBid <= 0.0D || bid <= maxBid;
    }

    private Optional<Long> cooldownRemaining(String discordId) {
        int cooldownSeconds = config.auctionsPlusCommandSettings().cooldownSeconds();
        if (cooldownSeconds <= 0) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        Long nextAllowed = cooldowns.get(discordId);
        if (nextAllowed == null || nextAllowed <= now) {
            cooldowns.remove(discordId);
            return Optional.empty();
        }
        return Optional.of(Math.max(1L, (nextAllowed - now + 999L) / 1000L));
    }

    private void markCooldown(String discordId) {
        int cooldownSeconds = config.auctionsPlusCommandSettings().cooldownSeconds();
        if (cooldownSeconds > 0) {
            cooldowns.put(discordId, System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
    }

    private String formatResult(AuctionResult result) {
        Map<String, String> values = result.placeholders();
        String message = switch (result.messageKey()) {
            case "bid-complete" -> "Bid **" + value(values, "bid") + "** on auction **#"
                    + value(values, "id") + "**, **" + value(values, "item") + "**. Next minimum: **"
                    + value(values, "next_bid") + "**.";
            case "no-permission" -> "Your linked Minecraft account does not have `auctionsplus.bid`.";
            case "permission-check-unavailable" -> "Auctions+ could not verify your offline `auctionsplus.bid` permission. Join the server or install a Vault permissions provider.";
            case "invalid-player" -> "Your linked Minecraft account could not be verified.";
            case "bidding-disabled" -> "Bidding auctions are disabled.";
            case "economy-unavailable" -> "No Vault economy provider is available.";
            case "listing-not-found" -> "That auction no longer exists.";
            case "listing-expired" -> "That auction has expired.";
            case "listing-unavailable" -> "That auction is no longer available.";
            case "not-biddable" -> "That listing is buy-now only. Use `/ah buy " + value(values, "id") + "` in-game.";
            case "cannot-bid-own" -> "You cannot bid on your own auction.";
            case "bid-too-low" -> "Bid at least **" + value(values, "min_bid") + "**. Current bid: **"
                    + value(values, "current_bid") + "**.";
            case "bid-too-high" -> "Bid at most **" + value(values, "max_bid") + "**.";
            case "not-enough-money" -> "You need **" + value(values, "price") + "** available for that bid.";
            case "purchase-failed" -> "Economy transaction failed: `" + value(values, "reason") + "`.";
            case "api-unavailable" -> "Auctions+ is not ready yet.";
            default -> result.success() ? "Auction command completed." : "Auction command failed.";
        };
        return result.success() ? message : "Bid failed: " + message;
    }

    private String formatListings(List<ListingView> listings) {
        if (listings.isEmpty()) {
            return "No active bid auctions right now.";
        }
        long now = System.currentTimeMillis();
        StringBuilder builder = new StringBuilder("**Active Auctions**");
        for (ListingView listing : listings) {
            builder.append('\n')
                    .append("`#")
                    .append(listing.id())
                    .append("` **")
                    .append(TextSanitizer.safeDiscord(listing.itemName()))
                    .append("** by **")
                    .append(TextSanitizer.safeDiscord(listing.sellerName()))
                    .append("** - current **")
                    .append(TextSanitizer.safeDiscord(listing.formattedCurrentPrice()))
                    .append("**, min **")
                    .append(TextSanitizer.safeDiscord(listing.formattedMinimumBid()))
                    .append("**, ")
                    .append(formatRemaining(listing.expiresAtMillis() - now));
        }
        builder.append("\nBid with `")
                .append(config.commandPrefix())
                .append("ah bid <id> <amount>`.");
        return builder.toString();
    }

    private String formatRemaining(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h left";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m left";
        }
        if (minutes > 0L) {
            return minutes + "m left";
        }
        return Math.max(1L, seconds) + "s left";
    }

    private String value(Map<String, String> values, String key) {
        return TextSanitizer.safeDiscord(values.getOrDefault(key, ""));
    }

    private String formatNumber(double amount) {
        if (amount == Math.rint(amount)) {
            return Long.toString((long) amount);
        }
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
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

    private void reply(MessageReceivedEvent event, String message) {
        event.getMessage().reply(TextSanitizer.truncate(message, 1900)).queue();
    }

    private void slashReply(InteractionHook hook, String message) {
        hook.editOriginal(TextSanitizer.truncate(message, 1900)).queue();
    }

    private record AuctionResult(boolean success, String messageKey, Map<String, String> placeholders) {
    }

    private record ListingView(long id, String sellerName, String itemName, String formattedCurrentPrice,
                               String formattedMinimumBid, long expiresAtMillis) {
    }

    private record AuctionsApi(Method bidMethod, Method activeAuctionsMethod) {
        private AuctionResult bid(UUID bidderUuid, String bidderName, long listingId, double bid)
                throws ReflectiveOperationException {
            Object result = invoke(bidMethod, bidderUuid, bidderName, listingId, bid);
            Class<?> type = result.getClass();
            boolean success = (Boolean) type.getMethod("success").invoke(result);
            String messageKey = (String) type.getMethod("messageKey").invoke(result);
            Object rawPlaceholders = type.getMethod("placeholders").invoke(result);
            Map<String, String> placeholders = new LinkedHashMap<>();
            if (rawPlaceholders instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    placeholders.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return new AuctionResult(success, messageKey, Map.copyOf(placeholders));
        }

        private List<ListingView> activeAuctions(int limit) throws ReflectiveOperationException {
            Object result = invoke(activeAuctionsMethod, limit);
            if (!(result instanceof List<?> rawListings)) {
                return List.of();
            }
            java.util.ArrayList<ListingView> listings = new java.util.ArrayList<>();
            for (Object rawListing : rawListings) {
                Class<?> type = rawListing.getClass();
                listings.add(new ListingView(
                        (Long) type.getMethod("id").invoke(rawListing),
                        (String) type.getMethod("sellerName").invoke(rawListing),
                        (String) type.getMethod("itemName").invoke(rawListing),
                        (String) type.getMethod("formattedCurrentPrice").invoke(rawListing),
                        (String) type.getMethod("formattedMinimumBid").invoke(rawListing),
                        (Long) type.getMethod("expiresAtMillis").invoke(rawListing)
                ));
            }
            return List.copyOf(listings);
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
