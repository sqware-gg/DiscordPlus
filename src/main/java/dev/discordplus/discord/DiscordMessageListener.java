package dev.discordplus.discord;

import dev.discordplus.auctions.AuctionsPlusDiscordCommands;
import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import dev.discordplus.link.LinkManager.PendingLink;
import dev.discordplus.orders.OrdersPlusDiscordCommands;
import dev.discordplus.playtime.PlaytimePlusDiscordCommands;
import dev.discordplus.points.PointsPlusDiscordCommands;
import dev.discordplus.roles.RoleSyncService;
import dev.discordplus.util.TextSanitizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordMessageListener extends ListenerAdapter {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;
    private final LinkManager linkManager;
    private final RoleSyncService roleSyncService;
    private final AuctionsPlusDiscordCommands auctionsPlusCommands;
    private final OrdersPlusDiscordCommands ordersPlusCommands;
    private final PointsPlusDiscordCommands pointsPlusCommands;
    private final PlaytimePlusDiscordCommands playtimePlusCommands;

    public DiscordMessageListener(JavaPlugin plugin, DiscordPlusConfig config, DiscordBotService botService,
                                  LinkManager linkManager, RoleSyncService roleSyncService) {
        this.plugin = plugin;
        this.config = config;
        this.botService = botService;
        this.linkManager = linkManager;
        this.roleSyncService = roleSyncService;
        this.auctionsPlusCommands = new AuctionsPlusDiscordCommands(plugin, config, linkManager);
        this.ordersPlusCommands = new OrdersPlusDiscordCommands(plugin, config);
        this.pointsPlusCommands = new PointsPlusDiscordCommands(plugin, config, linkManager);
        this.playtimePlusCommands = new PlaytimePlusDiscordCommands(plugin, config, linkManager);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        if (event.isFromGuild() && !botService.isConfiguredGuild(event.getGuild().getId())) {
            return;
        }

        String raw = event.getMessage().getContentRaw().trim();
        if (raw.isBlank()) {
            return;
        }
        if (config.linkingEnabled() && raw.startsWith(config.commandPrefix() + "link ")) {
            handleLink(event, raw.substring((config.commandPrefix() + "link ").length()).trim());
            return;
        }
        if (config.linkingEnabled() && raw.equalsIgnoreCase(config.commandPrefix() + "unlink")) {
            handleUnlink(event);
            return;
        }
        if (config.discordCommands() && raw.equalsIgnoreCase(config.commandPrefix() + "online")) {
            botService.sendStatus(event.getChannel());
            return;
        }
        if (config.discordCommands() && raw.equalsIgnoreCase(config.commandPrefix() + "players")) {
            event.getChannel().sendMessage(TextSanitizer.truncate(botService.onlinePlayersList(), 1900)).queue();
            return;
        }
        if (config.discordCommands() && pointsPlusCommands.handle(event, raw)) {
            return;
        }
        if (config.discordCommands() && playtimePlusCommands.handle(event, raw)) {
            return;
        }
        if (config.discordCommands() && auctionsPlusCommands.handle(event, raw)) {
            return;
        }
        if (config.discordCommands() && ordersPlusCommands.handle(event, raw)) {
            return;
        }

        if (!event.isFromGuild()) {
            return;
        }

        if (!config.discordToMinecraft()) {
            return;
        }
        if (config.chatChannelId().isBlank() || !event.getChannel().getId().equals(config.chatChannelId())) {
            return;
        }

        String message = discordMessageText(event);
        if (message.isBlank()) {
            return;
        }

        String user = displayName(event.getMember(), event.getAuthor().getName());
        String formatted = TextSanitizer.color(config.discordToMinecraftFormat())
                .replace("{user}", user)
                .replace("{message}", message);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(formatted));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.isFromGuild() && !botService.isConfiguredGuild(event.getGuild().getId())) {
            event.reply("DiscordPlus is not configured for this Discord server.").setEphemeral(true).queue();
            return;
        }
        if (!config.slashCommandsEnabled()) {
            event.reply("DiscordPlus slash commands are disabled.").setEphemeral(true).queue();
            return;
        }

        if ("points".equals(event.getName())) {
            pointsPlusCommands.handleSlash(event);
            return;
        }
        if ("playtime".equals(event.getName())) {
            playtimePlusCommands.handleSlash(event);
            return;
        }
        if ("auction".equals(event.getName())) {
            auctionsPlusCommands.handleSlash(event);
            return;
        }
        if ("orders".equals(event.getName())) {
            ordersPlusCommands.handleSlash(event);
            return;
        }
        if (!"discord".equals(event.getName())) {
            return;
        }

        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "link" -> handleSlashLink(event);
            case "unlink" -> handleSlashUnlink(event);
            case "online" -> event.reply(serverStatusText()).setEphemeral(true).queue();
            case "players" -> event.reply(TextSanitizer.truncate(botService.onlinePlayersList(), 1900)).setEphemeral(true).queue();
            default -> event.reply("Unknown DiscordPlus command.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        plugin.getLogger().info("Discord bot connected as " + event.getJDA().getSelfUser().getName() + ".");
        if (botService.guild() == null) {
            plugin.getLogger().warning("Discord guild was not found. Set bot.guild-id in config.yml.");
        }
        if ((config.minecraftToDiscord() || config.discordToMinecraft()) && botService.chatChannel() == null) {
            plugin.getLogger().warning("Discord chat channel was not found. Set channels.chat-channel-id in config.yml.");
        }
        if ((config.joinLeave() || config.firstJoinMessages() || config.advancementMessages()
                || config.deathMessages() || config.lifecycleMessages()) && botService.eventChannel() == null) {
            plugin.getLogger().warning("Discord event channel was not found. Set channels.events-channel-id or channels.chat-channel-id in config.yml.");
        }
        refreshSlashCommands(event);
        botService.onReady();
        if (roleSyncService != null) {
            roleSyncService.logRoleDiagnostics(botService.guild());
        }
    }

    private void refreshSlashCommands(ReadyEvent event) {
        if (!config.slashCommandsEnabled() || !config.slashCommandsRefreshOnStart()) {
            return;
        }

        List<CommandData> commands = slashCommands();
        Guild guild = botService.guild();
        if (guild != null) {
            guild.updateCommands().addCommands(commands).queue(
                    updated -> plugin.getLogger().info("Refreshed " + updated.size() + " Discord guild slash commands."),
                    error -> plugin.getLogger().warning("Could not refresh Discord guild slash commands: " + error.getMessage()));
            return;
        }

        event.getJDA().updateCommands().addCommands(commands).queue(
                updated -> plugin.getLogger().info("Refreshed " + updated.size() + " global Discord slash commands."),
                error -> plugin.getLogger().warning("Could not refresh global Discord slash commands: " + error.getMessage()));
    }

    private List<CommandData> slashCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("discord", "DiscordPlus account and server commands")
                .addSubcommands(
                        new SubcommandData("link", "Link your Discord account to Minecraft")
                                .addOption(OptionType.STRING, "code", "Link code from /discord link in Minecraft", true),
                        new SubcommandData("unlink", "Unlink your Discord account"),
                        new SubcommandData("online", "Show Minecraft server status"),
                        new SubcommandData("players", "List online Minecraft players")
                ));

        if (config.pointsPlusCommandsEnabled()) {
            DiscordPlusConfig.PointsPlusCommandSettings pointsSettings = config.pointsPlusCommandSettings();
            OptionData amount = new OptionData(OptionType.INTEGER, "amount", "Points to send", true).setMinValue(1);
            if (pointsSettings.maxPayment() > 0L) {
                amount.setMaxValue(pointsSettings.maxPayment());
            }
            commands.add(Commands.slash("points", "PointsPlus account commands")
                    .addSubcommands(
                            new SubcommandData("balance", "Show your linked PointsPlus balance"),
                            new SubcommandData("pay", "Send points to a linked Discord user")
                                    .addOptions(
                                            new OptionData(OptionType.USER, "user", "Linked Discord user to pay", true),
                                            amount
                                    ),
                            new SubcommandData("top", "Show the PointsPlus leaderboard")
                                    .addOptions(new OptionData(OptionType.INTEGER, "limit", "Rows to show", false)
                                            .setRequiredRange(1, pointsSettings.topLimit()))
                    ));
        }
        if (config.playtimePlusCommandsEnabled()) {
            DiscordPlusConfig.PlaytimePlusCommandSettings playtimeSettings = config.playtimePlusCommandSettings();
            OptionData metric = new OptionData(OptionType.STRING, "metric", "Playtime metric", false)
                    .addChoice("active", "active")
                    .addChoice("total", "total")
                    .addChoice("afk", "afk");
            commands.add(Commands.slash("playtime", "PlaytimePlus stats and leaderboards")
                    .addSubcommands(
                            new SubcommandData("me", "Show your linked PlaytimePlus stats"),
                            new SubcommandData("player", "Show a player's PlaytimePlus stats")
                                    .addOption(OptionType.STRING, "name", "Minecraft player name", true),
                            new SubcommandData("top", "Show the PlaytimePlus leaderboard")
                                    .addOptions(
                                            metric,
                                            new OptionData(OptionType.INTEGER, "limit", "Rows to show", false)
                                                    .setRequiredRange(1, playtimeSettings.topLimit())
                    )
                    ));
        }
        if (config.auctionsPlusCommandsEnabled()) {
            DiscordPlusConfig.AuctionsPlusCommandSettings auctionSettings = config.auctionsPlusCommandSettings();
            commands.add(Commands.slash("auction", "Auctions+ linked account commands")
                    .addSubcommands(
                            new SubcommandData("list", "List active Auctions+ bid auctions")
                                    .addOptions(new OptionData(OptionType.INTEGER, "limit", "Rows to show", false)
                                            .setRequiredRange(1, auctionSettings.listLimit())),
                            new SubcommandData("bid", "Bid on an Auctions+ timed auction")
                                    .addOptions(
                                            new OptionData(OptionType.INTEGER, "id", "Auction listing ID", true)
                                                    .setMinValue(1),
                                            new OptionData(OptionType.STRING, "amount", "Bid amount, such as 500 or 1.5k", true)
                                    )
                    ));
        }
        if (config.ordersPlusCommandsEnabled()) {
            DiscordPlusConfig.OrdersPlusCommandSettings orderSettings = config.ordersPlusCommandSettings();
            commands.add(Commands.slash("orders", "Orders+ market commands")
                    .addSubcommands(
                            new SubcommandData("list", "List active Orders+ buy orders")
                                    .addOptions(
                                            new OptionData(OptionType.STRING, "search", "Item, buyer, or order ID", false),
                                            new OptionData(OptionType.INTEGER, "limit", "Rows to show", false)
                                                    .setRequiredRange(1, orderSettings.listLimit())
                                    )
                    ));
        }
        return commands;
    }

    private String discordMessageText(MessageReceivedEvent event) {
        StringBuilder builder = new StringBuilder(event.getMessage().getContentDisplay());
        for (var attachment : event.getMessage().getAttachments()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(attachment.getUrl());
        }
        return TextSanitizer.safeMinecraft(builder.toString());
    }

    private void handleLink(MessageReceivedEvent event, String code) {
        Optional<PendingLink> pending = linkManager.consumeCode(code);
        if (pending.isEmpty()) {
            event.getMessage().reply("That link code is invalid or expired. Run `/discord link` in-game.").queue();
            return;
        }

        String tag = event.getAuthor().getEffectiveName();
        LinkedAccount account = linkManager.link(pending.get(), event.getAuthor().getId(), tag);
        event.getMessage().reply("Linked Discord to Minecraft player `" + pending.get().playerName() + "`.").queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(pending.get().playerUuid());
            if (player != null) {
                player.sendMessage(TextSanitizer.color(config.messagePrefix() + "&#57F287Discord account linked."));
                if (roleSyncService != null) {
                    roleSyncService.sync(player);
                }
            } else if (roleSyncService != null) {
                roleSyncService.syncLinkedOnly(account);
            }
        });
    }

    private void handleSlashLink(SlashCommandInteractionEvent event) {
        if (!config.linkingEnabled()) {
            event.reply("Discord account linking is disabled.").setEphemeral(true).queue();
            return;
        }

        String code = event.getOption("code", "", OptionMapping::getAsString).trim();
        Optional<PendingLink> pending = linkManager.consumeCode(code);
        if (pending.isEmpty()) {
            event.reply("That link code is invalid or expired. Run `/discord link` in-game.").setEphemeral(true).queue();
            return;
        }

        String tag = event.getUser().getEffectiveName();
        LinkedAccount account = linkManager.link(pending.get(), event.getUser().getId(), tag);
        event.reply("Linked Discord to Minecraft player `" + pending.get().playerName() + "`.").setEphemeral(true).queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(pending.get().playerUuid());
            if (player != null) {
                player.sendMessage(TextSanitizer.color(config.messagePrefix() + "&#57F287Discord account linked."));
                if (roleSyncService != null) {
                    roleSyncService.sync(player);
                }
            } else if (roleSyncService != null) {
                roleSyncService.syncLinkedOnly(account);
            }
        });
    }

    private void handleUnlink(MessageReceivedEvent event) {
        Optional<LinkedAccount> account = linkManager.unlinkByDiscordId(event.getAuthor().getId());
        if (account.isEmpty()) {
            event.getMessage().reply("Your Discord account is not linked.").queue();
            return;
        }

        if (roleSyncService != null) {
            roleSyncService.clearManagedRoles(account.get());
        }
        event.getMessage().reply("Your Discord account has been unlinked from `" + account.get().playerName() + "`.").queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(account.get().playerUuid());
            if (player != null) {
                player.sendMessage(TextSanitizer.color(config.messagePrefix() + "&7Discord account unlinked."));
            }
        });
    }

    private void handleSlashUnlink(SlashCommandInteractionEvent event) {
        if (!config.linkingEnabled()) {
            event.reply("Discord account linking is disabled.").setEphemeral(true).queue();
            return;
        }

        Optional<LinkedAccount> account = linkManager.unlinkByDiscordId(event.getUser().getId());
        if (account.isEmpty()) {
            event.reply("Your Discord account is not linked.").setEphemeral(true).queue();
            return;
        }

        if (roleSyncService != null) {
            roleSyncService.clearManagedRoles(account.get());
        }
        event.reply("Your Discord account has been unlinked from `" + account.get().playerName() + "`.").setEphemeral(true).queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(account.get().playerUuid());
            if (player != null) {
                player.sendMessage(TextSanitizer.color(config.messagePrefix() + "&7Discord account unlinked."));
            }
        });
    }

    private String serverStatusText() {
        String server = config.serverName().isBlank() ? plugin.getServer().getName() : config.serverName();
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        return "**" + TextSanitizer.safeDiscord(server) + "**: " + online + "/" + maxPlayers + " online";
    }

    private String displayName(Member member, String fallback) {
        if (member == null || member.getEffectiveName().isBlank()) {
            return fallback;
        }
        return member.getEffectiveName();
    }
}
