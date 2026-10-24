package za.co.afritiers.tagger.config;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TaggerConfig {
    public boolean enabled = true;
    public boolean nameplates = true;
    public boolean tabList = true;
    public boolean includeRetired = true;
    public boolean showTopTen = false;
    public String mode = "strongest";
    /** Empty uses tier colours; otherwise an explicit six-digit RGB value. */
    public String customColour = "";
    /** Explicit current username -> API username aliases, also usable for cracked players. */
    public java.util.Map<String, String> usernameAliases = new java.util.LinkedHashMap<>();

    public TaggerConfig copy() {
        TaggerConfig copy = new TaggerConfig();
        copy.enabled = enabled;
        copy.nameplates = nameplates;
        copy.tabList = tabList;
        copy.includeRetired = includeRetired;
        copy.showTopTen = showTopTen;
        copy.mode = mode;
        copy.customColour = customColour;
        copy.usernameAliases = usernameAliases == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(usernameAliases);
        return copy;
    }

    public void validate() {
        if (usernameAliases == null) usernameAliases = new java.util.LinkedHashMap<>();
        if (usernameAliases.size() > 128) throw new IllegalArgumentException("At most 128 username aliases are supported");
        var normalizedAliases = new java.util.LinkedHashMap<String, String>();
        for (var entry : usernameAliases.entrySet()) {
            String current = entry.getKey(), api = entry.getValue();
            if (current == null || api == null || !current.matches("[A-Za-z0-9_]{1,16}") || !api.matches("[A-Za-z0-9_]{1,16}")) {
                throw new IllegalArgumentException("Aliases require valid Minecraft usernames");
            }
            String previous = normalizedAliases.putIfAbsent(current.toLowerCase(Locale.ROOT), api.toLowerCase(Locale.ROOT));
            if (previous != null && !previous.equalsIgnoreCase(api)) throw new IllegalArgumentException("Conflicting aliases for one username");
        }
        usernameAliases = normalizedAliases;
        if (mode == null || !Pattern.matches("[a-z0-9_-]{1,32}", mode)) mode = "strongest";
        if (customColour == null) customColour = "";
        customColour = customColour.replace("#", "").trim().toUpperCase(Locale.ROOT);
        if (!customColour.isEmpty() && !Pattern.matches("[0-9A-F]{6}", customColour)) {
            throw new IllegalArgumentException("Colour must be six hexadecimal digits or blank");
        }
    }

    public int colour(String level) {
        if (!customColour.isEmpty()) return Integer.parseInt(customColour, 16);
        return switch (level.charAt(2)) {
            case '1' -> 0xFFD700;
            case '2' -> 0xC0C0C0;
            case '3' -> 0xCD7F32;
            default -> 0xB58AE8;
        };
    }
}
