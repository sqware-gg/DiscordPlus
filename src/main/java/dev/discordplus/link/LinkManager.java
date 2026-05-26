package dev.discordplus.link;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LinkManager {
    private final JavaPlugin plugin;
    private final File file;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, LinkedAccount> linksByPlayer = new HashMap<>();
    private final Map<String, UUID> playerByDiscord = new HashMap<>();
    private final Map<String, PendingLink> pendingLinks = new HashMap<>();

    public LinkManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "links.yml");
        reload();
    }

    public synchronized void reload() {
        linksByPlayer.clear();
        playerByDiscord.clear();
        pendingLinks.clear();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("links");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String playerName = section.getString(key + ".player-name", "");
                String discordId = section.getString(key + ".discord-id", "");
                String discordTag = section.getString(key + ".discord-tag", "");
                long linkedAt = section.getLong(key + ".linked-at", Instant.now().getEpochSecond());
                if (!discordId.isBlank()) {
                    LinkedAccount account = new LinkedAccount(uuid, playerName, discordId, discordTag, linkedAt);
                    linksByPlayer.put(uuid, account);
                    playerByDiscord.put(discordId, uuid);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid linked player UUID in links.yml: " + key);
            }
        }
    }

    public synchronized void save() {
        if (!plugin.getDataFolder().exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (LinkedAccount account : linksByPlayer.values()) {
            String path = "links." + account.playerUuid();
            yaml.set(path + ".player-name", account.playerName());
            yaml.set(path + ".discord-id", account.discordId());
            yaml.set(path + ".discord-tag", account.discordTag());
            yaml.set(path + ".linked-at", account.linkedAt());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save links.yml: " + e.getMessage());
        }
    }

    public synchronized PendingLink createCode(Player player, int expiryMinutes) {
        purgeExpired();
        pendingLinks.values().removeIf(link -> link.playerUuid().equals(player.getUniqueId()));

        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (pendingLinks.containsKey(code));

        PendingLink link = new PendingLink(code, player.getUniqueId(), player.getName(),
                System.currentTimeMillis() + expiryMinutes * 60_000L);
        pendingLinks.put(code, link);
        return link;
    }

    public synchronized Optional<PendingLink> consumeCode(String code) {
        purgeExpired();
        String normalized = code == null ? "" : code.trim();
        PendingLink link = pendingLinks.remove(normalized);
        return Optional.ofNullable(link);
    }

    public synchronized LinkedAccount link(PendingLink pendingLink, String discordId, String discordTag) {
        LinkedAccount previousAccount = linksByPlayer.remove(pendingLink.playerUuid());
        if (previousAccount != null) {
            playerByDiscord.remove(previousAccount.discordId());
        }

        UUID previousPlayer = playerByDiscord.remove(discordId);
        if (previousPlayer != null) {
            linksByPlayer.remove(previousPlayer);
        }

        LinkedAccount account = new LinkedAccount(pendingLink.playerUuid(), pendingLink.playerName(),
                discordId, discordTag, Instant.now().getEpochSecond());
        linksByPlayer.put(account.playerUuid(), account);
        playerByDiscord.put(account.discordId(), account.playerUuid());
        save();
        return account;
    }

    public synchronized Optional<LinkedAccount> unlink(UUID playerUuid) {
        LinkedAccount removed = linksByPlayer.remove(playerUuid);
        if (removed != null) {
            playerByDiscord.remove(removed.discordId());
            save();
        }
        return Optional.ofNullable(removed);
    }

    public synchronized Optional<LinkedAccount> findByPlayer(UUID playerUuid) {
        return Optional.ofNullable(linksByPlayer.get(playerUuid));
    }

    public synchronized Optional<LinkedAccount> findByDiscordId(String discordId) {
        UUID playerUuid = playerByDiscord.get(discordId);
        if (playerUuid == null) {
            return Optional.empty();
        }
        return findByPlayer(playerUuid);
    }

    public synchronized Optional<LinkedAccount> findByPlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        return linksByPlayer.values().stream()
                .filter(account -> account.playerName().equalsIgnoreCase(playerName))
                .findFirst();
    }

    public synchronized Optional<LinkedAccount> unlinkByDiscordId(String discordId) {
        UUID playerUuid = playerByDiscord.get(discordId);
        if (playerUuid == null) {
            return Optional.empty();
        }
        return unlink(playerUuid);
    }

    public synchronized int linkCount() {
        return linksByPlayer.size();
    }

    public synchronized int pendingCount() {
        purgeExpired();
        return pendingLinks.size();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        pendingLinks.values().removeIf(link -> link.expiresAtMillis() <= now);
    }

    public record PendingLink(String code, UUID playerUuid, String playerName, long expiresAtMillis) {
    }
}
