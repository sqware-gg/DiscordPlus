# DiscordPlus

[![Build](https://github.com/sqware-gg/DiscordPlus/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/DiscordPlus/actions/workflows/build.yml)

**Join the SQWARE Discord: [discord.sqware.gg](https://discord.sqware.gg).**

DiscordPlus is a DiscordSRV-style Minecraft Discord bridge for Paper servers. It connects Minecraft chat, Discord chat, server events, account linking, player status commands, Discord role sync, and companion plugin commands in one configurable plugin.

Use it when you want a Discord bridge that also understands SQWARE plugins such as PointsPlus, PlaytimePlus, AuctionsPlus, and OrdersPlus.

## Features

- Minecraft chat to Discord.
- Discord chat to Minecraft.
- Optional webhook chat with player-style names and avatars.
- Account linking with `/discord link` and `!link <code>`.
- Discord commands for online players, linked accounts, points, playtime, auctions, and orders.
- ChatPlus interactive placeholder support for relaying shared items, inventories, and ender chests to Discord.
- Permission-based Discord role sync.
- Optional linked-account Discord role.
- Join, quit, first-join, advancement, death, startup, shutdown, reload, purchase, and announcement messages.
- Text or embed styling per event type.
- Separate channel routing for chat, events, and staff lifecycle messages.

## Requirements

- Paper
- API target: Paper `1.18.2`
- Java `17+`
- JDA `6.4.1`
- Maven wrapper included

Paper is the supported target. The plugin has a legacy chat fallback, but modern Paper chat events provide the most reliable relay behavior.

## Discord Bot Setup

1. Create an application and bot in the Discord Developer Portal.
2. Enable `Message Content Intent`.
3. Enable `Server Members Intent`.
4. Invite the bot with `View Channels`, `Send Messages`, `Read Message History`, and `Manage Roles`.
5. Add `Manage Webhooks` only if you use webhook chat mode.
6. Put the bot role above every Discord role DiscordPlus should manage.

Keep the bot token private. Do not paste it into public logs, screenshots, issues, or Discord messages.

## Minecraft Commands

```text
/discord invite
/discord link
/discord unlink
/discord status
/discord sync [player]
/discord test [type]
/discord broadcast <style> <player|-> <message>
/discord reload
```

Test types:

```text
chat, join, first-join, quit, advancement, death, status, server-start, server-stop, reload
```

## Discord Commands

Discord users can DM the bot or type in the configured server:

```text
!link <code>
!unlink
!online
!players
!points
!points top
!playtime
!playtime top active
!ah list
!ah bid <id> <amount>
!orders list
!orders search <item|buyer|id>
```

The prefix is configurable. Auction bids require a linked Minecraft account with `auctionsplus.bid`.

## Permissions

```text
discordplus.command  - player commands, default true
discordplus.admin    - sync, test, broadcast, and reload commands, default op
```

## Role Sync

Role sync maps Minecraft permissions to Discord role IDs:

```yaml
role-sync:
  enabled: true
  mappings:
    vip:
      permission: "group.vip"
      role-id: "123456789012345678"
```

DiscordPlus only manages configured mapped roles and the optional linked role. It does not touch unrelated Discord roles.

## ChatPlus

When ChatPlus is installed, DiscordPlus uses `ChatPlusApi.renderDiscordChat(...)` for Minecraft-to-Discord chat relay. Messages containing ChatPlus placeholders such as `[item]`, `[inv]`, or `[ender]` are sent to Discord with readable item and inventory summaries.

This is controlled by `integrations.chatplus.interactive-placeholders` in `config.yml`.

## Build

```powershell
.\mvnw.cmd package
```

The shaded jar is written to `target/DiscordPlus-0.1.0.jar`.

## License

DiscordPlus is licensed under the Apache License, Version 2.0.
