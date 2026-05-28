package dev.discordplus.orders;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.util.TextSanitizer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrdersPlusDiscordCommands {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;

    public OrdersPlusDiscordCommands(JavaPlugin plugin, DiscordPlusConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean handle(MessageReceivedEvent event, String raw) {
        String prefix = config.commandPrefix();
        for (String command : List.of("orders", "order")) {
            if (matches(raw, prefix, command)) {
                return handleKnownCommand(event, split(body(raw, prefix, command)));
            }
        }
        return false;
    }

    public boolean handleSlash(SlashCommandInteractionEvent event) {
        if (!"orders".equals(event.getName())) {
            return false;
        }
        if (!config.ordersPlusCommandsEnabled()) {
            event.reply("Orders+ Discord commands are disabled.").setEphemeral(true).queue();
            return true;
        }

        DiscordPlusConfig.OrdersPlusCommandSettings settings = config.ordersPlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            event.reply("Orders+ Discord commands are disabled in DMs.").setEphemeral(true).queue();
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            event.reply("Orders+ Discord commands are not enabled in this channel.").setEphemeral(true).queue();
            return true;
        }

        event.deferReply(true).queue(hook ->
                Bukkit.getScheduler().runTask(plugin, () -> runSlashCommand(event, hook)));
        return true;
    }

    private boolean handleKnownCommand(MessageReceivedEvent event, List<String> args) {
        if (!config.ordersPlusCommandsEnabled()) {
            reply(event, "Orders+ Discord commands are disabled.");
            return true;
        }

        DiscordPlusConfig.OrdersPlusCommandSettings settings = config.ordersPlusCommandSettings();
        if (!event.isFromGuild() && !settings.allowDirectMessages()) {
            reply(event, "Orders+ Discord commands are disabled in DMs.");
            return true;
        }
        if (event.isFromGuild() && !settings.allowsChannel(event.getChannel().getId())) {
            reply(event, "Orders+ Discord commands are not enabled in this channel.");
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> runCommand(event, args));
        return true;
    }

    private void runCommand(MessageReceivedEvent event, List<String> args) {
        Optional<OrdersApi> api = ordersApi();
        if (api.isEmpty()) {
            reply(event, "Orders+ is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = args.isEmpty() ? "list" : args.get(0).toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "list", "active", "browse" -> list(event, api.get(), request(args, 1));
            case "search", "find" -> list(event, api.get(), request(args, 1));
            case "help", "commands", "?" -> help(event);
            default -> list(event, api.get(), request(args, 0));
        }
    }

    private void list(MessageReceivedEvent event, OrdersApi api, ListRequest request) {
        try {
            List<OrderView> orders = api.activeOrders(request.limit(), request.search());
            reply(event, formatOrders(orders, request.search()));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not list Orders+ Discord orders: " + e.getMessage());
            reply(event, "Could not list active Orders+ orders.");
        }
    }

    private void help(MessageReceivedEvent event) {
        String prefix = config.commandPrefix();
        reply(event, "Orders+ commands:"
                + "\n`" + prefix + "orders list [limit] [search]` - show active orders and IDs"
                + "\n`" + prefix + "orders search <item|buyer|id>` - find matching active orders"
                + "\nFulfill orders in-game with `/orders fulfill <id> [amount]`.");
    }

    private void runSlashCommand(SlashCommandInteractionEvent event, InteractionHook hook) {
        Optional<OrdersApi> api = ordersApi();
        if (api.isEmpty()) {
            slashReply(hook, "Orders+ is not installed, enabled, or compatible.");
            return;
        }

        String subcommand = event.getSubcommandName() == null ? "" : event.getSubcommandName();
        switch (subcommand) {
            case "list" -> slashList(event, hook, api.get());
            default -> slashReply(hook, "Unknown Orders+ command.");
        }
    }

    private void slashList(SlashCommandInteractionEvent event, InteractionHook hook, OrdersApi api) {
        int configuredLimit = config.ordersPlusCommandSettings().listLimit();
        int limit = event.getOption("limit", configuredLimit, OptionMapping::getAsInt);
        limit = Math.max(1, Math.min(configuredLimit, limit));
        String search = event.getOption("search", "", OptionMapping::getAsString).trim();

        try {
            List<OrderView> orders = api.activeOrders(limit, search);
            slashReply(hook, formatOrders(orders, search));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not list Orders+ Discord slash orders: " + e.getMessage());
            slashReply(hook, "Could not list active Orders+ orders.");
        }
    }

    private Optional<OrdersApi> ordersApi() {
        Plugin source = Bukkit.getPluginManager().getPlugin("OrdersPlus");
        if (source == null || !source.isEnabled()) {
            return Optional.empty();
        }
        try {
            Class<?> apiClass = Class.forName("dev.ordersplus.api.OrdersPlusApi", true,
                    source.getClass().getClassLoader());
            return Optional.of(new OrdersApi(apiClass.getMethod("activeOrders", int.class, String.class)));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            plugin.getLogger().warning("Could not hook Orders+ API: " + e.getMessage());
            return Optional.empty();
        }
    }

    private ListRequest request(List<String> args, int start) {
        int limit = config.ordersPlusCommandSettings().listLimit();
        int searchStart = Math.min(start, args.size());
        if (searchStart < args.size()) {
            try {
                int requestedLimit = Integer.parseInt(args.get(searchStart));
                if (requestedLimit > 0) {
                    limit = Math.min(limit, requestedLimit);
                    searchStart++;
                }
            } catch (NumberFormatException ignored) {
                // First argument is a search term.
            }
        }
        String search = searchStart >= args.size() ? "" : String.join(" ", args.subList(searchStart, args.size())).trim();
        return new ListRequest(limit, search);
    }

    private String formatOrders(List<OrderView> orders, String search) {
        if (orders.isEmpty()) {
            if (search == null || search.isBlank()) {
                return "No active Orders+ orders right now.";
            }
            return "No active Orders+ orders match `" + safe(search) + "`.";
        }

        long now = System.currentTimeMillis();
        StringBuilder builder = new StringBuilder("**Active Orders**");
        for (OrderView order : orders) {
            builder.append('\n')
                    .append("`#")
                    .append(order.id())
                    .append("` **")
                    .append(order.remainingAmount())
                    .append("/")
                    .append(order.originalAmount())
                    .append("x ")
                    .append(safe(order.materialName()))
                    .append("** by **")
                    .append(safe(order.buyerName()))
                    .append("** - **")
                    .append(safe(order.formattedPriceEach()))
                    .append("** each, **")
                    .append(safe(order.formattedRemainingValue()))
                    .append("** total, ")
                    .append(formatRemaining(order.expiresAtMillis() - now));
        }
        builder.append("\nFulfill in-game with `/orders fulfill <id> [amount]`.");
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

    private String safe(String value) {
        return TextSanitizer.safeDiscord(value);
    }

    private record ListRequest(int limit, String search) {
    }

    private record OrderView(long id, String buyerName, String materialName, int originalAmount,
                             int remainingAmount, String formattedPriceEach, String formattedRemainingValue,
                             long expiresAtMillis) {
    }

    private record OrdersApi(Method activeOrdersMethod) {
        private List<OrderView> activeOrders(int limit, String search) throws ReflectiveOperationException {
            Object result = invoke(activeOrdersMethod, limit, search == null ? "" : search);
            if (!(result instanceof List<?> rawOrders)) {
                return List.of();
            }
            ArrayList<OrderView> orders = new ArrayList<>();
            for (Object rawOrder : rawOrders) {
                Class<?> type = rawOrder.getClass();
                orders.add(new OrderView(
                        (Long) type.getMethod("id").invoke(rawOrder),
                        (String) type.getMethod("buyerName").invoke(rawOrder),
                        (String) type.getMethod("materialName").invoke(rawOrder),
                        (Integer) type.getMethod("originalAmount").invoke(rawOrder),
                        (Integer) type.getMethod("remainingAmount").invoke(rawOrder),
                        (String) type.getMethod("formattedPriceEach").invoke(rawOrder),
                        (String) type.getMethod("formattedRemainingValue").invoke(rawOrder),
                        (Long) type.getMethod("expiresAtMillis").invoke(rawOrder)
                ));
            }
            return List.copyOf(orders);
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
