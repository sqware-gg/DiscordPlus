package dev.discordplus.discord;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import dev.discordplus.link.LinkManager.PendingLink;
import dev.discordplus.roles.RoleSyncService;
import dev.discordplus.util.TextSanitizer;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordMessageListener extends ListenerAdapter {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;
    private final LinkManager linkManager;
    private final RoleSyncService roleSyncService;

    public DiscordMessageListener(JavaPlugin plugin, DiscordPlusConfig config, DiscordBotService botService,
                                  LinkManager linkManager, RoleSyncService roleSyncService) {
        this.plugin = plugin;
        this.config = config;
        this.botService = botService;
        this.linkManager = linkManager;
        this.roleSyncService = roleSyncService;
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
        botService.onReady();
        if (roleSyncService != null) {
            roleSyncService.logRoleDiagnostics(botService.guild());
        }
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
                player.sendMessage(TextSanitizer.color(config.messagePrefix() + "&7Discord account linked."));
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

    private String displayName(Member member, String fallback) {
        if (member == null || member.getEffectiveName().isBlank()) {
            return fallback;
        }
        return member.getEffectiveName();
    }
}
