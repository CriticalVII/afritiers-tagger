package za.co.afritiers.tagger.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import za.co.afritiers.tagger.ranking.ModeIcon;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Read-only UV windows remove transparent padding; the bundled PNGs stay unchanged. */
public final class ModeIconBounds {
    private static final JsonObject BOUNDS = load();
    private ModeIconBounds() { }
    private static JsonObject load() {
        try (var stream = ModeIconBounds.class.getResourceAsStream("/assets/afritiers_tagger/mode_bounds.json")) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { return new JsonObject(); }
    }
    public static float[] forIcon(ModeIcon icon) {
        var values = BOUNDS.getAsJsonArray(icon.texture());
        if (values == null || values.size() != 4) return new float[]{0, 0, 1, 1};
        return new float[]{values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat(), values.get(3).getAsFloat()};
    }
}
