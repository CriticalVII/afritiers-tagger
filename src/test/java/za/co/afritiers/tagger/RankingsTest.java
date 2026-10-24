package za.co.afritiers.tagger;

import org.junit.jupiter.api.Test;
import za.co.afritiers.tagger.ranking.*;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class RankingsTest {
    private Rankings parse(String players) {
        return Rankings.parse("{\"players\":[" + players + "],\"updatedAt\":\"2026-10-23T12:00:00Z\",\"stale\":false}");
    }
    private String player(String name, String tiers) {
        return "{\"name\":\"" + name + "\",\"tiers\":[" + tiers + "]}";
    }
    private String tier(String mode, String level, boolean retired) {
        return "{\"mode\":\"" + mode + "\",\"level\":\"" + level + "\",\"retired\":" + retired + "}";
    }

    @Test void matchesCaseInsensitivelyRegardlessOfUuidOrAccountType() {
        Rankings rankings = parse("""
            {"name":"OfflinePvper","uuid":"not-a-uuid","minecraftProfileUuid":"unrelated",
             "accountType":"paid","tiers":[{"mode":"mace","level":"HT5","retired":false}]}
            """);
        assertEquals("HT5", rankings.select("OFFLINEpvper", "mace", false).orElseThrow().level());
        assertTrue(rankings.select("unrelated", "mace", false).isEmpty());
    }
    @Test void missingUuidAndAccountTypeDoNotMatter() {
        assertTrue(parse(player("CrackedUser", tier("sword", "LT2", false)))
                .select("crackeduser", "strongest", false).isPresent());
    }
    @Test void duplicateNormalizedNamesStayAmbiguousEvenWithThreeRecords() {
        String tiers = tier("mace", "HT3", false);
        Rankings rankings = parse(player("Fighter", tiers) + "," + player("FIGHTER", tiers) + "," + player("fighter", tiers));
        assertEquals(1, rankings.ambiguousNames());
        assertTrue(rankings.select("Fighter", "strongest", true).isEmpty());
    }
    @Test void namesAreNotInferredFromLinkedAccountsOrUuid() {
        Rankings rankings = parse("""
            {"name":"NewName","uuid":"OldName","linkedAccount":{"minecraftUsername":"OldName"},
             "tiers":[{"mode":"mace","level":"HT3","retired":false}]}
            """);
        assertTrue(rankings.select("OldName", "strongest", false).isEmpty());
        assertTrue(rankings.select("NewName", "strongest", false).isPresent());
        assertTrue(rankings.select("[VIP] NewName", "strongest", false).isEmpty());
    }
    @Test void strongerTierWinsAndModeSelectionIsRespected() {
        Rankings rankings = parse(player("Tester", tier("sword", "LT1", false) + "," + tier("mace", "HT2", false)));
        assertEquals("LT1", rankings.select("Tester", "strongest", false).orElseThrow().level());
        assertEquals("HT2", rankings.select("Tester", "mace", false).orElseThrow().level());
        assertTrue(rankings.select("Tester", "creeper", false).isEmpty());
    }
    @Test void excludesRetiredAndDoesNotUsePeaks() {
        Rankings rankings = parse(player("Tester", tier("mace", "HT1", true) + "," + tier("sword", "HT4", false)));
        assertEquals("HT4", rankings.select("Tester", "strongest", false).orElseThrow().level());
        assertEquals("HT1", rankings.select("Tester", "strongest", true).orElseThrow().level());
    }
    @Test void unknownTiersAreSkippedButModeRemainsSelectable() {
        Rankings rankings = parse(player("Tester", tier("new_mode", "GOD", false)));
        assertTrue(rankings.modes().contains("new_mode"));
        assertTrue(rankings.select("Tester", "strongest", true).isEmpty());
    }
    @Test void conflictingModeEntriesAreNotGuessed() {
        Rankings rankings = parse(player("Tester", tier("mace", "HT1", false) + "," + tier("mace", "HT2", false)));
        assertTrue(rankings.select("Tester", "mace", true).isEmpty());
    }
    @Test void malformedEnvelopeFailsWhileEmptyListSucceeds() {
        for (String json : new String[]{"{}", "[]", "{\"players\":null}", "{\"players\":[]} trailing", "{players:[]}"}) {
            assertThrows(IllegalArgumentException.class, () -> Rankings.parse(json));
        }
        assertTrue(parse("").players().isEmpty());
    }
    @Test void malformedPlayerCannotHideOtherPlayers() {
        Rankings rankings = parse("null,{\"name\":42},{\"name\":\"bad name\"}," + player("Good", tier("creeper", "LT5", false)));
        assertEquals(3, rankings.skippedRecords());
        assertTrue(rankings.select("Good", "strongest", false).isPresent());
    }
    @Test void normalizationDoesNotDependOnTurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("fighter", Tier.normalizeUsername("FIGHTER"));
        } finally { Locale.setDefault(original); }
    }
}
