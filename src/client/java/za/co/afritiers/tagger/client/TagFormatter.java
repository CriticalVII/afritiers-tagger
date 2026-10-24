package za.co.afritiers.tagger.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import za.co.afritiers.tagger.config.TaggerConfig;
import za.co.afritiers.tagger.ranking.Tier;

public final class TagFormatter {
    private static final HoverEvent.ShowText OVERALL_HOVER = new HoverEvent.ShowText(Component.literal("AfriTiers overall ranking"));
    private TagFormatter() { }

    public static Component decorate(String username, Component original, boolean tabList) {
        AfriTiersClient mod = AfriTiersClient.instance();
        if (mod == null || original == null) return original;
        TaggerConfig config = mod.activeConfig();
        if (!config.enabled || (tabList ? !config.tabList : !config.nameplates)) return original;
        // A renderer or another compatibility hook may reuse an already decorated component.
        boolean decorated = original.visit((style, text) -> ModeIcons.iconForFont(style.getFont()) != null || TabColumns.isSpacing(style.getFont()) || OVERALL_HOVER.equals(style.getHoverEvent())
                ? java.util.Optional.of(true) : java.util.Optional.empty(),
                net.minecraft.network.chat.Style.EMPTY).orElse(false);
        if (decorated) return original;
        var rankings = mod.repository().state().rankings();
        String apiName = mod.rankingUsername(username);
        int position = config.showTopTen ? rankings.topTenPosition(apiName) : 0;
        var selected = rankings.select(apiName, config.mode, config.includeRetired);
        if (tabList && position > 0 && TabColumns.active()) return TabColumns.format(selected.orElse(null), position, config, original);
        return selected
                .<Component>map(tier -> Component.empty().append(placement(position)).append(tag(tier, config, tabList)).append(original))
                .orElseGet(() -> position == 0 ? original : Component.empty().append(placement(position)).append(original));
    }

    public static Component placement(int position) {
        if (position < 1 || position > 10) return Component.empty();
        return Component.literal("#" + position + " ")
                .withStyle(style -> style.withFont(FontDescription.DEFAULT).withColor(0xFFD700).withHoverEvent(OVERALL_HOVER));
    }

    public static Component tag(Tier tier, TaggerConfig config) {
        return tag(tier, config, false);
    }

    public static Component tag(Tier tier, TaggerConfig config, boolean tabList) {
        return Component.empty()
                .append(ModeIcons.component(tier.mode(), tabList))
                .append(" ")
                .append(Component.literal(tier.level() + (tier.retired() ? " (retired)" : ""))
                        .withStyle(style -> style.withFont(FontDescription.DEFAULT).withColor(config.colour(tier.level()))))
                .append(Component.literal(tabList ? " " : " | ").withColor(0xAAAAAA))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(tier.label()))));
    }
}
