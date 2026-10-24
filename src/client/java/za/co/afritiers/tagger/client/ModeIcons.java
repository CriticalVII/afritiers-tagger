package za.co.afritiers.tagger.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.data.AtlasIds;
import za.co.afritiers.tagger.ranking.ModeIcon;

public final class ModeIcons {
    private static final java.util.Map<ModeIcon, FontDescription.AtlasSprite> FONTS = new java.util.EnumMap<>(ModeIcon.class);
    private static final java.util.Map<ModeIcon, FontDescription.Resource> TAB_FONTS = new java.util.EnumMap<>(ModeIcon.class);
    static {
        for (ModeIcon icon : ModeIcon.values()) {
            FONTS.put(icon, new FontDescription.AtlasSprite(AtlasIds.GUI,
                    Identifier.fromNamespaceAndPath("afritiers_tagger", "mode/remade/" + icon.texture())));
            TAB_FONTS.put(icon, new FontDescription.Resource(Identifier.fromNamespaceAndPath("afritiers_tagger", "tab/" + icon.texture())));
        }
    }

    private ModeIcons() { }

    public static boolean isModeSprite(FontDescription.AtlasSprite font) {
        return FONTS.containsValue(font);
    }

    public static ModeIcon iconForFont(FontDescription font) {
        for (ModeIcon icon : ModeIcon.values()) if (FONTS.get(icon).equals(font) || TAB_FONTS.get(icon).equals(font)) return icon;
        return null;
    }

    public static FontDescription.AtlasSprite sprite(ModeIcon icon) { return FONTS.get(icon); }

    public static Component component(String mode) {
        return component(mode, false);
    }

    public static Component component(String mode, boolean tabList) {
        ModeIcon icon = ModeIcon.forMode(mode);
        FontDescription font = tabList ? TAB_FONTS.get(icon) : FONTS.get(icon);
        return Component.literal(icon.glyph()).withStyle(style -> style
                .withFont(font).withColor(0xFFFFFF).withoutShadow().withBold(false).withItalic(false));
    }
}
