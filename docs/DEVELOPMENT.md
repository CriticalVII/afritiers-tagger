# Development

## Requirements

JDK 25. The included wrapper downloads Gradle 9.6.1 with a pinned SHA-256 checksum. Minecraft, Fabric and Mod Menu versions are pinned in `gradle.properties`.

## Build and test

```sh
./gradlew build
./gradlew runClient
./gradlew runProductionClientGameTest
./gradlew runProductionClientGameTest -PwithoutModMenu
./gradlew releaseZip
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

| Task | Result |
| --- | --- |
| `build` | Compile, create JARs and run deterministic JUnit tests |
| `runClient` | Launch a development client, using `run/` |
| `runClientGameTest` | Run native client tests on the development classpath |
| `runProductionClientGameTest` | Run the actual packaged mod JAR in a separate Minecraft client |
| `-PwithoutModMenu` | Omit Mod Menu from either client test runtime |
| `liveApiSmoke` | Read the live API once and report normalized counts |
| `liveApiSmoke -PprofileName=PlayerName` | Also check a current paid username against API paid identity data |
| `releaseZip` | Package the installable JAR and guides in `build/distributions/` |

The regular JAR is in `build/libs/`. Do not install sources or gametest JARs.

## Test boundaries

Unit tests use synthetic records and local HTTP servers; they do not require the live API. Client tests use separate fixtures and fake transports in a real generated Minecraft world. Test classes and fixtures are excluded from the installable mod. Client tests require a working graphics environment.

Reports are in `build/reports/tests/test/`. Production client screenshots are in `build/run/productionClientGameTest/screenshots/`. Selected previews in `docs/screenshots/` are captured from these native tests.

GitHub Actions runs `build` on pushes to main and pull requests, using Java 25. It uploads the installable JAR and test report. Native game tests remain a separate local check; CI does not claim to run the graphics suite.

## Releasing

1. Update `mod_version`, the changelog, installation instructions and validation notes.
2. Run `build` and production client checks with and without Mod Menu.
3. Review the packaged JAR and screenshots, then run `releaseZip`.
4. Publish the regular JAR and release ZIP on GitHub Releases. Keep development/test JARs out of the player download.

The repository excludes Gradle caches, local instances, logs, credentials and local planning notes. Use GitHub's no-reply email for public commits if personal email privacy is desired. Existing rights-reserved licensing remains in force; this guide does not grant redistribution or relicensing rights.
