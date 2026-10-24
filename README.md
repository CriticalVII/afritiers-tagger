<p align="center">
  <img src="src/main/resources/assets/afritiers_tagger/icon.png" width="96" alt="AfriTiers Tier Tagger">
</p>

# AfriTiers Tier Tagger

A client-side Fabric mod for **Minecraft Java 26.2**, powered by the [AfriTiers API](https://afritiers.co.za/api/players).

- Mode icons and coloured tiers on player nametags and the Tab list.
- A Players tab with username search, region, points, combat title and rankings.
- Optional gold `#1`–`#10` labels, with Tab alignment only for top-ten players.
- Mod Menu settings, a live preview and a rebindable **K** shortcut.
- Username-based cracked/offline matching, verified paid-account rename recovery and saved aliases.

## Install

Download [afritiers-tagger-1.5.0.jar](releases/afritiers-tagger-1.5.0.jar) or get it from [GitHub Releases](https://github.com/CriticalVII/afritiers-tagger/releases/latest).

Verify downloads with the SHA-256 value in [releases/SHA256SUMS.txt](releases/SHA256SUMS.txt).

| Requirement | Version |
| --- | --- |
| Minecraft Java | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.161.0+26.2 |
| Mod Menu, optional | 20.0.3 |

Install [Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.2), then put the mod JAR in your client instance's `mods` folder. Replace older copies. Servers and other players do not need the mod.

## Use

Press **K**, run `/afritiers config`, or open **Mods → AfriTiers Tier Tagger → Configure**. Select a mode or Strongest tier, adjust options, then Save. Top 10 rank is off by default; retired tiers are included by default.

The Players tab accepts a username with Search or Enter. Tier colours are gold for tier 1, silver for tier 2, bronze for tier 3, and gray for tiers 4–5. Nametags keep the `|` separator; Tab does not add one.

Cracked/offline accounts match by username. Paid rename recovery uses verified paid IDs from AfriTiers and the official current-name lookup. Server/offline UUIDs are never used for tier matching. Skin previews use an available online skin or a labelled default skin.

| Command | Action |
| --- | --- |
| `/afritiers config` | Open settings |
| `/afritiers refresh` | Request a refresh |
| `/afritiers status [username]` | Show connection or player details |
| `/afritiers alias set <current> <apiName>` | Save a known name change |
| `/afritiers alias remove <current>` | Remove an alias |
| `/afritiers alias list` | List aliases |

Settings are saved in `config/afritiers_tagger.json`. Rankings refresh automatically and depend on upstream API availability.

## Build

Use JDK 25 and run `./gradlew build`, or `.\gradlew.bat build` on Windows. The build writes the mod JAR to `build/libs/`.

The repository contains production source/assets, build files and the latest released JAR in `releases/`.

## License

All rights reserved. See [LICENSE](LICENSE).
