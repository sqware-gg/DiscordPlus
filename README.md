# DiscordPlus

[![Build](https://github.com/sqware-gg/DiscordPlus/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/DiscordPlus/actions/workflows/build.yml)

DiscordPlus connects a Paper Minecraft server to a Discord community. It relays chat, sends styled server events, supports account linking, exposes lightweight Discord commands, and can sync Minecraft permission groups to Discord roles.

It is built for Minecraft server owners who want a polished Discord bridge without giving up control of formatting, channels, or role behavior.

## Links

- Website: https://sqware.gg
- Plugin information and support: https://discord.sqware.gg

## Compatibility

- Server software: Paper
- API target: Paper `1.18.2`
- Java: `17+`
- Discord library: JDA `6.4.1`
- Build tool: Maven

As of May 2026, Paper is the supported server target. The plugin includes a legacy chat fallback, but modern Paper chat events give the best result.

## Features

- Minecraft chat to Discord.
- Optional webhook mode for Minecraft chat with player-style names and avatars.
- Discord chat to Minecraft.
- Player account linking with `/discord link` and `!link <code>`.
- Discord-side unlinking with `!unlink`.
- Discord-side `!online` and `!players` commands.
- Optional linked Discord role.
- Permission-based Discord role sync for LuckPerms-style permissions such as `group.vip`.
- Join, leave, first-join, advancement, death, lifecycle, and reload messages.
- Styled Discord text or embeds for events.
- Configurable event, chat, and staff channel routing.
- Admin broadcast command for purchase and announcement style messages.

## Discord Bot Setup

1. Create an application and bot in the Discord Developer Portal.
2. Enable `Message Content Intent`.
3. Enable `Server Members Intent`.
4. Invite the bot to your Discord server with:
   - View Channels
   - Send Messages
   - Read Message History
   - Manage Roles
   - Manage Webhooks, only if you use webhook chat mode
5. Put the bot role above every Discord role that DiscordPlus should manage.

## Plugin Installation

1. Download the latest DiscordPlus jar from GitHub Releases.
2. Stop your Minecraft server.
3. Put the jar in your server `plugins` folder.
4. Start the server once to generate `plugins/DiscordPlus/config.yml`.
5. Set `bot.token`, `bot.guild-id`, and `channels.chat-channel-id`.
6. Set `server.invite-url` if you want `/discord invite`.
7. Restart the server, or run `/discord reload`.

Keep your bot token private. Do not paste it into public logs, screenshots, GitHub issues, or Discord messages.

## Minecraft Commands

```text
/discord invite
/discord link
/discord unlink
/discord status
/discord sync
/discord sync <player>
/discord test [type]
/discord broadcast <style> <player|-> <message>
/discord reload
```

Test types:

```text
chat, join, first-join, quit, advancement, death, status, server-start, server-stop, reload
```

Broadcast examples:

```text
/discord broadcast purchase Hilal_h18 Premium Rank
/discord broadcast announcement - Server maintenance starts in 10 minutes.
```

## Discord Commands

Discord users can DM the bot or type in the configured server:

```text
!link <code>
!unlink
!online
!players
```

The command prefix is configurable under `linking.command-prefix`.

## Permissions

```text
discordplus.command  - player commands, default true
discordplus.admin    - admin sync, test, broadcast, and reload commands, default op
```

## Role Sync

Role sync uses Bukkit permissions. With LuckPerms, map Discord roles to group permissions:

```yaml
role-sync:
  enabled: true
  mappings:
    vip:
      permission: "group.vip"
      role-id: "123456789012345678"
```

DiscordPlus only manages roles that you configure in `role-sync.mappings` and the optional linked role. It does not touch unrelated Discord roles.

`role-sync.remove-unmatched-mapped-roles` is disabled by default. Leave it disabled if Discord should be the long-term source of truth for premium or manually granted roles. Enable it only when Minecraft permissions are the authority and you want DiscordPlus to remove mapped roles when the matching Minecraft permission is gone.

## Styling

Discord formatting is controlled under `discord-style`.

- `minecraft-chat` controls Minecraft chat relayed to Discord.
- `minecraft-chat.use-webhook` enables player-style webhook output.
- `join`, `first-join`, `quit`, `advancement`, `death`, `server-start`, `server-stop`, `reload`, `status`, and `broadcasts.<style>` support text or embed styles.
- Embed styles support author rows, titles, descriptions, colors, footers, thumbnails, images, fields, and timestamps.
- `minecraft-style.discord-chat` controls Discord messages shown inside Minecraft.

Channel routing is controlled under `channels`.

- `chat-channel-id` handles chat relay and Discord commands.
- `events-channel-id` handles gameplay and lifecycle events when set.
- `staff-channel-id` can receive lifecycle messages when `lifecycle.use-staff-channel` is enabled.

## Updating

DiscordPlus does not overwrite your existing `config.yml`.

If your config is old or missing the current `config-version`, the plugin writes a fresh reference file to `plugins/DiscordPlus/config-new.yml`. Compare it with your current config and copy over the settings you want.

## Build From Source

```powershell
./mvnw.cmd package
```

The shaded server jar is written to:

```text
target/DiscordPlus-0.1.0.jar
```

## Troubleshooting

- If the bot does not connect, check the token, gateway intents, and console startup logs.
- If Discord messages do not appear in Minecraft, confirm `features.discord-to-minecraft-chat` and the configured channel ID.
- If Minecraft chat does not appear in Discord, confirm `features.minecraft-to-discord-chat` and bot channel permissions.
- If role sync does not work, move the bot role above managed roles and verify the mapped permission is present on the Minecraft player.
- If webhook mode fails, check the webhook URL and make sure it belongs to the intended channel.

## Support

For setup help, compatibility questions, and plugin information, use https://discord.sqware.gg.
