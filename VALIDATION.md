# Validation — 1.5.0

Checked on 24 October 2026 on Windows with Microsoft OpenJDK 25.0.4.1, NVIDIA GeForce RTX 3060 and Minecraft's OpenGL backend.

## Pinned versions

| Component | Version |
| --- | --- |
| Minecraft Java | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Mod Menu | 20.0.3, optional |
| Fabric Loom | 1.17.21 |
| Gradle wrapper | 9.6.1, distribution checksum pinned |
| Java | 25 |

## Automated and live checks

- `gradlew.bat build`: compilation, packaged regular JAR, source JAR and **54 JUnit tests**, zero failures/errors.
- Unit coverage includes username matching, malformed/ambiguous records, rank selection and retired tiers, account-type filtering, conflicting and duplicate paid identities, explicit aliases, configuration persistence, HTTP status and body validation, bounded responses, deadlines, refresh/cancellation, profile cache expiry, queue deduplication and Retry-After handling. Presentation checks cover all ten rank colours and all 17 mode assets.
- New deterministic checks cover active-only points, combat-title thresholds, untrusted API totals, region fallback, alphabetical placement ties, the tenth/eleventh boundary and ambiguous/empty profiles. Configuration checks persist the new toggle and confirm its default is off.
- `gradlew.bat liveApiSmoke -PprofileName=SadVerity67`: live HTTPS requests to AfriTiers and the official Minecraft profile service succeeded. At the time of the check, AfriTiers returned 46 normalized usernames and `stale=false`. The current name **SadVerity67** resolved to the paid profile attached to API name **CriticalVII**, selecting **Spear Mace LT3**. Counts are observations, not fixed expectations. No raw API response was saved.
- That paid-rename live result was recorded earlier on the same day for the unchanged official lookup transport. A subsequent stats check again loaded 46 usernames and selected AngryVerity67 as first with 17 points, Combat Novice and region AF; its optional official-name request hit the five-second timeout. The deterministic client suite verifies paid recovery independently of remote availability.
- `gradlew.bat runProductionClientGameTest`: the actual packaged regular JAR runs in a separate Minecraft client with Fabric API and Mod Menu.
- `gradlew.bat runProductionClientGameTest -PwithoutModMenu`: the packaged JAR passes the same applicable suite with Mod Menu absent.

## Production client coverage

The suite verifies:

1. Mod Menu discovers the configuration entrypoint. The metadata references the bundled HT1 icon, and the real Mods screen renders it in the filtered AfriTiers list and details view.
2. The native settings GUI stays within bounds at GUI scales 2 and 3 and at the 320×240 fallback. Save/Cancel, invalid colour, preview cycling, scrolling layout and Details/Back draft preservation work.
3. Cracked records match by case-insensitive username despite deliberately unrelated UUID fields. An explicit saved cracked-name alias recovers a tier, and removal takes effect.
4. A renamed paid player recovers tiers in both Tab and an actual player nametag through a verified paid ID, independently of the test player's unrelated server UUID. A different paid account using the old username receives no inherited tier.
5. Tab displays `<icon> <tier> <server display name>` with no added pipe. Nametags display `<icon> <tier> | <server display name>`. Nicknames and team prefixes retain their formatting. Unranked players have no added label or indentation.
6. Nameplate tags survive renderer extraction/finalization and follow hidden-team rules. Turning nameplates off retains enabled Tab tags.
7. K and `/afritiers config` open the settings screen.
8. All 17 mode glyphs reserve 13px for nameplates. Tab glyphs reserve 10px and render within the native 9px row, with an 8.5px square UV crop. These checks repeat after resource reload; vanilla sprite glyphs stay at 8px.
9. The ten ranks retain gold/silver/bronze/gray colours. Icon colours are independent of text colours and have no added shadow.
10. Nearby visible armor-stand names and text displays containing an exact player username are decorated. Hidden labels, distant holograms, partial matches and unrelated labels are excluded. Reused tagged components are not tagged twice; server entity text remains unchanged.
11. The Players tab searches case-insensitively, rejects empty/invalid usernames, shows region, points, combat title, overall placement and retired mode tiers, handles not-found searches without retaining another profile, supports Enter and scrolls long profiles. Paid name recovery is exercised in the tab.
12. Search works before joining a world while tier tags are disabled. The deterministic client suite uses fake transports for its ranking and paid-name lookups.
13. The Players tab stays in bounds at GUI scales 2 and 3 and at 320×240. Returning to Settings preserves unsaved colour edits.
14. Saving Top 10 rank enables `#1`–`#10` prefixes in native Tab and player nametag screenshots. All ten positions use HT1 gold (`FFD700`); eleventh place is omitted. Overall rank remains independent of the selected mode, and rank-only labels remain idempotent when no tier matches the chosen mode.
15. Only top-ten native Tab rows align their columns, including single/double-digit ranks and retired tiers. The production test measures identical prefix widths for these rows and confirms other rows keep their original compact spacing. It verifies exact invisible advances before and after resource reload, prevents repeated indentation, and checks that disabling Top 10 rank disables column alignment. Scope ends after the Tab render; nametag spacing is retained.

Native Tab and nametag screenshots document the final icon sizes. Earlier sizing comparisons are preserved in `docs/screenshots/` alongside the current release previews.

The original mode PNGs remain unchanged. Their remakes preserve imagegen alpha; larger Tab artwork uses UV coordinates instead of raster editing. The Mod Menu icon was generated separately from a design reference. Prompts are documented in `docs/asset-edits/`.

Synthetic fixtures, fake transports and test worlds belong only to the separate gametest source set/JAR; none are included in the installable JAR.

## Compatibility limits

- OpenGL was tested. Vulkan, Sodium, Iris and other Tab/nameplate replacement mods were not tested.
- Offline account and server-generated label behaviour was checked in a real integrated world with synthetic records. A separate offline-mode dedicated server was not tested. Arbitrary nickname-only, multiline, distant or packet-only label systems are not covered.
- Automatic rename recovery requires a usable paid profile ID in AfriTiers data and an official response for the current username. Cracked records retain username-only matching; explicit aliases cover known cracked name changes. A paid display name alone cannot prove ownership on an offline-mode server.
- Official profile lookups run in a bounded background queue with a one-second gap, five-minute session caching and backoff. Lookups may take time to complete on a busy server. They send current usernames to the official Minecraft service and do not use server UUIDs.
- Search shows an available online skin or an explicitly labelled native default skin. It does not download arbitrary API skin URLs.
- Positions use active current-tier points and alphabetical API-name ties, computed from valid unambiguous records in the current snapshot. Names and positions can lag upstream changes until refreshed.
- The current public full-array AfriTiers response is supported. Schema changes may require an update. API polling defaults to five minutes plus jitter and honours Retry-After.
- No persistent API/profile response cache is used. Failed ranking refreshes clear tags until data is available again.
- This release supports Minecraft 26.2 on Fabric.

## Evidence

- Unit report: `build/reports/tests/test/index.html`.
- Live lookup: `build/paid-rename-live-smoke.log`.
- Live stats: `build/player-stats-live-smoke.log`.
- Production with Mod Menu, search and placements: `build/player-search-client-test.log`.
- Production without Mod Menu: `build/player-search-client-test-without-modmenu.log`.
- Screenshots: `docs/screenshots/`.
- Installable JAR: `build/libs/afritiers-tagger-1.5.0.jar`.
- Release bundle: `build/distributions/afritiers-tagger-1.5.0-mc26.2.zip`.
