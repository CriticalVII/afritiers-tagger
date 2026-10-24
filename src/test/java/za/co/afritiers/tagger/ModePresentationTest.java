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

    @Test void everyMappedGlyphHasOneBundledFontProviderAndReadablePng() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/afritiers_tagger/font/mode_icons.json")) {
            assertNotNull(stream);
            var providers = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("providers");
            assertEquals(ModeIcon.values().length, providers.size());
            var found = new HashSet<String>();
            for (var entry : providers) {
                var provider = entry.getAsJsonObject();
                String glyph = provider.getAsJsonArray("chars").get(0).getAsString();
                assertTrue(found.add(glyph), "Duplicate icon glyph");
                String resource = provider.get("file").getAsString();
                try (var imageStream = getClass().getResourceAsStream("/assets/" + resource.replace(":", "/textures/"))) {
                    assertNotNull(imageStream, resource);
                    var image = javax.imageio.ImageIO.read(imageStream);
                    assertNotNull(image, resource);
                    assertTrue(image.getColorModel().hasAlpha(), resource);
                }
                for (var icon : ModeIcon.values()) {
                    if (icon.glyph().equals(glyph)) assertEquals("afritiers_tagger:mode/" + icon.texture() + ".png", resource);
                }
            }
            for (var icon : ModeIcon.values()) assertTrue(found.contains(icon.glyph()));
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
