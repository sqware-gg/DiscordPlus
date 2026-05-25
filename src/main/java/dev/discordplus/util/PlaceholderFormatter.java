package dev.discordplus.util;

import dev.discordplus.config.DiscordPlusConfig;
import java.util.Collection;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderFormatter {
    private PlaceholderFormatter() {
    }

    public static String discord(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message) {
        return TextSanitizer.safeDiscord(apply(template, plugin, config, player, message,
                OnlineSnapshot.current(), EventContext.empty()));
    }

    public static String discord(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                                 OnlineSnapshot onlineSnapshot) {
        return TextSanitizer.safeDiscord(apply(template, plugin, config, player, message,
                onlineSnapshot, EventContext.empty()));
    }

    public static String discord(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                                 OnlineSnapshot onlineSnapshot, AdvancementSnapshot advancementSnapshot) {
        return discord(template, plugin, config, player, message, onlineSnapshot, EventContext.advancement(advancementSnapshot));
    }

    public static String discord(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                                 OnlineSnapshot onlineSnapshot, EventContext eventContext) {
        return TextSanitizer.safeDiscord(apply(template, plugin, config, player, message, onlineSnapshot, eventContext));
    }

    public static String minecraft(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message) {
        return apply(template, plugin, config, player, message, OnlineSnapshot.current(), EventContext.empty());
    }

    public static String raw(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message) {
        return apply(template, plugin, config, player, message, OnlineSnapshot.current(), EventContext.empty());
    }

    public static String raw(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                             OnlineSnapshot onlineSnapshot) {
        return apply(template, plugin, config, player, message, onlineSnapshot, EventContext.empty());
    }

    public static String raw(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                             OnlineSnapshot onlineSnapshot, AdvancementSnapshot advancementSnapshot) {
        return raw(template, plugin, config, player, message, onlineSnapshot, EventContext.advancement(advancementSnapshot));
    }

    public static String raw(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                             OnlineSnapshot onlineSnapshot, EventContext eventContext) {
        return apply(template, plugin, config, player, message, onlineSnapshot, eventContext);
    }

    private static String apply(String template, JavaPlugin plugin, DiscordPlusConfig config, Player player, String message,
                                OnlineSnapshot onlineSnapshot, EventContext eventContext) {
        eventContext = eventContext == null ? EventContext.empty() : eventContext;
        String value = template == null ? "" : template;
        value = value.replace("{server}", config.serverName().isBlank() ? plugin.getServer().getName() : config.serverName());
        value = value.replace("{online}", String.valueOf(onlineSnapshot.online()));
        value = value.replace("{max_players}", String.valueOf(Bukkit.getMaxPlayers()));
        value = value.replace("{players}", onlineSnapshot.players());
        value = value.replace("{message}", message == null ? "" : message);
        value = applyEvent(value, eventContext);
        value = applyAdvancement(value, eventContext.advancementSnapshot());
        value = applyDeath(value, eventContext.deathSnapshot());
        value = applyLifecycle(value, eventContext.lifecycleSnapshot());
        value = applyBroadcast(value, eventContext.broadcastSnapshot());

        if (player == null) {
            value = value.replace("{player}", "")
                    .replace("{display_name}", "")
                    .replace("{uuid}", "")
                    .replace("{player_avatar_url}", "")
                    .replace("{world}", "")
                    .replace("{x}", "")
                    .replace("{y}", "")
                    .replace("{z}", "");
            return value;
        }

        Location location = player.getLocation();
        return value.replace("{player}", player.getName())
                .replace("{display_name}", TextSanitizer.stripMinecraftColor(player.getDisplayName()))
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{player_avatar_url}", "https://mc-heads.net/avatar/" + player.getName() + "/64")
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()));
    }

    private static String applyEvent(String value, EventContext eventContext) {
        return value.replace("{event_type}", eventContext.eventType())
                .replace("{event_label}", eventContext.eventLabel())
                .replace("{event_color}", eventContext.eventColor());
    }

    private static String applyAdvancement(String value, AdvancementSnapshot advancementSnapshot) {
        if (advancementSnapshot == null) {
            return value.replace("{advancement_key}", "")
                    .replace("{advancement_namespace}", "")
                    .replace("{advancement_path}", "")
                    .replace("{advancement_title}", "")
                    .replace("{advancement_description}", "")
                    .replace("{advancement_type}", "")
                    .replace("{advancement_type_label}", "")
                    .replace("{advancement_color}", "")
                    .replace("{advancement_hidden}", "")
                    .replace("{advancement_announces}", "");
        }
        return value.replace("{advancement_key}", advancementSnapshot.key())
                .replace("{advancement_namespace}", advancementSnapshot.namespace())
                .replace("{advancement_path}", advancementSnapshot.path())
                .replace("{advancement_title}", advancementSnapshot.title())
                .replace("{advancement_description}", advancementSnapshot.description())
                .replace("{advancement_type}", advancementSnapshot.type())
                .replace("{advancement_type_label}", advancementSnapshot.typeLabel())
                .replace("{advancement_color}", advancementSnapshot.color())
                .replace("{advancement_hidden}", String.valueOf(advancementSnapshot.hidden()))
                .replace("{advancement_announces}", String.valueOf(advancementSnapshot.announcesToChat()));
    }

    private static String applyDeath(String value, DeathSnapshot deathSnapshot) {
        if (deathSnapshot == null) {
            return value.replace("{death_message}", "")
                    .replace("{death_cause}", "")
                    .replace("{killer}", "")
                    .replace("{killer_uuid}", "")
                    .replace("{killer_display_name}", "")
                    .replace("{killer_avatar_url}", "")
                    .replace("{is_pvp_death}", "");
        }
        return value.replace("{death_message}", deathSnapshot.deathMessage())
                .replace("{death_cause}", deathSnapshot.deathCause())
                .replace("{killer}", deathSnapshot.killer())
                .replace("{killer_uuid}", deathSnapshot.killerUuid())
                .replace("{killer_display_name}", deathSnapshot.killerDisplayName())
                .replace("{killer_avatar_url}", deathSnapshot.killerAvatarUrl())
                .replace("{is_pvp_death}", String.valueOf(deathSnapshot.pvp()));
    }

    private static String applyLifecycle(String value, LifecycleSnapshot lifecycleSnapshot) {
        if (lifecycleSnapshot == null) {
            return value.replace("{lifecycle_type}", "")
                    .replace("{lifecycle_label}", "")
                    .replace("{lifecycle_description}", "")
                    .replace("{lifecycle_color}", "");
        }
        return value.replace("{lifecycle_type}", lifecycleSnapshot.type())
                .replace("{lifecycle_label}", lifecycleSnapshot.label())
                .replace("{lifecycle_description}", lifecycleSnapshot.description())
                .replace("{lifecycle_color}", lifecycleSnapshot.color());
    }

    private static String applyBroadcast(String value, BroadcastSnapshot broadcastSnapshot) {
        if (broadcastSnapshot == null) {
            return value.replace("{broadcast_type}", "")
                    .replace("{broadcast_label}", "")
                    .replace("{broadcast_message}", "")
                    .replace("{broadcast_player}", "")
                    .replace("{broadcast_player_avatar_url}", "");
        }
        return value.replace("{broadcast_type}", broadcastSnapshot.type())
                .replace("{broadcast_label}", broadcastSnapshot.label())
                .replace("{broadcast_message}", broadcastSnapshot.message())
                .replace("{broadcast_player}", broadcastSnapshot.playerName())
                .replace("{broadcast_player_avatar_url}", broadcastSnapshot.playerAvatarUrl());
    }

    public record EventContext(String eventType, String eventLabel, String eventColor,
                               AdvancementSnapshot advancementSnapshot, DeathSnapshot deathSnapshot,
                               LifecycleSnapshot lifecycleSnapshot, BroadcastSnapshot broadcastSnapshot) {
        public static EventContext empty() {
            return new EventContext("", "", "", null, null, null, null);
        }

        public static EventContext basic(String eventType, String eventLabel, String eventColor) {
            return new EventContext(eventType, eventLabel, eventColor, null, null, null, null);
        }

        public static EventContext advancement(AdvancementSnapshot advancementSnapshot) {
            return new EventContext("advancement",
                    advancementSnapshot == null ? "" : advancementSnapshot.typeLabel(),
                    advancementSnapshot == null ? "" : advancementSnapshot.color(),
                    advancementSnapshot, null, null, null);
        }

        public static EventContext death(DeathSnapshot deathSnapshot) {
            return new EventContext("death", "Player Death", "#ED4245", null, deathSnapshot, null, null);
        }

        public static EventContext lifecycle(LifecycleSnapshot lifecycleSnapshot) {
            return new EventContext("lifecycle",
                    lifecycleSnapshot == null ? "" : lifecycleSnapshot.label(),
                    lifecycleSnapshot == null ? "" : lifecycleSnapshot.color(),
                    null, null, lifecycleSnapshot, null);
        }

        public static EventContext broadcast(BroadcastSnapshot broadcastSnapshot) {
            return new EventContext("broadcast",
                    broadcastSnapshot == null ? "" : broadcastSnapshot.label(),
                    "#FEE75C",
                    null, null, null, broadcastSnapshot);
        }
    }

    public record AdvancementSnapshot(String key, String namespace, String path, String title, String description,
                                      String type, String typeLabel, String color, boolean hidden,
                                      boolean announcesToChat) {
    }

    public record DeathSnapshot(String deathMessage, String deathCause, String killer, String killerUuid,
                                String killerDisplayName, String killerAvatarUrl, boolean pvp) {
    }

    public record LifecycleSnapshot(String type, String label, String description, String color) {
    }

    public record BroadcastSnapshot(String type, String label, String message, String playerName,
                                    String playerAvatarUrl) {
    }

    public record OnlineSnapshot(int online, String players) {
        public static OnlineSnapshot current() {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            return new OnlineSnapshot(onlinePlayers.size(), onlinePlayers(onlinePlayers, null));
        }

        public static OnlineSnapshot excluding(Player excludedPlayer) {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            int count = onlinePlayers.size();
            if (excludedPlayer != null && onlinePlayers.stream()
                    .anyMatch(player -> player.getUniqueId().equals(excludedPlayer.getUniqueId()))) {
                count = Math.max(0, count - 1);
            }
            return new OnlineSnapshot(count, onlinePlayers(onlinePlayers, excludedPlayer));
        }
    }

    private static String onlinePlayers(Collection<? extends Player> onlinePlayers, Player excludedPlayer) {
        if (onlinePlayers.isEmpty()) {
            return "None";
        }
        String players = onlinePlayers.stream()
                .filter(player -> excludedPlayer == null || !player.getUniqueId().equals(excludedPlayer.getUniqueId()))
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        return players.isBlank() ? "None" : players;
    }
}
