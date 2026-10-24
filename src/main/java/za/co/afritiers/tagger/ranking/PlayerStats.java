package za.co.afritiers.tagger.ranking;

/** Public profile fields and the overall position in one normalized API snapshot. */
public record PlayerStats(String name, String region, int points, int position) {
    public String combatTitle() {
        if (points >= 180) return "Combat Master";
        if (points >= 80) return "Combat Ace";
        if (points >= 40) return "Combat Specialist";
        if (points >= 20) return "Combat Cadet";
        if (points >= 10) return "Combat Novice";
        return "Rookie";
    }

}
