package za.co.afritiers.tagger;

import org.junit.jupiter.api.Test;
import za.co.afritiers.tagger.ranking.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsTest {
    @Test void pointsIgnoreRetiredPeaksAndUntrustedTotals() {
        var rankings = Rankings.parse("""
                {"players":[{"name":"Fighter","region":"za","totalPoints":999999,
                "tiers":[{"mode":"sword","level":"HT2","retired":false,"peakTier":"HT1"},
                         {"mode":"mace","level":"HT1","retired":true}]}]}
                """);
        var stats = rankings.profile("FIGHTER").orElseThrow();
        assertEquals("Fighter", stats.name());
        assertEquals("ZA", stats.region());
        assertEquals(30, stats.points());
        assertEquals("Combat Cadet", stats.combatTitle());
        assertEquals(1, stats.position());
    }

    @Test void overallPositionsUsePointsAndAlphabeticalTieBreakAndStopAtTen() {
        var players = new com.google.gson.JsonArray();
        for (int i = 12; i >= 0; i--) {
            var player = new com.google.gson.JsonObject();
            player.addProperty("name", "Fighter" + String.format("%02d", i));
            player.add("tiers", com.google.gson.JsonParser.parseString("[{\"mode\":\"mace\",\"level\":\"HT3\",\"retired\":false}]"));
            players.add(player);
        }
        var root = new com.google.gson.JsonObject(); root.add("players", players);
        var rankings = Rankings.parse(root.toString());
        assertEquals(1, rankings.topTenPosition("Fighter00"));
        assertEquals(10, rankings.topTenPosition("Fighter09"));
        assertEquals(11, rankings.profile("Fighter10").orElseThrow().position());
        assertEquals(0, rankings.topTenPosition("Fighter10"));
        assertEquals(0, rankings.topTenPosition("missing"));
    }

    @Test void malformedRegionIsUnknownAndEmptyOrAmbiguousPlayersHaveNoPlacement() {
        var rankings = Rankings.parse("""
                {"players":[{"name":"Empty","region":"bad text","tiers":[]},
                 {"name":"DUP","tiers":[{"mode":"sword","level":"HT1","retired":false}]},
                 {"name":"dup","tiers":[]}]}
                """);
        assertEquals("Unknown", rankings.profile("empty").orElseThrow().region());
        assertEquals(0, rankings.profile("empty").orElseThrow().position());
        assertTrue(rankings.profile("dup").isEmpty());
        assertEquals(0, rankings.topTenPosition("dup"));
    }

    @Test void pointTableAndCombatThresholdsFollowAfriTiers() {
        int[] points = {60,45,30,20,10,6,4,3,2,1};
        for (int i = 0; i < Tier.ORDER.size(); i++) {
            assertEquals(points[i], new Tier("sword", Tier.ORDER.get(i), false).points());
            assertEquals(0, new Tier("sword", Tier.ORDER.get(i), true).points());
        }
        int[] thresholds = {0,10,20,40,80,180};
        String[] titles = {"Rookie","Combat Novice","Combat Cadet","Combat Specialist","Combat Ace","Combat Master"};
        for (int i = 0; i < thresholds.length; i++) {
            assertEquals(titles[i], new PlayerStats("Test", "ZA", thresholds[i], 0).combatTitle());
            if (i > 0) assertEquals(titles[i-1], new PlayerStats("Test", "ZA", thresholds[i]-1, 0).combatTitle());
        }
    }

    @Test void overallPositionsIgnoreWhetherRetiredTiersAreDisplayed() {
        var rankings = Rankings.parse("""
                {"players":[{"name":"Retired","tiers":[{"mode":"mace","level":"HT1","retired":true}]},
                  {"name":"Active","tiers":[{"mode":"sword","level":"LT5","retired":false}]}]}
                """);
        assertEquals(1, rankings.topTenPosition("Active"));
        assertEquals(2, rankings.topTenPosition("Retired"));
        assertTrue(rankings.select("Retired", "strongest", false).isEmpty());
        assertEquals(2, rankings.topTenPosition("Retired"));
    }
}
