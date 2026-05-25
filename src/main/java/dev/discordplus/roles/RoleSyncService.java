package dev.discordplus.roles;

import dev.discordplus.config.DiscordPlusConfig;
import dev.discordplus.config.DiscordPlusConfig.RoleMapping;
import dev.discordplus.discord.DiscordBotService;
import dev.discordplus.link.LinkedAccount;
import dev.discordplus.link.LinkManager;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RoleSyncService {
    private final JavaPlugin plugin;
    private final DiscordPlusConfig config;
    private final DiscordBotService botService;
    private final LinkManager linkManager;
    private BukkitTask task;
    private BukkitTask startupTask;

    public RoleSyncService(JavaPlugin plugin, DiscordPlusConfig config, DiscordBotService botService, LinkManager linkManager) {
        this.plugin = plugin;
        this.config = config;
        this.botService = botService;
        this.linkManager = linkManager;
    }

    public void start() {
        if (!config.roleSyncEnabled() && config.linkedRoleId().isBlank()) {
            return;
        }
        if (config.roleSyncEnabled()) {
            long intervalTicks = Math.max(1L, config.roleSyncIntervalMinutes()) * 60L * 20L;
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::syncOnlinePlayers, intervalTicks, intervalTicks);
        }
        startupTask = Bukkit.getScheduler().runTaskLater(plugin, this::syncOnlinePlayers, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (startupTask != null) {
            startupTask.cancel();
            startupTask = null;
        }
    }

    public void syncOnlinePlayers() {
        if (!config.roleSyncEnabled() && config.linkedRoleId().isBlank()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player);
        }
    }

    public SyncResult sync(Player player) {
        if (!config.roleSyncEnabled() && config.linkedRoleId().isBlank()) {
            return SyncResult.DISABLED;
        }
        Optional<LinkedAccount> account = linkManager.findByPlayer(player.getUniqueId());
        if (account.isEmpty()) {
            return SyncResult.NOT_LINKED;
        }

        Guild guild = botService.guild();
        if (guild == null) {
            return SyncResult.NO_GUILD;
        }

        Set<String> desiredRoleIds = desiredRoleIds(player);
        Set<String> managedRoleIds = managedRoleIds();
        if (managedRoleIds.isEmpty()) {
            return SyncResult.NO_ROLES_CONFIGURED;
        }

        guild.retrieveMemberById(account.get().discordId()).queue(member -> applyRoles(guild, member, desiredRoleIds, managedRoleIds),
                error -> plugin.getLogger().fine("Could not retrieve Discord member for " + player.getName() + ": " + error.getMessage()));
        return SyncResult.QUEUED;
    }

    public void clearManagedRoles(LinkedAccount account) {
        Guild guild = botService.guild();
        Set<String> managedRoleIds = managedRoleIds();
        if (guild == null || managedRoleIds.isEmpty()) {
            return;
        }

        guild.retrieveMemberById(account.discordId()).queue(member -> {
            Set<String> currentRoleIds = new HashSet<>();
            for (Role role : member.getRoles()) {
                currentRoleIds.add(role.getId());
            }
            for (String roleId : managedRoleIds) {
                if (!currentRoleIds.contains(roleId)) {
                    continue;
                }
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    guild.removeRoleFromMember(member, role).queue(null,
                            error -> plugin.getLogger().fine("Could not remove Discord role " + roleId + ": " + error.getMessage()));
                }
            }
        }, error -> plugin.getLogger().fine("Could not clear Discord roles for " + account.discordId() + ": " + error.getMessage()));
    }

    public void syncLinkedOnly(LinkedAccount account) {
        Guild guild = botService.guild();
        if (guild == null || config.linkedRoleId().isBlank()) {
            return;
        }

        guild.retrieveMemberById(account.discordId()).queue(member -> {
            Role role = guild.getRoleById(config.linkedRoleId());
            if (role != null && !member.getRoles().contains(role)) {
                guild.addRoleToMember(member, role).queue(null,
                        error -> plugin.getLogger().fine("Could not add linked Discord role: " + error.getMessage()));
            }
        }, error -> plugin.getLogger().fine("Could not sync linked role for " + account.discordId() + ": " + error.getMessage()));
    }

    private Set<String> desiredRoleIds(Player player) {
        Set<String> desired = new HashSet<>();
        if (!config.linkedRoleId().isBlank()) {
            desired.add(config.linkedRoleId());
        }
        if (config.roleSyncEnabled()) {
            for (RoleMapping mapping : config.roleMappings()) {
                if (player.hasPermission(mapping.permission())) {
                    desired.add(mapping.roleId());
                }
            }
        }
        return desired;
    }

    private Set<String> managedRoleIds() {
        Set<String> managed = new HashSet<>();
        if (!config.linkedRoleId().isBlank()) {
            managed.add(config.linkedRoleId());
        }
        if (config.roleSyncEnabled()) {
            for (RoleMapping mapping : config.roleMappings()) {
                managed.add(mapping.roleId());
            }
        }
        managed.remove("");
        return managed;
    }

    public void logRoleDiagnostics(Guild guild) {
        Set<String> roleIds = managedRoleIds();
        if (guild == null || roleIds.isEmpty()) {
            return;
        }

        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            plugin.getLogger().warning("Discord bot is missing Manage Roles permission.");
        }

        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                plugin.getLogger().warning("Configured Discord role was not found: " + roleId);
                continue;
            }
            if (!self.canInteract(role)) {
                plugin.getLogger().warning("Bot role must be higher than managed Discord role: " + role.getName());
            }
        }
    }

    private void applyRoles(Guild guild, Member member, Set<String> desiredRoleIds, Set<String> managedRoleIds) {
        Set<String> currentRoleIds = new HashSet<>();
        for (Role role : member.getRoles()) {
            currentRoleIds.add(role.getId());
        }

        for (String roleId : desiredRoleIds) {
            if (currentRoleIds.contains(roleId)) {
                continue;
            }
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                guild.addRoleToMember(member, role).queue(null,
                        error -> plugin.getLogger().fine("Could not add Discord role " + roleId + ": " + error.getMessage()));
            }
        }

        if (!config.removeUnmatchedMappedRoles()) {
            return;
        }

        for (String roleId : managedRoleIds) {
            if (desiredRoleIds.contains(roleId) || !currentRoleIds.contains(roleId)) {
                continue;
            }
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                guild.removeRoleFromMember(member, role).queue(null,
                        error -> plugin.getLogger().fine("Could not remove Discord role " + roleId + ": " + error.getMessage()));
            }
        }
    }

    public enum SyncResult {
        QUEUED,
        DISABLED,
        NOT_LINKED,
        NO_GUILD,
        NO_ROLES_CONFIGURED
    }
}
