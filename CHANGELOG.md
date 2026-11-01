# Changelog

## 1.7.1 — 2026-11-01

Changes since 1.6.0.

### Added

- `/tiers <player>` opens Profiles with that player already searched. `/tiers` opens your own account.
- A Waitlists tab with per-mode subscriptions, All/None controls, a notification pause toggle and an optional sound.
- In-game chat alerts when a watched waitlist opens, with a clickable Discord Join link when available.
- Automatic queue checks about every 30 seconds while watching in game or browsing Waitlists. Alerts are deduplicated per opening/event; retries respect service cooldowns.

### Changed

- Profiles displays your own account with an empty search box. My profile returns to your account and clears the search box.
- Nametags and the preview use spaces between the icon, tier and username, without an added pipe separator.

### Fixed

- Tiers refresh after world/player replacements, respawns and server teleports, including hub-to-Practice transfers that retain the same connection.
- Valid tiers remain visible while transfer refreshes wait for the API cooldown. Rapid transfers share one pending refresh and reuse an in-flight ranking request.
- Changing settings in the Settings tab preserves waitlist subscriptions.
- Temporary Windows config-file locks are retried without deleting the original or weakening atomic saves.

Requires Minecraft Java 26.2, Java 25, Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.2. Mod Menu 20.0.3 is optional.

[Full comparison](https://github.com/CriticalVII/afritiers-tagger/compare/v1.6.0...v1.7.1)
