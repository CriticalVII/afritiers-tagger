package za.co.afritiers.tagger;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import za.co.afritiers.tagger.config.TaggerConfig;
import za.co.afritiers.tagger.ranking.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ModePresentationTest {
    @Test void highAndLowRanksShareTheRequestedMedalPalette() {
        TaggerConfig config = new TaggerConfig();
        int[] expected = {0xFFD700, 0xC0C0C0, 0xCD7F32, 0xAAAAAA, 0xAAAAAA};
        for (int tier = 1; tier <= 5; tier++) {
            assertEquals(expected[tier - 1], config.colour("HT" + tier));
            assertEquals(expected[tier - 1], config.colour("LT" + tier));
        }
    }

    @Test void websiteModeIdsResolveToTheSuppliedFilenameVariants() {
        assertEquals("bed-pvp", ModeIcon.forMode("bed").texture());
        assertEquals("diamond-vanilla", ModeIcon.forMode("diavanilla").texture());
        assertEquals("spear-mace", ModeIcon.forMode("spear").texture());
        assertEquals("og-vanilla", ModeIcon.forMode("ogv").texture());
        assertEquals("nethop", ModeIcon.forMode("nethpot").texture());
        assertEquals(ModeIcon.SPEAR_MACE, ModeIcon.forMode("SPEAR-MACE"));
        assertEquals(ModeIcon.OVERALL, ModeIcon.forMode("new_mode"));
    }

    @Test void everyMappedGlyphHasUniqueGlyphAndValidUvBounds() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/afritiers_tagger/mode_bounds.json")) {
            assertNotNull(stream);
            var bounds = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(ModeIcon.values().length, bounds.size());
            var found = new HashSet<String>();
            for (var icon : ModeIcon.values()) {
                assertTrue(found.add(icon.glyph()), "Duplicate icon glyph");
                assertTrue(bounds.has(icon.texture()), "Missing icon bounds: " + icon.texture());
                var window = bounds.getAsJsonArray(icon.texture());
                assertEquals(4, window.size());
                for (var value : window) assertTrue(value.getAsDouble() >= 0 && value.getAsDouble() <= 1);
                assertTrue(window.get(0).getAsDouble() < window.get(2).getAsDouble());
                assertTrue(window.get(1).getAsDouble() < window.get(3).getAsDouble());
            }
        }
    }

    @Test void hyphenatedModeIdentifiersCanBeSelected() {
        var rankings = Rankings.parse("""
            {"players":[{"name":"Fighter","tiers":[{"mode":"bed-pvp","level":"LT2","retired":false}]}],"stale":false}
            """);
        assertEquals("LT2", rankings.select("Fighter", "bed-pvp", false).orElseThrow().level());
        var config = new TaggerConfig();
        config.mode = "bed-pvp";
        config.validate();
        assertEquals("bed-pvp", config.mode);
    }

    @Test void everyRemadeSpriteIsBundledWithTransparentPadding() throws Exception {
        for (var icon : ModeIcon.values()) {
            String resource = "/assets/afritiers_tagger/textures/gui/sprites/mode/remade/" + icon.texture() + ".png";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var image = javax.imageio.ImageIO.read(stream);
                assertNotNull(image, resource);
                assertTrue(image.getColorModel().hasAlpha(), resource);
                assertEquals(image.getWidth(), image.getHeight(), resource);
                assertEquals(0, image.getRGB(0, 0) >>> 24, "Sprite corner is opaque: " + resource);
                assertEquals(0, image.getRGB(image.getWidth() - 1, image.getHeight() - 1) >>> 24, resource);
            }
        }
    }
}
