package za.co.afritiers.tagger.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;

/** Some servers hide the vanilla name and attach an armor stand or text display instead. */
public final class ServerNameLabels {
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");
    private ServerNameLabels() { }

    public static Component decorate(Entity label, Component text) {
        if (!(label instanceof ArmorStand || label instanceof Display.TextDisplay)) return text;
        AfriTiersClient mod = AfriTiersClient.instance();
        if (mod == null || !mod.activeConfig().enabled || !mod.activeConfig().nameplates) return text;
        String visible = ChatFormatting.stripFormatting(text.getString());
        // Restrict this to single-line, short labels rather than general server holograms.
        if (visible == null || visible.length() > 64 || visible.contains("\n")) return text;
        var names = new HashSet<String>();
        var words = WORD.matcher(visible);
        while (words.find()) names.add(words.group().toLowerCase(java.util.Locale.ROOT));
        String match = null;
        for (var player : label.level().players()) {
            String username = player.getGameProfile().name();
            if (!names.contains(username.toLowerCase(java.util.Locale.ROOT))) continue;
            double dx = player.getX() - label.getX(), dz = player.getZ() - label.getZ();
            double dy = label.getY() - player.getY();
            if (dx * dx + dz * dz > 1.0 || dy < 0 || dy > 3.5) continue;
            if (match != null) return text; // Never guess between multiple players.
            match = username;
        }
        return match == null ? text : TagFormatter.decorate(match, text, false);
    }

    public static void decorateTextDisplay(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Display.TextDisplay) || !(state instanceof TextDisplayEntityRenderState display)
                || display.textRenderState == null || display.cachedInfo == null) return;
        Component original = display.textRenderState.text();
        Component decorated = decorate(entity, original);
        if (decorated == original) return;
        var font = Minecraft.getInstance().font;
        int width = Math.max(display.textRenderState.lineWidth(), font.width(decorated));
        var lines = new ArrayList<Display.TextDisplay.CachedLine>();
        int maxWidth = 0;
        for (var line : font.split(decorated, width)) {
            int lineWidth = font.width(line);
            maxWidth = Math.max(maxWidth, lineWidth);
            lines.add(new Display.TextDisplay.CachedLine(line, lineWidth));
        }
        // Change the render snapshot only; leave server entity data and its text cache untouched.
        display.cachedInfo = new Display.TextDisplay.CachedInfo(lines, maxWidth);
    }
}
