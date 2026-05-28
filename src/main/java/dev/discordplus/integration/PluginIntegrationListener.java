package dev.discordplus.integration;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import dev.discordplus.util.TextSanitizer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginIntegrationListener implements Listener {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;
    private final Set<String> registeredEvents = ConcurrentHashMap.newKeySet();

    public PluginIntegrationListener(JavaPlugin plugin, DiscordPlusConfig config, DiscordBotService botService) {
        this.plugin = plugin;
        this.config = config;
        this.botService = botService;
    }

    public void registerAvailable() {
        registerIf("AuctionsPlus", "dev.auctionsplus.api.event.AuctionListingCreatedEvent",
                config.integrationEnabled("auctionsplus", "listing-created"), this::auctionCreated);
        registerIf("AuctionsPlus", "dev.auctionsplus.api.event.AuctionListingSoldEvent",
                config.integrationEnabled("auctionsplus", "listing-sold"), this::auctionSold);
        registerIf("AuctionsPlus", "dev.auctionsplus.api.event.AuctionBidPlacedEvent",
                config.integrationEnabled("auctionsplus", "bid-placed"), this::auctionBid);
        registerIf("AuctionsPlus", "dev.auctionsplus.api.event.AuctionWonEvent",
                config.integrationEnabled("auctionsplus", "auction-won"), this::auctionWon);
        registerIf("AuctionsPlus", "dev.auctionsplus.api.event.AuctionListingCancelledEvent",
                config.integrationEnabled("auctionsplus", "listing-cancelled"), this::auctionCancelled);
        registerIf("AuctionsPlus", "dev.auctionsplus.api.event.AuctionListingExpiredEvent",
                config.integrationEnabled("auctionsplus", "listing-expired"), this::auctionExpired);

        registerIf("OrdersPlus", "dev.ordersplus.api.event.OrderCreatedEvent",
                config.integrationEnabled("ordersplus", "order-created"), this::orderCreated);
        registerIf("OrdersPlus", "dev.ordersplus.api.event.OrderFulfilledEvent",
                config.integrationEnabled("ordersplus", "order-fulfilled"), this::orderFulfilled);
        registerIf("OrdersPlus", "dev.ordersplus.api.event.OrderCancelledEvent",
                config.integrationEnabled("ordersplus", "order-cancelled"), this::orderCancelled);
        registerIf("OrdersPlus", "dev.ordersplus.api.event.OrderExpiredEvent",
                config.integrationEnabled("ordersplus", "order-expired"), this::orderExpired);

        registerIf("PlaytimePlus", "dev.playtimeplus.api.event.PlaytimeAfkStateChangeEvent",
                config.integrationEnabled("playtimeplus", "afk"), this::playtimeAfk);
        registerIf("PlaytimePlus", "dev.playtimeplus.api.event.PlaytimeRewardClaimEvent",
                config.integrationEnabled("playtimeplus", "reward"), this::playtimeReward);

        registerIf("AdvancementPlus", "dev.advancementplus.api.event.AdvancementPlusBroadcastEvent",
                config.integrationEnabled("advancementplus", "progress")
                        || config.integrationEnabled("advancementplus", "completion"), this::advancementPlus);

        registerIf("SkinsPlus", "dev.skinsplus.api.event.SkinChangeEvent",
                config.integrationEnabled("skinsplus", "change"), this::skinChanged);

        registerIf("Parcel", "com.sqware.parcel.event.ParcelDeliveryQueuedEvent",
                config.integrationEnabled("parcel", "queued"), this::parcelQueued);
        registerIf("Parcel", "com.sqware.parcel.event.ParcelDeliveryExecutedEvent",
                config.integrationEnabled("parcel", "delivered")
                        || config.integrationEnabled("parcel", "failed"), this::parcelExecuted);

        registerIf("ChatPlus", "dev.chatplus.api.event.ChatPlusBroadcastEvent",
                config.integrationEnabled("chatplus", "broadcast"), this::chatPlusBroadcast);
    }

    @org.bukkit.event.EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        registerAvailable();
    }

    private void registerIf(String pluginName, String eventClassName, boolean enabled, Consumer<Event> handler) {
        if (!enabled || registeredEvents.contains(eventClassName)) {
            return;
        }
        Plugin source = Bukkit.getPluginManager().getPlugin(pluginName);
        if (source == null || !source.isEnabled()) {
            return;
        }
        try {
            Class<?> rawClass = Class.forName(eventClassName, false, source.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().warning("Could not hook " + eventClassName + " because it is not a Bukkit event.");
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            EventExecutor executor = (listener, event) -> handler.accept(event);
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, executor, plugin, false);
            registeredEvents.add(eventClassName);
            plugin.getLogger().info("Hooked Discord forwarding for " + eventClass.getSimpleName() + ".");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().fine("Optional integration event not found: " + eventClassName);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not register Discord forwarding for " + eventClassName + ": " + e.getMessage());
        }
    }

    private void auctionCreated(Event event) {
        if (!config.integrationEnabled("auctionsplus", "listing-created")) {
            return;
        }
        String listingType = string(event, "listingType");
        boolean bidAuction = "bid".equalsIgnoreCase(listingType);
        String action = bidAuction ? "started bidding for" : "listed";
        String priceLabel = bidAuction ? " at " : " for ";
        botService.sendBroadcast("auction-listing", string(event, "sellerName"),
                string(event, "sellerName") + " " + action + " " + string(event, "itemName")
                        + priceLabel + string(event, "formattedPrice") + " (#" + string(event, "listingId") + ").");
    }

    private void auctionSold(Event event) {
        if (!config.integrationEnabled("auctionsplus", "listing-sold")) {
            return;
        }
        botService.sendBroadcast("auction-sale", string(event, "buyerName"),
                string(event, "buyerName") + " bought " + string(event, "itemName")
                        + " from " + string(event, "sellerName") + " for " + string(event, "formattedPrice") + ".");
    }

    private void auctionBid(Event event) {
        if (!config.integrationEnabled("auctionsplus", "bid-placed")) {
            return;
        }
        botService.sendBroadcast("auction-bid", string(event, "bidderName"),
                string(event, "bidderName") + " bid " + string(event, "formattedBid")
                        + " on " + string(event, "itemName") + " from " + string(event, "sellerName") + ".");
    }

    private void auctionWon(Event event) {
        if (!config.integrationEnabled("auctionsplus", "auction-won")) {
            return;
        }
        botService.sendBroadcast("auction-won", string(event, "winnerName"),
                string(event, "winnerName") + " won " + string(event, "itemName")
                        + " from " + string(event, "sellerName") + " for "
                        + string(event, "formattedWinningBid") + ".");
    }

    private void auctionCancelled(Event event) {
        if (!config.integrationEnabled("auctionsplus", "listing-cancelled")) {
            return;
        }
        botService.sendBroadcast("auction-cancel", string(event, "sellerName"),
                "Auction #" + string(event, "listingId") + " for " + string(event, "itemName")
                        + " was cancelled by " + string(event, "cancelledByName") + ".");
    }

    private void auctionExpired(Event event) {
        if (!config.integrationEnabled("auctionsplus", "listing-expired")) {
            return;
        }
        botService.sendBroadcast("auction-expire", string(event, "sellerName"),
                string(event, "sellerName") + "'s auction for " + string(event, "itemName")
                        + " expired at " + string(event, "formattedPrice") + ".");
    }

    private void orderCreated(Event event) {
        if (!config.integrationEnabled("ordersplus", "order-created")) {
            return;
        }
        botService.sendBroadcast("order-created", string(event, "buyerName"),
                string(event, "buyerName") + " created order #" + string(event, "orderId")
                        + " for " + string(event, "amount") + "x " + string(event, "materialName")
                        + " at " + string(event, "formattedPriceEach") + " each ("
                        + string(event, "formattedTotal") + " total).");
    }

    private void orderFulfilled(Event event) {
        if (!config.integrationEnabled("ordersplus", "order-fulfilled")) {
            return;
        }
        String completion = bool(event, "completed") ? " and completed the order" : "";
        botService.sendBroadcast("order-fulfilled", string(event, "fulfillerName"),
                string(event, "fulfillerName") + " fulfilled " + string(event, "amount")
                        + "x " + string(event, "materialName") + " for "
                        + string(event, "buyerName") + completion + " ("
                        + string(event, "formattedPayout") + ").");
    }

    private void orderCancelled(Event event) {
        if (!config.integrationEnabled("ordersplus", "order-cancelled")) {
            return;
        }
        botService.sendBroadcast("order-cancel", string(event, "buyerName"),
                "Order #" + string(event, "orderId") + " for "
                        + string(event, "remainingAmount") + "x " + string(event, "materialName")
                        + " was cancelled by " + string(event, "cancelledByName")
                        + ". Refunded " + string(event, "formattedRefund") + ".");
    }

    private void orderExpired(Event event) {
        if (!config.integrationEnabled("ordersplus", "order-expired")) {
            return;
        }
        botService.sendBroadcast("order-expire", string(event, "buyerName"),
                string(event, "buyerName") + "'s order #" + string(event, "orderId")
                        + " for " + string(event, "remainingAmount") + "x "
                        + string(event, "materialName") + " expired. Refunded "
                        + string(event, "formattedRefund") + ".");
    }

    private void playtimeAfk(Event event) {
        if (!config.integrationEnabled("playtimeplus", "afk")) {
            return;
        }
        boolean afk = bool(event, "afk");
        String reason = string(event, "reason");
        String message = afk
                ? string(event, "playerName") + " is now AFK" + (reason.isBlank() ? "." : ": " + reason)
                : string(event, "playerName") + " is no longer AFK after " + formatDuration(longValue(event, "durationMillis")) + ".";
        botService.sendBroadcast("playtime-afk", string(event, "playerName"), message);
    }

    private void playtimeReward(Event event) {
        if (!config.integrationEnabled("playtimeplus", "reward")) {
            return;
        }
        botService.sendBroadcast("playtime-reward", string(event, "playerName"),
                string(event, "playerName") + " claimed " + string(event, "rewardName")
                        + " (#" + string(event, "claimNumber") + ") for "
                        + formatDuration(longValue(event, "thresholdMillis")) + " " + enumKey(event, "metric") + " playtime.");
    }

    private void advancementPlus(Event event) {
        String kind = string(event, "kind").toLowerCase(Locale.ROOT);
        if (!config.integrationEnabled("advancementplus", kind)) {
            return;
        }
        String style = "completion".equals(kind) ? "advancementplus-completion" : "advancementplus-progress";
        String message = string(event, "message");
        if (message.isBlank()) {
            message = string(event, "playerName") + " " + ("completion".equals(kind) ? "completed " : "progressed ")
                    + string(event, "title") + ".";
        }
        botService.sendBroadcast(style, string(event, "playerName"), message);
    }

    private void skinChanged(Event event) {
        if (!config.integrationEnabled("skinsplus", "change")) {
            return;
        }
        String source = string(event, "sourceName");
        String mode = string(event, "mode");
        String message = "none".equalsIgnoreCase(mode) || source.isBlank()
                ? string(event, "playerName") + " disabled their custom skin."
                : string(event, "playerName") + " changed their skin to " + source + ".";
        botService.sendBroadcast("skinsplus-change", string(event, "playerName"), message);
    }

    private void parcelQueued(Event event) {
        if (!config.integrationEnabled("parcel", "queued")) {
            return;
        }
        botService.sendBroadcast("parcel-queued", string(event, "playerName"),
                "Parcel queued order " + string(event, "orderId") + " until "
                        + playerLabel(event) + " is online.");
    }

    private void parcelExecuted(Event event) {
        boolean success = bool(event, "success");
        if (success && !config.integrationEnabled("parcel", "delivered")) {
            return;
        }
        if (!success && !config.integrationEnabled("parcel", "failed")) {
            return;
        }
        String style = success ? "parcel-delivery" : "parcel-failed";
        String message = success
                ? "Parcel delivered order " + string(event, "orderId") + " to " + playerLabel(event) + "."
                : "Parcel failed order " + string(event, "orderId") + " for " + playerLabel(event)
                + ": " + string(event, "error");
        botService.sendBroadcast(style, string(event, "playerName"), message);
    }

    private void chatPlusBroadcast(Event event) {
        if (!config.integrationEnabled("chatplus", "broadcast")) {
            return;
        }
        botService.sendBroadcast("chatplus-broadcast", "",
                "[" + string(event, "categoryLabel") + "] " + string(event, "message"));
    }

    private String playerLabel(Event event) {
        String playerName = string(event, "playerName");
        if (!playerName.isBlank()) {
            return playerName;
        }
        String playerUuid = string(event, "playerUuid");
        return playerUuid.isBlank() ? "the target player" : playerUuid;
    }

    private String enumKey(Event event, String methodName) {
        Object value = value(event, methodName);
        if (value == null) {
            return "";
        }
        try {
            Method key = value.getClass().getMethod("key");
            Object rendered = key.invoke(value);
            return rendered == null ? "" : String.valueOf(rendered);
        } catch (NoSuchMethodException ignored) {
            if (value instanceof Enum<?> enumValue) {
                return enumValue.name().toLowerCase(Locale.ROOT);
            }
            return String.valueOf(value).toLowerCase(Locale.ROOT);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return String.valueOf(value).toLowerCase(Locale.ROOT);
        }
    }

    private String string(Event event, String methodName) {
        Object value = value(event, methodName);
        return value == null ? "" : TextSanitizer.stripMinecraftColor(String.valueOf(value));
    }

    private boolean bool(Event event, String methodName) {
        Object value = value(event, methodName);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private long longValue(Event event, String methodName) {
        Object value = value(event, methodName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private Object value(Event event, String methodName) {
        for (String candidate : methodCandidates(methodName)) {
            try {
                Method method = event.getClass().getMethod(candidate);
                return method.invoke(event);
            } catch (NoSuchMethodException ignored) {
                // Try the next method name.
            } catch (IllegalAccessException | InvocationTargetException e) {
                plugin.getLogger().fine("Could not read integration event value " + methodName + ": " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    private String[] methodCandidates(String methodName) {
        String capitalized = methodName.isBlank()
                ? methodName
                : Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        return new String[]{methodName, "get" + capitalized, "is" + capitalized};
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
}
