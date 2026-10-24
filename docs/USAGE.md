# Usage reference

## Settings

Open settings with K, `/afritiers config`, or Mod Menu. K can be rebound in Minecraft Controls.

- Enable or disable nametags and Tab independently. Vanilla visibility and team rules still apply.
- Select a mode, or Strongest tier to choose each player's best eligible tier.
- Retired tiers are enabled by default. Existing saved preferences are preserved.
- Top 10 rank is off by default. Enabling it adds gold `#1`–`#10` labels and aligns only those Tab rows.
- Leave the colour override blank for tier colours, or enter six hexadecimal digits. This changes tier text without tinting icons or ranking labels.
- Save applies and persists the draft. Cancel/Escape discards it. Reset changes the draft until saved.
- Details and Players preserve unsaved settings when you return. Refresh uses saved settings.

The native interface adapts to GUI scale and scrolls when necessary. Hover effects follow Minecraft's Screen Effects setting.

## Player search

The Players tab accepts a full Minecraft username, case-insensitively. Search or Enter submits the query. It displays region, active points, combat title, overall position and every valid recorded tier. Retired entries are labelled and earn zero points. Hover a row for its full text.

Search works in-game, before joining a world, and while tier tags are disabled. In-game it reuses loaded rankings when available. Otherwise it starts a temporary background session that ends when the tab closes. An available online skin is used for the preview; other profiles show a labelled native default skin. Arbitrary API skin URLs are not downloaded.

## Matching and changed names

API usernames match case-insensitively. Server nicknames are preserved for display and are not used as identity keys. Cracked/offline accounts remain username-only. Paid accounts can recover through the official current-name lookup and an explicitly paid profile ID in AfriTiers data. Server/offline UUIDs are never used to match ranking records.

Ambiguous usernames and conflicting paid IDs are excluded from automatic recovery. If a different paid account takes an old username, it does not inherit the previous paid account's tiers once verified.

For known cracked name changes or missing paid identity data:

```text
/afritiers alias set NewPlayer OldApiName
/afritiers alias list
/afritiers alias remove NewPlayer
```

Aliases are saved, case-insensitive, one-hop mappings to an existing API username. Direct cracked-name matches take priority. Reset clears aliases when saved. `/afritiers status NewPlayer` reports the resolved API name and selected tier.

## Points and placements

Overall positions follow active-tier points descending, then API username alphabetically. They are computed once per normalized snapshot and do not change when the display mode or retired-tier setting changes. Records without valid tiers are unranked; malformed or ambiguous records are excluded by the parser.

| Tier | HT points | LT points | Colour |
| --- | --- | --- | --- |
| 1 | 60 | 45 | Gold · `FFD700` |
| 2 | 30 | 20 | Silver · `C0C0C0` |
| 3 | 10 | 6 | Bronze · `CD7F32` |
| 4 | 4 | 3 | Gray · `AAAAAA` |
| 5 | 2 | 1 | Gray · `AAAAAA` |

Combat titles start at these point totals: Rookie 0, Combat Novice 10, Combat Cadet 20, Combat Specialist 40, Combat Ace 80 and Combat Master 180. Retired tiers and historical peaks contribute no points. Rules follow the [AfriTiers ranking script](https://afritiers.co.za/js/rankings-r6.js?v=65-active-points).

## Configuration file

Settings are stored in the Minecraft instance's `config/afritiers_tagger.json`:

```json
{
  "enabled": true,
  "nameplates": true,
  "tabList": true,
  "includeRetired": true,
  "showTopTen": false,
  "mode": "strongest",
  "customColour": "",
  "usernameAliases": {}
}
```

Malformed settings are backed up with an `.invalid-<timestamp>` suffix before defaults load. Saving reports an error if the backup cannot be made.

## Networking and privacy

The mod reads the [public players endpoint](https://afritiers.co.za/api/players) without sending player lists, credentials or Discord/account-link data to AfriTiers. Polling occurs on connection and every 5–5.5 minutes while enabled, or while a temporary Players session is open. Manual refresh has a 30-second cooldown. Reconnects preserve cooldowns; failures use exponential backoff and respect Retry-After.

Networking and parsing run off the render thread. Ranking requests have a ten-second total deadline and a 4 MiB response limit. Responses are not written to disk. Failed refreshes clear ranking labels until data is available again. Details reports source freshness and connection diagnostics.

Paid-name lookups send individual usernames to `api.minecraftservices.com` without credentials. They use a separate worker, a one-second gap, a five-second deadline, a 16 KiB limit, five-minute session caching and Retry-After/backoff. They pause when disconnected/disabled unless Players is open for an explicit search. Direct cracked-name matches do not use paid profile IDs.

## Server labels

Normal player nametags and nearby visible armor-stand/text-display labels containing one exact player username are supported. Hidden labels, partial matches and distant holograms are excluded. Nickname-only, multiline or packet-only systems may need separate compatibility work. See [validation](../VALIDATION.md) for tested behaviour.
