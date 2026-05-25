package dev.discordplus.link;

import java.util.UUID;

public record LinkedAccount(UUID playerUuid, String playerName, String discordId, String discordTag, long linkedAt) {
}
