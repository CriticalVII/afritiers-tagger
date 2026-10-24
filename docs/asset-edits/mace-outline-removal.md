# Mace outline removal

The dark contour was part of the supplied mace PNG's artwork. Removing tag brackets or disabling Minecraft text shadows did not remove those pixels.

Edited with the built-in imagegen tool, preserving alpha transparency. The source icon remains in `textures/mode/mace.png` for provenance. The displayed replacement is `src/main/resources/assets/afritiers_tagger/textures/gui/sprites/mode/mace_outline_free.png`, loaded as a native GUI-atlas sprite so its generated resolution is supported without changing the image file. Other supplied mode icons are unchanged.

Prompt:

> Use case: precise-object-edit. Edit target: the supplied 72 x 72 transparent Minecraft mace mode icon. Remove ONLY the nearly black dark outline/rim along the outer silhouette of the mace head and handle. Keep the exact same original small icon design: a dark gray-purple blocky mace head at upper right, lavender-gray handle extending diagonally to lower left; preserve the silhouette, proportions, pose, placement, internal face shading, muted colors, and transparent padding. Replace the dark perimeter stroke with the adjacent matching face/handle colors so the shaded icon reaches its edges WITHOUT a drawn border. Do not replace it with a different mace or add detail. No black outline, no dark stroke, no drop shadow, no glow, no background, no checkerboard, no text. Maintain genuine alpha transparency. Target a tiny crisp game UI sprite readable at 10 pixels high; if supported return the original 72x72 dimensions. This is a minimal outline-removal edit, not a redesign.
