package za.co.afritiers.tagger;

import org.junit.jupiter.api.Test;
import za.co.afritiers.tagger.ranking.Rankings;
import static org.junit.jupiter.api.Assertions.*;

class PaidRenameTest {
    private static final String ID = "a48655695b2d4e068289883d615f94b6";
    private Rankings parse(String players) { return Rankings.parse("{\"players\":[" + players + "],\"stale\":false}"); }
    private String player(String name, String type, String extra) {
        return "{\"name\":\"" + name + "\",\"accountType\":\"" + type + "\"," + extra
                + ",\"tiers\":[{\"mode\":\"spear\",\"level\":\"LT3\",\"retired\":false}]}";
    }
    @Test void paidIdentityFindsTheOldApiNameWithoutRequiringTheServerUuid() {
        var ranks = parse(player("CriticalVII", "paid", "\"minecraftProfileUuid\":\"" + ID + "\""));
        assertEquals("criticalvii", ranks.paidProfiles().get(ID));
        assertEquals("LT3", ranks.select(ranks.paidProfiles().get(ID), "strongest", true).orElseThrow().level());
        assertTrue(ranks.select("SadVerity67", "strongest", true).isEmpty());
    }
    @Test void verifiedLinkedAccountIsUsedWhenTopLevelProfileIdIsMissing() {
        var ranks = parse(player("CriticalVII", "paid", "\"linkedAccount\":{\"accountType\":\"paid\",\"minecraftUuid\":\"" + ID + "\"}"));
        assertEquals("criticalvii", ranks.paidProfiles().get(ID));
    }
    @Test void crackedAndUnverifiedRecordsNeverPublishPaidIdentities() {
        var ranks = parse(player("Cracked", "cracked", "\"minecraftProfileUuid\":\"" + ID + "\"") + ","
                + player("Unknown", "unknown", "\"minecraftProfileUuid\":\"" + ID + "\"") + ","
                + player("Placeholder", "paid", "\"minecraftProfileUuid\":\"00000000000000000000000000000000\"") + ","
                + player("UsernameKey", "paid", "\"uuid\":\"" + ID + "\""));
        assertTrue(ranks.paidProfiles().isEmpty());
        assertTrue(ranks.select("Cracked", "strongest", true).isPresent());
    }
    @Test void conflictingPaidIdsAndDuplicateAccountsAreExcluded() {
        var ranks = parse(player("One", "paid", "\"minecraftProfileUuid\":\"" + ID + "\"") + ","
                + player("Two", "paid", "\"minecraftProfileUuid\":\"" + ID + "\"") + ","
                + player("Conflict", "paid", "\"minecraftProfileUuid\":\"11111111111111111111111111111111\","
                    + "\"linkedAccount\":{\"accountType\":\"paid\",\"minecraftUuid\":\"22222222222222222222222222222222\"}"));
        assertTrue(ranks.paidProfiles().isEmpty());
    }
    @Test void duplicateUsernamesCannotReenterThroughPaidRecovery() {
        var ranks = parse(player("One", "paid", "\"minecraftProfileUuid\":\"" + ID + "\"") + ","
                + player("ONE", "paid", "\"minecraftProfileUuid\":\"" + ID + "\""));
        assertTrue(ranks.players().isEmpty()); assertTrue(ranks.paidProfiles().isEmpty());
    }
}
