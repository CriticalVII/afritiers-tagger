# Changelog

## 1.5.0 — 2026-10-24

- Add a Players tab with username search, region, active points, combat title, overall position and scrollable mode tiers in the existing native GUI style.
- Preserve unsaved Settings drafts while switching tabs; support Search/Enter, invalid input, not-found results and API errors.
- Search from Mod Menu without joining a world, including while tier tags are disabled, using a temporary bounded background session.
- Reuse paid rename recovery and saved username aliases for searches. Use online skins when available and a labelled default preview otherwise.
- Add an optional Top 10 rank setting, off by default, for overall `#1`–`#10` prefixes in HT1 gold on Tab and nametags. Places below tenth get no prefix.
- Align rank, icon, tier and display-name columns only on top-ten Tab rows, with exact pixel spacing and support for wider retired labels. Other rows keep compact spacing. Nametag spacing and native Tab row heights are retained.
- Compute positions once per API snapshot from active-tier points and alphabetical name ties, independently of selected-mode and retired-display settings.
- Add stats/placement boundary tests and real Minecraft search/top-ten UI checks.

## 1.4.0 — 2026-10-24

- Add an emerald HT1 tag icon with gold lettering and Minecraft-style pixel edges for Mod Menu.
- Recover renamed paid players through the official current-name profile lookup and paid IDs in AfriTiers data. Server/offline UUIDs remain unused for tier matching.
- Preserve username-only cracked-account matching, and add persistent `/afritiers alias set`, `remove`, and `list` commands for explicit name changes.
- Exclude duplicate/conflicting paid IDs and reject an old paid username when it resolves to a different account.
- Run paid-name lookups on a throttled background worker with bounded responses, deadlines, session caching and Retry-After handling.
- Enlarge nameplate icons to 13px and render Tab artwork tightly inside 9px rows, using UV windows without modifying the PNGs.
- Remove the tier-prefix pipe from Tab while retaining it on nametags.
- Add real Tab/nameplate size previews, mixed-account recovery tests, alias persistence tests and a verified live paid-account rename check.

## 1.3.1 — 2026-10-23

- Enable retired tiers by default for new configurations and Reset defaults. Existing saved preferences are retained.

## 1.3.0 — 2026-10-23

- Redesign the native configuration GUI as a compact beveled panel with Minecraft's pixel font and restrained green accents.
- Add a centered live nametag preview with previous/next controls for all ten HT/LT tiers.
- Group display and appearance settings; use descriptive rows on tall screens and compact rows at smaller GUI scales.
- Keep preview and Save/Cancel fixed while controls scroll on narrow screens.
- Move timestamps, freshness and parsing diagnostics into a Details view that preserves the unsaved draft.
- Highlight colour errors immediately and disable Save until corrected.
- Retain native keyboard input, focus outlines, narration and click sounds. Hover fades respect Minecraft's Screen Effects setting.

## 1.2.1 — 2026-10-23

- Increase all mode icons from 10px to 11px, with matching text spacing and clipping bounds.
- Adjust ascent to 8.5px to preserve vertical centering beside the tier and name.

## 1.2.0 — 2026-10-23

- Remake all 17 mode icons with transparent backgrounds and no dark external outline.
- Reduce mode glyphs from 12px to 10px with consistent spacing, clipping bounds and baseline alignment.
- Apply ordinary player tags after renderer state extraction and finalization.
- Support server-generated armor stand and text-display name labels by exact nearby player username, preserving entity data and existing visibility rules.
- Prevent duplicate tags on reused text components.
- Include nameplate/Tab toggles and mode in status, and add `/afritiers status <username>` diagnostics.
- Keep username-only API matching, tier colours, and Mod Menu integration.

## 1.1.2 — 2026-10-23

- Enlarge the outline-free mace icon from an 8px to a 12px sprite glyph, including spacing and clipping bounds.
- Increase the other mode icons from 10px to 12px font sizing and align their baselines with tier text.
- Scope sprite scaling to the mod's mace icon; leave vanilla sprites at their original size and rebuild the icon wrapper after resource reloads.
- Preserve the existing artwork, medal colours, and `<icon> <tier> | <player name>` layout.

## 1.1.1 — 2026-10-23

- Remove the dark rim baked into the mace artwork by replacing its displayed asset with an outline-free transparent edit.
- Load the edited mace through Minecraft's native sprite font; retain the original source PNG and all other supplied mode icons.
- Preserve the medal colours and `<icon> <tier> | <player name>` format.

## 1.1.0 — 2026-10-23

- Bundle all 17 supplied transparent mode icons unchanged using a native bitmap font.
- Display tags as `<mode icon> <tier> | <player name>` in nameplates and Tab, without brackets or an added icon shadow/outline.
- Use gold for tier 1, silver for tier 2, bronze for tier 3, and gray for tiers 4–5, for both HT and LT ranks.
- Preserve the original icon colours independently of tier text or custom text-colour overrides.
- Show mode icons in the mode selector and preview; map website IDs such as `bed`, `diavanilla`, `spear`, and `ogv` to the supplied filenames.
- Support hyphenated mode IDs and a general-purpose icon for new modes.
- Add asset, mode-alias, palette, text-style, and real-client screenshot checks.

## 1.0.0 — 2026-10-23

- Target Minecraft Java 26.2 with Fabric and Java 25.
- Add AfriTiers tier prefixes to nameplates and the Tab list.
- Match API usernames case-insensitively for paid and cracked/offline players; ignore UUIDs.
- Add mode selection, strongest-tier selection, retired-tier display, and custom colours.
- Add native scrollable settings, optional Mod Menu integration, a K shortcut, and local commands.
- Add asynchronous API fetching, bounded requests, cooldowns, retry backoff, stale/error status, and duplicate handling.
- Preserve server name formatting and vanilla nameplate visibility.
- Add configuration recovery, automated logic/network tests, and real-client integration tests.
