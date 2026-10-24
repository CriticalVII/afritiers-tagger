package za.co.afritiers.tagger.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.afritiers.tagger.client.EnlargedIconSource;
import za.co.afritiers.tagger.client.ModeIcons;
import za.co.afritiers.tagger.client.ModeIconBounds;

@Mixin(Font.class)
public abstract class ModeIconFontMixin {
    @Shadow @Final private Font.Provider provider;
    @Unique private final java.util.Map<FontDescription, GlyphSource[]> afritiers$icons = new java.util.HashMap<>();
    @Inject(method = "getGlyphSource", at = @At("RETURN"), cancellable = true)
    private void afritiers$sizeOwnIcon(FontDescription font, CallbackInfoReturnable<GlyphSource> callback) {
        var icon = ModeIcons.iconForFont(font);
        if (icon == null) return;
        boolean tab = font instanceof FontDescription.Resource;
        GlyphSource original = tab ? provider.glyphs(ModeIcons.sprite(icon)) : callback.getReturnValue();
        GlyphSource[] cached = afritiers$icons.get(font);
        if (cached == null || cached[0] != original) {
            var sprite = tab ? Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.GUI)
                    .getSprite(ModeIcons.sprite(icon).spriteId()) : null;
            cached = new GlyphSource[] {original, new EnlargedIconSource(original, sprite, tab ? ModeIconBounds.forIcon(icon) : null)};
            afritiers$icons.put(font, cached);
        }
        callback.setReturnValue(cached[1]);
    }
}
