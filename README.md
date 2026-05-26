# DiscordPlus

[![Build](https://github.com/sqware-gg/DiscordPlus/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/DiscordPlus/actions/workflows/build.yml)

DiscordPlus is a Minecraft Discord bridge plugin for Paper servers. It connects in-game chat, server events, account linking, player status commands, and Discord role sync in one configurable plugin.

Use it to build an active Discord community around your Minecraft server: relay chat, announce joins and deaths, post advancement messages, show online players, link accounts, and keep Discord roles aligned with Minecraft permissions.

## Links

- Website: https://sqware.gg
- Support and plugin updates: https://discord.sqware.gg

## Compatibility

- Server software: Paper
- API target: Paper `1.18.2`
- Java: `17+`
- Discord library: JDA `6.4.1`
- Build tool: Maven

As of May 2026, Paper is the supported server target. The plugin includes a legacy chat fallback, but modern Paper chat events provide the best chat relay behavior.

## Why Server Owners Use It

- Bring Minecraft chat and Discord chat into one community feed.
- Announce server activity with clean Discord embeds.
- Let players link Minecraft and Discord accounts.
- Sync Discord roles from Minecraft permissions and groups.
- Keep event, staff, and chat messages in separate Discord channels.
- Customize Discord formatting without editing source code.

## Features

- Minecraft chat to Discord.
- Discord chat to Minecraft.
- Optional webhook chat mode with player-style names and avatars.
- Account linking with `/discord link` and `!link <code>`.
- Discord commands: `!online`, `!players`, `!link`, `!unlink`, and optional companion plugin commands.
- Optional linked-account Discord role.
- Permission-based role sync for Minecraft groups.
- Join, leave, first-join, advancement, death, startup, shutdown, and reload messages.
- Text or embed styles for server events.
- Channel routing for chat, events, and staff/admin lifecycle messages.
- Admin broadcast styles for purchase messages, announcements, and custom broadcasts.

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

1. Download the latest jar from the GitHub Releases page.
2. Stop your Minecraft server.
3. Put the jar in the server `plugins` folder.
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
!points top
!playtime
!playtime top active
!ah list
!ah bid <id> <amount>
```

The command prefix is configurable under `linking.command-prefix`.
PointsPlus command results reply to the original Discord message in the channel where the command was used.
PlaytimePlus commands are available when PlaytimePlus is installed and `integrations.playtimeplus.events.commands` is enabled.
Auctions+ bid commands are available when Auctions+ is installed and `integrations.auctionsplus.events.commands` is enabled.
Discord auction bids require a linked Minecraft account and a verified `auctionsplus.bid` permission.
Server owners can restrict auction commands with `integrations.auctionsplus.commands.allowed-channel-ids`,
`require-player-online`, `cooldown-seconds`, `max-bid`, and `list-limit`.

## Permissions

```text
discordplus.command  - player commands, default true
discordplus.admin    - admin sync, test, broadcast, and reload commands, default op
```

## Role Sync

Role sync maps Minecraft permissions to Discord role IDs. It works well with permission plugins that expose group permissions such as `group.vip` or `group.staff`.

```yaml
role-sync:
  enabled: true
  mappings:
    vip:
      permission: "group.vip"
      role-id: "123456789012345678"
```

DiscordPlus only manages roles configured in `role-sync.mappings` and the optional linked role. It does not touch unrelated Discord roles.

`role-sync.remove-unmatched-mapped-roles` is disabled by default. Leave it disabled if Discord should remain the long-term source of truth for premium or manually granted roles. Enable it only when Minecraft permissions are the authority and mapped Discord roles should be removed when the matching permission is gone.

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

If your config is old or missing the current `config-version`, the plugin writes `plugins/DiscordPlus/config-new.yml`. Compare it with your current config and copy over the settings you want.

Release history is tracked in [CHANGELOG.md](CHANGELOG.md).

## Build From Source

```powershell
./mvnw.cmd package
```

The shaded server jar is written to:

```text
target/DiscordPlus-0.1.0.jar
```

## Troubleshooting

- Bot does not connect: check the token, enabled gateway intents, and console startup logs.
- Discord chat does not appear in Minecraft: confirm `features.discord-to-minecraft-chat` and the configured channel ID.
- Minecraft chat does not appear in Discord: confirm `features.minecraft-to-discord-chat` and bot channel permissions.
- Role sync does not work: move the bot role above managed roles and verify the mapped Minecraft permission is present.
- Webhook mode fails: check the webhook URL and make sure it belongs to the intended Discord channel.

## License

DiscordPlus is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Support

For setup help, compatibility questions, and plugin updates, use https://discord.sqware.gg.
