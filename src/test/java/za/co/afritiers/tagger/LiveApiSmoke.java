package za.co.afritiers.tagger;

import za.co.afritiers.tagger.network.HttpRankingSource;
import za.co.afritiers.tagger.network.HttpProfileLookup;

public final class LiveApiSmoke {
    public static void main(String[] args) throws Exception {
        try (var source = new HttpRankingSource()) {
            var rankings = source.fetch();
            System.out.println("LIVE_API_OK usernames=" + rankings.players().size() + " modes=" + rankings.modes()
                    + " stale=" + rankings.stale() + " ambiguous=" + rankings.ambiguousNames()
                    + " sourceUpdated=" + rankings.updatedAt());
            rankings.stats().values().stream().filter(stats -> stats.position() == 1).findFirst().ifPresent(stats ->
                    System.out.println("LIVE_STATS_OK first=" + stats.name() + " points=" + stats.points()
                            + " title=" + stats.combatTitle() + " region=" + stats.region()));
            if (args.length > 0) {
                try (var profiles = new HttpProfileLookup()) {
                    var profile = profiles.lookup(args[0]).orElseThrow();
                    String apiName = rankings.paidProfiles().get(profile.id());
                    if (apiName == null) throw new IllegalStateException("Official profile has no matching paid API record");
                    System.out.println("PAID_RENAME_OK current=" + profile.name() + " apiName=" + apiName
                            + " strongest=" + rankings.select(apiName, "strongest", true).orElseThrow().label());
                }
            }
        }
    }
}
