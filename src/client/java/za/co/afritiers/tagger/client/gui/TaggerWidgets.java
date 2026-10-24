package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pixel-aligned native widgets retain vanilla input, focus, narration and click sounds. */
final class TaggerWidgets {
    static final int TEXT = 0xFFE8ECE9, MUTED = 0xFF9EA9A5, GREEN = 0xFFA0C5A1, LINE = 0xFF353D3F;
    private TaggerWidgets() { }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x60000000);
        g.fill(x, y, x + w, y + h, 0xFF101315);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF5C6564);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF121719);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF202629);
        g.fill(x + 12, y + 2, x + 64, y + 3, 0xFF8AAE8E);
    }

    static Action action(String text, int width, boolean primary, Button.OnPress action) {
        return new Action(text, width, 20, primary, action);
    }

    static class Action extends Button {
        private final boolean primary;
        private float hover;
        private long lastFrame;
        Action(String text, int width, int height, boolean primary, OnPress action) {
            super(0, 0, width, height, Component.literal(text), action, DEFAULT_NARRATION);
            this.primary = primary;
        }
        final float hoverAmount() {
            long now = System.nanoTime();
            float target = isHoveredOrFocused() && active ? 1 : 0;
            if (Minecraft.getInstance().options.screenEffectScale().get() == 0 || lastFrame == 0) hover = target;
            else hover += (target - hover) * Math.min(1, (now - lastFrame) / 100_000_000F);
            lastFrame = now;
            return hover;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            float over = hoverAmount();
            int fill = primary ? mix(0xFF365740, 0xFF487653, over) : mix(0xFF30383B, 0xFF444F52, over);
            if (!active) fill = 0xFF272D30;
            g.fill(getX(), getY(), getRight(), getBottom(), 0xFF111719);
            g.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, fill);
            g.fill(getX() + 1, getY() + 1, getRight() - 1, getY() + 2, primary ? 0xFF729678 : 0xFF687370);
            if (isFocused()) g.outline(getX(), getY(), width, height, GREEN);
            g.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, active ? TEXT : MUTED);
        }
    }

    static final class Toggle extends Action {
        private final String label, description;
        private final BooleanSupplier value;
        Toggle(String label, String description, int width, boolean compact, BooleanSupplier value, Consumer<Boolean> change) {
            super(label + ": " + (value.getAsBoolean() ? "ON" : "OFF"), width, compact ? 26 : 38, false, button -> {
                change.accept(!value.getAsBoolean());
                button.setMessage(Component.literal(label + ": " + (value.getAsBoolean() ? "ON" : "OFF")));
            });
            this.label = label; this.description = description; this.value = value;
            setTooltip(Tooltip.create(Component.literal(label + ": " + description)));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            row(g, this, hoverAmount());
            var font = Minecraft.getInstance().font;
            g.text(font, font.plainSubstrByWidth(label, width - 48), getX() + 8, getY() + (height < 38 ? 9 : 7), TEXT, false);
            if (height >= 38) g.text(font, font.plainSubstrByWidth(description, width - 16), getX() + 8, getY() + 23, MUTED, false);
            int right = getRight() - 7;
            boolean on = value.getAsBoolean();
            int chipY = getY() + (height < 38 ? 7 : 5);
            g.fill(right - 27, chipY, right, chipY + 13, on ? 0xFF3D5E46 : 0xFF3B4143);
            g.fill(right - 27, chipY, right, chipY + 1, on ? 0xFF709578 : 0xFF596160);
            g.centeredText(font, on ? "ON" : "OFF", right - 13, chipY + 3, on ? 0xFFDAEBDD : 0xFFB3BCB8);
        }
    }

    static final class Mode extends Action {
        private final Supplier<Component> value;
        Mode(int width, boolean compact, Supplier<Component> value, OnPress action) {
            super("Ranking mode", width, compact ? 26 : 38, false, button -> {
                action.onPress(button);
                button.setMessage(Component.literal("Ranking mode: ").append(value.get()));
            });
            this.value = value;
            setMessage(Component.literal("Ranking mode: ").append(value.get()));
            setTooltip(Tooltip.create(Component.literal("Click to cycle ranking modes. Strongest tier selects each player's best eligible ranking.")));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            row(g, this, hoverAmount());
            var font = Minecraft.getInstance().font;
            if (height >= 38) g.text(font, "Ranking mode", getX() + 8, getY() + 6, MUTED, false);
            int textY = getY() + (height < 38 ? 9 : 22);
            g.enableScissor(getX() + 7, textY - 2, getRight() - 19, getBottom());
            g.text(font, value.get(), getX() + 8, textY, TEXT, false);
            g.disableScissor();
            g.text(font, ">", getRight() - 13, textY, MUTED, false);
        }
    }

    private static void row(GuiGraphicsExtractor g, Button widget, float hover) {
        g.fill(widget.getX(), widget.getY(), widget.getRight(), widget.getBottom(), mix(0xFF272E31, 0xFF333E40, hover));
        g.fill(widget.getX() + 7, widget.getBottom() - 1, widget.getRight() - 7, widget.getBottom(), LINE);
        if (widget.isFocused()) g.outline(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), GREEN);
    }
    private static int mix(int from, int to, float amount) {
        int r = (int) (((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int g = (int) (((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int b = (int) ((from & 255) * (1 - amount) + (to & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
