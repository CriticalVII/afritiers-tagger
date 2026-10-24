package za.co.afritiers.tagger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import za.co.afritiers.tagger.config.TaggerConfig;
import za.co.afritiers.tagger.ranking.Tier;
import java.util.List;

/** Pixel-measured columns scoped to one native Tab render, using invisible space glyphs. */
public final class TabColumns {
    private static final FontDescription SPACING = new FontDescription.Resource(Identifier.fromNamespaceAndPath("afritiers_tagger", "spacing"));
    private record Widths(int rank, int icon, int tier) { }
    private static Widths current;
    private TabColumns() { }

    public static boolean isSpacing(FontDescription font) { return SPACING.equals(font); }
    public static boolean active() { return current != null; }

    public static void begin(List<PlayerInfo> players) {
        current = null;
        var mod = AfriTiersClient.instance();
        if (mod == null || !mod.activeConfig().enabled || !mod.activeConfig().tabList || !mod.activeConfig().showTopTen) return;
        var config = mod.activeConfig();
        var rankings = mod.repository().state().rankings();
        var font = Minecraft.getInstance().font;
        int rankWidth = 0, iconWidth = 0, tierWidth = 0;
        for (var info : players.stream().limit(80).toList()) {
            String name = mod.rankingUsername(info.getProfile().name(), rankings);
            int position = config.showTopTen ? rankings.topTenPosition(name) : 0;
            if (position == 0) continue;
            rankWidth = Math.max(rankWidth, font.width(TagFormatter.placement(position)));
            var tier = rankings.select(name, config.mode, config.includeRetired);
            if (tier.isPresent()) {
                iconWidth = Math.max(iconWidth, font.width(ModeIcons.component(tier.get().mode(), true)));
                tierWidth = Math.max(tierWidth, font.width(tierText(tier.get(), config)));
            }
        }
        if (rankWidth > 0 || iconWidth > 0) current = new Widths(rankWidth, iconWidth, tierWidth);
    }

    public static void end() { current = null; }

    static Component format(Tier tier, int position, TaggerConfig config, Component original) {
        Widths widths = current;
        if (widths == null) return original;
        var font = Minecraft.getInstance().font;
        var result = Component.empty();
        if (widths.rank > 0) {
            Component rank = TagFormatter.placement(position);
            result.append(rank).append(spaces(widths.rank - font.width(rank)));
        }
        if (widths.icon > 0) {
            Component icon = tier == null ? Component.empty() : ModeIcons.component(tier.mode(), true);
            Component text = tier == null ? Component.empty() : tierText(tier, config);
            result.append(icon).append(spaces(widths.icon - font.width(icon) + 4));
            result.append(text).append(spaces(widths.tier - font.width(text) + 4));
        }
        return result.append(original);
    }

    private static Component tierText(Tier tier, TaggerConfig config) {
        return Component.literal(tier.level() + (tier.retired() ? " (retired)" : ""))
                .withStyle(style -> style.withFont(FontDescription.DEFAULT).withColor(config.colour(tier.level())));
    }

    public static Component spaces(int pixels) {
        var text = new StringBuilder();
        int remaining = Math.max(0, pixels);
        while (remaining > 0) {
            int bit = Math.min(7, 31 - Integer.numberOfLeadingZeros(remaining));
            text.append((char) (0xE100 + bit));
            remaining -= 1 << bit;
        }
        return Component.literal(text.toString()).withStyle(style -> style.withFont(SPACING).withoutShadow().withBold(false).withItalic(false));
    }
}
