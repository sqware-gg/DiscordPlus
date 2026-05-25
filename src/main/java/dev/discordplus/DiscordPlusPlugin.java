package dev.discordplus;

import dev.discordplus.chat.ChatBridgeListener;
import dev.discordplus.command.DiscordCommand;
import dev.discordplus.config.ConfigReferenceWriter;
import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.discord.DiscordBotService;
import dev.discordplus.link.LinkManager;
import dev.discordplus.roles.RoleSyncService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordPlusPlugin extends JavaPlugin {
    private DiscordPlusConfig discordConfig;
    private LinkManager linkManager;
    private DiscordBotService botService;
    private RoleSyncService roleSyncService;

    @Override
    public void onEnable() {
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);

        discordConfig = new DiscordPlusConfig(this);
        discordConfig.logWarnings();
        linkManager = new LinkManager(this);
        botService = new DiscordBotService(this, discordConfig, linkManager);
        roleSyncService = new RoleSyncService(this, discordConfig, botService, linkManager);
        botService.setRoleSyncService(roleSyncService);

        boolean modernChat = registerPaperChatListener();
        getServer().getPluginManager().registerEvents(new ChatBridgeListener(this, discordConfig, botService, roleSyncService, !modernChat), this);
        DiscordCommand command = new DiscordCommand(this, discordConfig, linkManager, botService, roleSyncService);
        PluginCommand pluginCommand = getCommand("discord");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        botService.start();
        roleSyncService.start();
    }

    private boolean registerPaperChatListener() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Class<?> listenerClass = Class.forName("dev.discordplus.chat.PaperChatBridgeListener");
            Listener listener = (Listener) listenerClass
                    .getConstructor(DiscordPlusConfig.class, DiscordBotService.class)
                    .newInstance(discordConfig, botService);
            getServer().getPluginManager().registerEvents(listener, this);
            getLogger().info("Using Paper modern chat event.");
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (roleSyncService != null) {
            roleSyncService.stop();
        }
        if (botService != null) {
            botService.sendServerStop();
            botService.stop();
        }
        if (linkManager != null) {
            linkManager.save();
        }
    }

    public void reloadPlugin() {
        roleSyncService.stop();
        botService.stop();
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);
        discordConfig.reload();
        discordConfig.logWarnings();
        linkManager.reload();
        botService.startForReload();
        roleSyncService.start();
    }
}
