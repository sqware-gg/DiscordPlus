package dev.discordplus.command;

import dev.discordplus.DiscordPlusPlugin;
import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import dev.discordplus.roles.RoleSyncService;
import dev.discordplus.roles.RoleSyncService.SyncResult;
import dev.discordplus.util.TextSanitizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class DiscordCommand implements CommandExecutor, TabCompleter {
    private static final List<String> TEST_TYPES = List.of("chat", "join", "first-join", "quit",
            "advancement", "death", "status", "server-start", "server-stop", "reload");

    private final DiscordPlusPlugin plugin;
    private final DiscordPlusConfig config;
    private final LinkManager linkManager;
    private final DiscordBotService botService;
    private final RoleSyncService roleSyncService;

    public DiscordCommand(DiscordPlusPlugin plugin, DiscordPlusConfig config, LinkManager linkManager,
                          DiscordBotService botService, RoleSyncService roleSyncService) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
        this.botService = botService;
        this.roleSyncService = roleSyncService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("discordplus.command")) {
            message(sender, "&cNo permission.");
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "invite" -> invite(sender);
            case "link" -> link(sender);
            case "unlink" -> unlink(sender);
            case "status" -> status(sender);
            case "sync" -> sync(sender, args);
            case "test" -> test(sender, args);
            case "broadcast" -> broadcast(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    private void link(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cOnly players can link accounts.");
            return;
        }
        if (!config.linkingEnabled()) {
            message(sender, "&cDiscord linking is disabled.");
            return;
        }
        if (linkManager.findByPlayer(player.getUniqueId()).isPresent()) {
            message(sender, "&7You are already linked. Use &f/discord unlink &7first.");
            return;
        }
        if (!botService.isReady()) {
            message(sender, "&cDiscord is not connected yet. Try again shortly.");
            return;
        }

        LinkManager.PendingLink link = linkManager.createCode(player, config.codeExpiryMinutes());
        message(player, "&7Your link code is &f" + link.code() + "&7.");
        message(player, "&7In Discord, DM the bot or type &f" + config.commandPrefix() + "link " + link.code() + "&7.");
        message(player, "&7This expires in &f" + config.codeExpiryMinutes() + " minutes&7.");
    }

    private void invite(CommandSender sender) {
        if (config.inviteUrl().isBlank()) {
            message(sender, "&7No Discord invite is configured.");
            return;
        }
        message(sender, "&7Discord: &f" + config.inviteUrl());
    }

    private void unlink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cOnly players can unlink accounts.");
            return;
        }
        Optional<LinkedAccount> removed = linkManager.unlink(player.getUniqueId());
        if (removed.isEmpty()) {
            message(player, "&7You are not linked.");
            return;
        }
        roleSyncService.clearManagedRoles(removed.get());
        message(player, "&7Your Discord account was unlinked.");
    }

    private void status(CommandSender sender) {
        if (!(sender instanceof Player) || sender.hasPermission("discordplus.admin")) {
            message(sender, "&7Bot: &f" + (botService.isReady() ? "connected" : "not connected"));
            message(sender, "&7Guild: &f" + (botService.guild() != null ? "found" : "not found"));
            message(sender, "&7Chat channel: &f" + (botService.chatChannel() != null ? "found" : "not found"));
            message(sender, "&7Event channel: &f" + (botService.eventChannel() != null ? "found" : "not found"));
            message(sender, "&7Staff channel: &f" + (botService.staffChannel() != null ? "found" : "not found"));
            message(sender, "&7Webhook chat: &f" + (config.minecraftChatStyle().useWebhook() ? "enabled" : "disabled"));
            message(sender, "&7Discord commands: &f" + (config.discordCommands() ? "enabled" : "disabled"));
            message(sender, "&7Death messages: &f" + (config.deathMessages() ? "enabled" : "disabled"));
            message(sender, "&7Advancement messages: &f" + (config.advancementMessages() ? "enabled" : "disabled"));
            message(sender, "&7Lifecycle messages: &f" + (config.lifecycleMessages() ? "enabled" : "disabled"));
            message(sender, "&7Role sync: &f" + (config.roleSyncEnabled() ? "enabled" : "disabled"));
            message(sender, "&7Linked role: &f" + (!config.linkedRoleId().isBlank() ? "set" : "not set"));
            message(sender, "&7Role mappings: &f" + config.roleMappings().size());
            message(sender, "&7Linked accounts: &f" + linkManager.linkCount());
            message(sender, "&7Pending link codes: &f" + linkManager.pendingCount());
            if (!(sender instanceof Player)) {
                return;
            }
        }

        if (!(sender instanceof Player player)) {
            return;
        }

        Optional<LinkedAccount> account = linkManager.findByPlayer(player.getUniqueId());
        if (account.isEmpty()) {
            message(player, "&7You are not linked.");
            return;
        }
        message(player, "&7Linked to &f" + account.get().discordTag() + "&7.");
    }

    private void sync(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("discordplus.admin")) {
                message(sender, "&cNo permission.");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                message(sender, "&cPlayer is not online.");
                return;
            }
            sendSyncResult(sender, roleSyncService.sync(target), target.getName());
            return;
        }

        if (!(sender instanceof Player player)) {
            message(sender, "&cUsage: /discord sync <player>");
            return;
        }
        sendSyncResult(player, roleSyncService.sync(player), player.getName());
    }

    private void test(CommandSender sender, String[] args) {
        if (!sender.hasPermission("discordplus.admin")) {
            message(sender, "&cNo permission.");
            return;
        }
        String type = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "chat";

        Boolean sent;
        switch (type) {
            case "chat" -> sent = botService.sendChatMessage("DiscordPlus chat test from "
                    + TextSanitizer.safeDiscord(sender.getName()) + ".");
            case "join" -> sent = sendPlayerTest(sender, botService::sendTestJoin);
            case "first-join", "firstjoin" -> sent = sendPlayerTest(sender, botService::sendTestFirstJoin);
            case "quit", "leave" -> sent = sendPlayerTest(sender, botService::sendTestQuit);
            case "advancement", "advancements" -> sent = sendPlayerTest(sender, botService::sendTestAdvancement);
            case "death", "deaths" -> sent = sendPlayerTest(sender, botService::sendTestDeath);
            case "status" -> sent = botService.sendStatus(botService.eventChannel());
            case "server-start", "start", "online" -> sent = botService.sendTestServerStart();
            case "server-stop", "stop", "offline" -> sent = botService.sendTestServerStop();
            case "reload" -> sent = botService.sendTestReload();
            default -> {
                message(sender, "&cUnknown test type. Use: &f" + String.join("&7, &f", TEST_TYPES));
                return;
            }
        }

        if (sent == null) {
            return;
        }
        message(sender, sent ? "&7Sent Discord test: &f" + type + "&7." : "&cDiscord channel is not available for that test.");
    }

    private void broadcast(CommandSender sender, String[] args) {
        if (!sender.hasPermission("discordplus.admin")) {
            message(sender, "&cNo permission.");
            return;
        }
        if (args.length < 4) {
            message(sender, "&cUsage: /discord broadcast <style> <player|-> <message>");
            message(sender, "&7Styles: &f" + String.join("&7, &f", config.broadcastStyleKeys()));
            return;
        }

        String style = args[1].toLowerCase(Locale.ROOT);
        if (config.broadcastStyle(style) == null) {
            message(sender, "&cUnknown broadcast style. Use: &f" + String.join("&7, &f", config.broadcastStyleKeys()));
            return;
        }

        String playerName = args[2];
        String broadcastMessage = joinArgs(args, 3);
        boolean sent = botService.sendBroadcast(style, playerName, broadcastMessage);
        message(sender, sent ? "&7Sent Discord broadcast: &f" + style + "&7." : "&cDiscord event channel is not available.");
    }

    private Boolean sendPlayerTest(CommandSender sender, PlayerTest test) {
        Player player = testPlayer(sender);
        if (player == null) {
            message(sender, "&cPlayer-style test messages need at least one online player.");
            return null;
        }
        return test.send(player);
    }

    private Player testPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("discordplus.admin")) {
            message(sender, "&cNo permission.");
            return;
        }
        plugin.reloadPlugin();
        message(sender, "&7DiscordPlus reloaded.");
    }

    private void help(CommandSender sender) {
        message(sender, "&7Commands: &f/discord invite&7, &f/discord link&7, &f/discord unlink&7, &f/discord status&7, &f/discord sync");
        if (sender.hasPermission("discordplus.admin")) {
            message(sender, "&7Admin: &f/discord sync <player>&7, &f/discord test [type]&7, &f/discord broadcast <style> <player|-> <message>&7, &f/discord reload");
        }
    }

    private void sendSyncResult(CommandSender sender, SyncResult result, String playerName) {
        switch (result) {
            case QUEUED -> message(sender, "&7Queued role sync for &f" + playerName + "&7.");
            case DISABLED -> message(sender, "&cRole sync is disabled and no linked role is configured.");
            case NOT_LINKED -> message(sender, "&c" + playerName + " is not linked.");
            case NO_GUILD -> message(sender, "&cDiscord guild is not available.");
            case NO_ROLES_CONFIGURED -> message(sender, "&cNo Discord roles are configured.");
        }
    }

    private void message(CommandSender sender, String message) {
        sender.sendMessage(TextSanitizer.color(config.messagePrefix() + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("invite", "link", "unlink", "status", "sync"));
            if (sender.hasPermission("discordplus.admin")) {
                options.add("reload");
                options.add("test");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sync") && sender.hasPermission("discordplus.admin")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test") && sender.hasPermission("discordplus.admin")) {
            return filter(TEST_TYPES, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("broadcast") && sender.hasPermission("discordplus.admin")) {
            return filter(new ArrayList<>(config.broadcastStyleKeys()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("broadcast") && sender.hasPermission("discordplus.admin")) {
            List<String> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            players.add("-");
            return filter(players, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    @FunctionalInterface
    private interface PlayerTest {
        boolean send(Player player);
    }
}
