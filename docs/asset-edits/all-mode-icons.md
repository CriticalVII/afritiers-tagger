# Mode icon remakes — 1.2.0

All 17 icons were edited individually using the built-in imagegen tool, with genuine alpha transparency. Each original 72×72 PNG was the edit target. The selected generated PNGs are copied unchanged into `src/main/resources/assets/afritiers_tagger/textures/gui/sprites/mode/remade/`. No raster resampling or manual pixel editing is used. Runtime text metrics render each as a 10px glyph, with white tint and no added shadow.

The original source images remain in `src/main/resources/assets/afritiers_tagger/textures/mode/`. See `docs/MODE_ICONS.md` for API mode aliases. The file names below correspond directly to both the originals and remakes.

Each request used `transparent_background=true`, the matching original PNG as its sole reference, and this prompt with the indicated name and description substituted:

> Use case: precise-object-edit. Edit target: attached {name} Minecraft mode icon ({description}). Remake this single icon as a crisp simplified game inventory sprite, keeping its identity, colors, orientation and overall silhouette. Remove ALL black/dark external outline strokes: the edge must meet transparent pixels directly in the object's own fill color. Internal subtle shading is allowed, but no dark contour or drop shadow. Genuine transparent background. Centered square with 12% clear padding, readable at only 10 pixels high. No text, no frame, no border, no glow, no checkerboard. One isolated icon only.

| Name | Description |
| --- | --- |
| axe | blue battle axe diagonal, handle lower left and head upper right |
| bed-pvp | red bed with white pillow and beige legs |
| cart | gray minecart with orange-red load |
| creeper | green spotted creeper spawn egg |
| diamond-vanilla | cyan diamond crystal with pale facets |
| diapot | round pink potion bottle with pale glass neck |
| diasmp | lavender apple or fruit with pale leaf |
| mace | gray-purple mace head and lavender diagonal handle |
| nethop | purple netherite helmet |
| og-vanilla | golden apple with green leaf and brown stem |
| overall | gold trophy cup |
| smp | teal ender pearl |
| spear-mace | crossed gray mace and cyan spear |
| speed | cyan speed potion in tilted glass bottle |
| sword | cyan blue sword diagonally pointing upper right |
| uhc | red heart |
| vanilla | purple crystal with pale facets |

The first DiaSMP candidate was rejected because its smooth illustration style did not match the other sprites. The final selected DiaSMP replaces it, using the first candidate as edit target and the remade golden apple as style reference. Final built-in edit prompt:

> Use case: style-transfer. Image 1 is the edit target: DiaSMP lavender-purple fruit with two pale leaves. Image 2 is the strict style reference: blocky golden apple from this same Minecraft icon set. Remake ONLY the DiaSMP icon to match image 2's exact crisp pixel-block style: coarse square pixel grid, stair-stepped silhouette, square leaf pixels and flat stepped shading bands. Preserve DiaSMP's purple fruit identity, lavender palette and two pale lilac leaves angled toward upper left; same centered scale with generous transparent padding. NO smooth curves, NO vector illustration, NO smooth gradients, NO soft painted surface. No dark perimeter outline, no black stroke, no shadow, no glow, no text, no frame. Object's colored fill should reach the transparent edge directly. One isolated DiaSMP icon only, genuinely transparent background, square image, readable at 10px high.
