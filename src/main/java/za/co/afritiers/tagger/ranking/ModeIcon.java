package za.co.afritiers.tagger.ranking;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Stable glyphs reference the original supplied textures through our bundled bitmap font. */
public enum ModeIcon {
    AXE('\uE000', "axe", "Axe", "axe"),
    BED('\uE001', "bed-pvp", "Bed PvP", "bed", "bed_pvp"),
    CART('\uE002', "cart", "Cart", "cart"),
    CREEPER('\uE003', "creeper", "Creeper", "creeper"),
    DIAMOND_VANILLA('\uE004', "diamond-vanilla", "Diamond Vanilla", "diavanilla", "diamond_vanilla"),
    DIAPOT('\uE005', "diapot", "DiaPot", "diapot", "diamond_pot", "pot"),
    DIASMP('\uE006', "diasmp", "DiaSMP", "diasmp", "diamond_smp"),
    MACE('\uE007', "mace", "Mace", "mace"),
    NETHOP('\uE008', "nethop", "NethOP", "nethop", "nethpot", "nether_pot"),
    OG_VANILLA('\uE009', "og-vanilla", "OG Vanilla", "ogv", "og_vanilla"),
    OVERALL('\uE00A', "overall", "Overall", "overall"),
    SMP('\uE00B', "smp", "SMP", "smp"),
    SPEAR_MACE('\uE00C', "spear-mace", "Spear Mace", "spear", "spear_mace", "spearmace"),
    SPEED('\uE00D', "speed", "Speed", "speed"),
    SWORD('\uE00E', "sword", "Sword", "sword"),
    UHC('\uE00F', "uhc", "UHC", "uhc"),
    VANILLA('\uE010', "vanilla", "Vanilla", "vanilla");

    private final char glyph;
    private final String texture;
    private final String displayName;
    private final List<String> modes;
    private static final Map<String, ModeIcon> BY_MODE;

    static {
        Map<String, ModeIcon> icons = new HashMap<>();
        for (ModeIcon icon : values()) for (String mode : icon.modes) icons.put(mode, icon);
        BY_MODE = Map.copyOf(icons);
    }

    ModeIcon(char glyph, String texture, String displayName, String... modes) {
        this.glyph = glyph;
        this.texture = texture;
        this.displayName = displayName;
        this.modes = List.of(modes);
    }

    public String glyph() { return Character.toString(glyph); }
    public String texture() { return texture; }
    public String displayName() { return displayName; }

    public static Optional<ModeIcon> find(String mode) {
        if (mode == null) return Optional.empty();
        String normalized = mode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return Optional.ofNullable(BY_MODE.get(normalized));
    }

    /** New API modes get the supplied general-purpose icon until a dedicated asset is added. */
    public static ModeIcon forMode(String mode) { return find(mode).orElse(OVERALL); }
}
