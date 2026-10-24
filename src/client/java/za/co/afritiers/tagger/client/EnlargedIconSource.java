package za.co.afritiers.tagger.client;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.PlainTextRenderable;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import za.co.afritiers.tagger.ranking.ModeIcon;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Give our mode sprites consistent line-fitting metrics, advance and clipping bounds. */
public final class EnlargedIconSource implements GlyphSource {
    public static final float SIZE = 13.0F;
    private static final float SCALE = SIZE / 8.0F;
    private final BakedGlyph glyph;

    public EnlargedIconSource(GlyphSource original) {
        this(original, null, null);
    }

    public EnlargedIconSource(GlyphSource original, TextureAtlasSprite tabSprite, float[] crop) {
        BakedGlyph base = original.getGlyph(ModeIcon.MACE.glyph().codePointAt(0));
        glyph = new BakedGlyph() {
            @Override public GlyphInfo info() { return GlyphInfo.simple(tabSprite == null ? SIZE : 10); }

            @Override public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor,
                                                               Style style, float boldOffset, float shadowOffset) {
                TextRenderable.Styled renderable = base.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset);
                if (!(renderable instanceof PlainTextRenderable sprite)) return renderable;
                return new EnlargedSprite(sprite, tabSprite, crop);
            }
        };
    }

    @Override public BakedGlyph getGlyph(int codepoint) { return glyph; }
    @Override public BakedGlyph getRandomGlyph(RandomSource random, int width) { return glyph; }

    private record EnlargedSprite(PlainTextRenderable original, TextureAtlasSprite tabSprite, float[] crop) implements PlainTextRenderable {
        @Override public float x() { return original.x(); }
        @Override public float y() { return original.y(); }
        @Override public int color() { return original.color(); }
        @Override public int shadowColor() { return original.shadowColor(); }
        @Override public float shadowOffset() { return original.shadowOffset() * SCALE; }
        @Override public Style style() { return original.style(); }
        @Override public float width() { return tabSprite == null ? SIZE : 8.5F; }
        @Override public float height() { return width(); }
        @Override public float ascent() { return tabSprite == null ? 9.5F : 7.0F; }
        @Override public RenderType renderType(Font.DisplayMode displayMode) { return original.renderType(displayMode); }
        @Override public GpuTextureView textureView() { return original.textureView(); }
        @Override public RenderPipeline guiPipeline() { return original.guiPipeline(); }

        @Override public void renderSprite(Matrix4fc pose, VertexConsumer buffer, int light,
                                           float offsetX, float offsetY, float z, int color) {
            if (tabSprite != null) {
                float x0 = left() + offsetX, x1 = right() + offsetX, y0 = top() + offsetY, y1 = bottom() + offsetY;
                float u0 = tabSprite.getU(crop[0]), u1 = tabSprite.getU(crop[2]);
                float v0 = tabSprite.getV(crop[1]), v1 = tabSprite.getV(crop[3]);
                buffer.addVertex(pose, x0, y0, z).setUv(u0, v0).setColor(color).setLight(light);
                buffer.addVertex(pose, x0, y1, z).setUv(u0, v1).setColor(color).setLight(light);
                buffer.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(color).setLight(light);
                buffer.addVertex(pose, x1, y0, z).setUv(u1, v0).setColor(color).setLight(light);
                return;
            }
            // Nameplate icons keep their artwork padding and centred baseline.
            float shiftY = 7.0F - ascent() - SCALE * (7.0F - original.ascent());
            Matrix4f enlarged = new Matrix4f(pose).translate(x(), y() + shiftY, 0)
                    .scale(SCALE, SCALE, 1).translate(-x(), -y(), 0);
            original.renderSprite(enlarged, buffer, light, offsetX / SCALE, offsetY / SCALE, z, color);
        }
    }
}
