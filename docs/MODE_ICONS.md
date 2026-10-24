# Mode icons

Source: the supplied `gamemode-icons-transparent` set. All 17 original 72×72 transparent PNGs were copied unchanged into `src/main/resources/assets/afritiers_tagger/textures/mode/`; their file hashes match the originals.

In 1.2.0, all 17 displayed icons are separate transparent remakes without dark external contours, stored in `src/main/resources/assets/afritiers_tagger/textures/gui/sprites/mode/remade/`. Minecraft's native GUI-atlas sprite font supports the generated image resolutions without altering the saved PNGs. The original PNGs and legacy bitmap font remain bundled for provenance.

Glyphs use white tint with no added shadow. The surrounding tier text uses the normal Minecraft font and the configured tier colour. The tag layout is `<icon> <tier> | <username>` with no bracket frame.

See [the complete icon remake prompt set](asset-edits/all-mode-icons.md) for the current assets and prompts. [The earlier mace edit](asset-edits/mace-outline-removal.md) documents the 1.1.1 version.

In 1.4.0, nameplate sprites use 13px width/height/advance with a 9.5px ascent. Tab artwork uses an 8.5px square, a 7px ascent and a 10px advance, so it stays within each 9px Tab row. Read-only UV windows in `assets/afritiers_tagger/mode_bounds.json` remove transparent margins during Tab rendering, preserving the PNGs. World text uses atlas-sprite fonts; Tab uses private resource-font markers resolved to the same native atlas. Vanilla fonts retain their metrics, and resource reloads replace cached wrappers. The approved size-review screenshots are in `docs/screenshots/`.

Nameplates keep `<icon> <tier> | <username>`; Tab uses `<icon> <tier> <username>` without an added pipe.

| AfriTiers mode ID | Supplied icon |
| --- | --- |
| `axe` | `axe.png` |
| `bed` | `bed-pvp.png` |
| `cart` | `cart.png` |
| `creeper` | `creeper.png` |
| `diavanilla` | `diamond-vanilla.png` |
| `diapot` | `diapot.png` |
| `diasmp` | `diasmp.png` |
| `mace` | `mace.png` |
| `nethop` | `nethop.png` |
| `ogv` | `og-vanilla.png` |
| `overall`, strongest-mode selector, unknown modes | `overall.png` |
| `smp` | `smp.png` |
| `spear` | `spear-mace.png` |
| `speed` | `speed.png` |
| `sword` | `sword.png` |
| `uhc` | `uhc.png` |
| `vanilla` | `vanilla.png` |

Filename-style aliases such as `bed-pvp`, `diamond-vanilla`, `og-vanilla`, and `spear-mace` are supported too. Strongest-tier player tags use the actual ranking's icon, rather than the Overall selector icon. Human-readable mode and rank text remains available in the tag's hover component.
