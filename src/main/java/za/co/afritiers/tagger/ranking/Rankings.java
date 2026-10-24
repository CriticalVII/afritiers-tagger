package za.co.afritiers.tagger.ranking;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Username lookup plus a separate verified-paid-account index for rename recovery. */
public record Rankings(Map<String, List<Tier>> players, List<String> modes,
                       Instant updatedAt, boolean stale, int ambiguousNames, int skippedRecords,
                       Map<String, String> paidProfiles, Map<String, PlayerStats> stats) {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern MODE = Pattern.compile("[a-z0-9_-]{1,32}");
    public static final Rankings EMPTY = new Rankings(Map.of(), List.of(), null, false, 0, 0, Map.of(), Map.of());

    public Rankings {
        var immutable = new HashMap<String, List<Tier>>();
        players.forEach((name, tiers) -> immutable.put(name, List.copyOf(tiers)));
        players = Map.copyOf(immutable);
        modes = List.copyOf(modes);
        paidProfiles = Map.copyOf(paidProfiles);
        stats = Map.copyOf(stats);
    }

    public Optional<PlayerStats> profile(String username) {
        return Optional.ofNullable(stats.get(Tier.normalizeUsername(username)));
    }

    public int topTenPosition(String username) {
        PlayerStats profile = stats.get(Tier.normalizeUsername(username));
        return profile != null && profile.position() >= 1 && profile.position() <= 10 ? profile.position() : 0;
    }

    public Optional<Tier> select(String username, String mode, boolean includeRetired) {
        ModeIcon selected = ModeIcon.find(mode).orElse(null);
        return players.getOrDefault(Tier.normalizeUsername(username), List.of()).stream()
                .filter(tier -> includeRetired || !tier.retired())
                .filter(tier -> mode.equals("strongest") || tier.mode().equals(mode)
                        || selected != null && ModeIcon.find(tier.mode()).orElse(null) == selected)
                .min(Comparator.comparingInt(Tier::strength).thenComparing(Tier::mode)
                        .thenComparing(Tier::retired));
    }

    public static Rankings parse(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT || !element.isJsonObject()) {
                throw new IllegalArgumentException("Invalid response envelope");
            }
            JsonObject root = element.getAsJsonObject();
            if (!root.has("players") || !root.get("players").isJsonArray()) {
                throw new IllegalArgumentException("Response is missing the players array");
            }
            Map<String, List<Tier>> players = new HashMap<>();
            Map<String, PlayerStats> stats = new HashMap<>();
            Map<String, String> paidProfiles = new HashMap<>();
            Set<String> conflictingProfiles = new HashSet<>();
            Set<String> seenNames = new HashSet<>();
            Set<String> ambiguous = new HashSet<>();
            Set<String> modes = new TreeSet<>();
            int skipped = 0;
            for (JsonElement entry : root.getAsJsonArray("players")) {
                if (!entry.isJsonObject()) { skipped++; continue; }
                JsonObject player = entry.getAsJsonObject();
                String name = string(player, "name").trim();
                if (!USERNAME.matcher(name).matches()) { skipped++; continue; }
                String key = Tier.normalizeUsername(name);
                String paidId = paidProfileId(player);
                if (paidId != null) {
                    String previous = paidProfiles.putIfAbsent(paidId, key);
                    if (previous != null && !previous.equals(key)) conflictingProfiles.add(paidId);
                }
                if (!seenNames.add(key)) { ambiguous.add(key); players.remove(key); stats.remove(key); }
                if (!player.has("tiers") || !player.get("tiers").isJsonArray()) { skipped++; continue; }
                List<Tier> tiers = new ArrayList<>();
                Set<String> seenModes = new HashSet<>();
                Set<String> conflictingModes = new HashSet<>();
                for (JsonElement tierEntry : player.getAsJsonArray("tiers")) {
                    if (!tierEntry.isJsonObject()) { skipped++; continue; }
                    JsonObject tier = tierEntry.getAsJsonObject();
                    String mode = string(tier, "mode").trim().toLowerCase(Locale.ROOT);
                    String level = string(tier, "level").trim().toUpperCase(Locale.ROOT);
                    if (!MODE.matcher(mode).matches()) { skipped++; continue; }
                    modes.add(mode);
                    if (!Tier.ORDER.contains(level)) { skipped++; continue; }
                    if (!seenModes.add(mode)) conflictingModes.add(mode);
                    boolean retired = bool(tier, "retired", true);
                    tiers.add(new Tier(mode, level, retired));
                }
                tiers.removeIf(tier -> conflictingModes.contains(tier.mode()));
                if (!ambiguous.contains(key)) {
                    players.put(key, tiers);
                    String region = string(player, "region").trim().toUpperCase(Locale.ROOT);
                    if (!region.matches("[A-Z]{2,3}")) region = "Unknown";
                    stats.put(key, new PlayerStats(name, region, tiers.stream().mapToInt(Tier::points).sum(), 0));
                }
            }
            // Same rule as the website's profilePosition: active points descending, then name.
            var names = java.text.Collator.getInstance(Locale.ENGLISH);
            names.setStrength(java.text.Collator.SECONDARY);
            List<String> ordered = players.keySet().stream().filter(key -> !players.get(key).isEmpty())
                    .sorted(Comparator.<String>comparingInt(key -> stats.get(key).points()).reversed()
                            .thenComparing(key -> stats.get(key).name(), names).thenComparing(Comparator.naturalOrder())).toList();
            for (int i = 0; i < ordered.size(); i++) {
                String key = ordered.get(i);
                PlayerStats profile = stats.get(key);
                stats.put(key, new PlayerStats(profile.name(), profile.region(), profile.points(), i + 1));
            }
            Instant updatedAt = null;
            try { updatedAt = Instant.parse(string(root, "updatedAt")); }
            catch (java.time.format.DateTimeParseException ignored) { }
            paidProfiles.entrySet().removeIf(entry -> conflictingProfiles.contains(entry.getKey()) || !players.containsKey(entry.getValue()));
            return new Rankings(players, new ArrayList<>(modes), updatedAt,
                    bool(root, "stale", true), ambiguous.size(), skipped, paidProfiles, stats);
        } catch (JsonParseException | java.io.IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("Invalid API JSON", exception);
        }
    }

    public static String canonicalProfileId(String value) {
        if (value == null) return null;
        String compact = value.replace("-", "").toLowerCase(Locale.ROOT);
        return compact.matches("[0-9a-f]{32}") && !compact.equals("00000000000000000000000000000000") ? compact : null;
    }

    private static String paidProfileId(JsonObject player) {
        if (!string(player, "accountType").equalsIgnoreCase("paid")) return null;
        String direct = canonicalProfileId(string(player, "minecraftProfileUuid"));
        String linked = null;
        if (player.has("linkedAccount") && player.get("linkedAccount").isJsonObject()) {
            JsonObject account = player.getAsJsonObject("linkedAccount");
            if (string(account, "accountType").equalsIgnoreCase("paid")) linked = canonicalProfileId(string(account, "minecraftUuid"));
        }
        if (direct != null && linked != null && !direct.equals(linked)) return null;
        return direct != null ? direct : linked;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : fallback;
    }
}
