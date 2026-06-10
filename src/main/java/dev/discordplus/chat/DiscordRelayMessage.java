package dev.discordplus.chat;

import java.util.List;

public record DiscordRelayMessage(String content, List<ItemPreview> itemPreviews) {
    public static DiscordRelayMessage plain(String content) {
        return new DiscordRelayMessage(content == null ? "" : content, List.of());
    }

    public boolean hasItemPreviews() {
        return itemPreviews != null && !itemPreviews.isEmpty();
    }

    public record ItemPreview(
            String placeholder,
            String hand,
            String ownerName,
            String name,
            String materialKey,
            String materialName,
            String imageKey,
            int amount,
            String durability,
            int customModelData,
            List<String> enchantments,
            List<String> lore
    ) {
    }
}
