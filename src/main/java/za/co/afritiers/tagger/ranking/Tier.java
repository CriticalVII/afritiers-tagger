package za.co.afritiers.tagger.ranking;

import java.util.List;
import java.util.Locale;

public record Tier(String mode, String level, boolean retired) {
    public static final List<String> ORDER = List.of("HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5");

    public int strength() { return ORDER.indexOf(level); }

    /** AfriTiers awards points for active current tiers, never retired tiers or peaks. */
    public int points() {
        if (retired) return 0;
        return switch (level) {
            case "HT1" -> 60; case "LT1" -> 45;
            case "HT2" -> 30; case "LT2" -> 20;
            case "HT3" -> 10; case "LT3" -> 6;
            case "HT4" -> 4; case "LT4" -> 3;
            case "HT5" -> 2; case "LT5" -> 1;
            default -> 0;
        };
    }

    public String label() {
        return modeLabel(mode) + " " + level + (retired ? " (retired)" : "");
    }

    public static String modeLabel(String mode) {
        return ModeIcon.find(mode).map(ModeIcon::displayName)
                .orElseGet(() -> Character.toUpperCase(mode.charAt(0)) + mode.substring(1).replace('_', ' ').replace('-', ' '));
    }

    public static String normalizeUsername(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
