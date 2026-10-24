package za.co.afritiers.tagger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import za.co.afritiers.tagger.config.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path directory;

    @Test void savesIndependentCopyAndRestoresColourAndMode() throws Exception {
        ConfigStore store = new ConfigStore(directory.resolve("config.json"));
        TaggerConfig config = store.load();
        TaggerConfig draft = config.copy();
        draft.mode = "mace";
        draft.customColour = "#aabbcc";
        draft.nameplates = false;
        draft.showTopTen = true;
        assertEquals("strongest", config.mode);
        store.save(draft);
        var loaded = store.load();
        assertEquals("mace", loaded.mode);
        assertEquals(0xAABBCC, loaded.colour("HT1"));
        assertFalse(loaded.nameplates);
        assertTrue(loaded.showTopTen);
        assertFalse(config.showTopTen);
    }
    @Test void invalidConfigIsBackedUpAndDefaultsCanBeSaved() throws Exception {
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{broken");
        ConfigStore store = new ConfigStore(path);
        assertTrue(store.load().enabled);
        assertTrue(store.warning().contains("backed up"));
        try (var files = Files.list(directory)) { assertEquals(1, files.filter(p -> p.toString().contains(".invalid-")).count()); }
        store.save(new TaggerConfig());
        assertTrue(Files.exists(path));
    }
    @Test void invalidColourCannotBeSaved() {
        TaggerConfig config = new TaggerConfig();
        config.customColour = "nope";
        assertThrows(IllegalArgumentException.class, () -> new ConfigStore(directory.resolve("config.json")).save(config));
    }
    @Test void usernameAliasesAreNormalizedPersistedAndCopiedIndependently() throws Exception {
        var store = new ConfigStore(directory.resolve("config.json"));
        var config = store.load(); var draft = config.copy();
        draft.usernameAliases.put("NewCracked", "OldCracked");
        assertTrue(config.usernameAliases.isEmpty());
        store.save(draft);
        assertEquals("oldcracked", store.load().usernameAliases.get("newcracked"));
        draft.usernameAliases.put("Bad Name", "OldCracked");
        assertThrows(IllegalArgumentException.class, () -> store.save(draft));
    }
}
