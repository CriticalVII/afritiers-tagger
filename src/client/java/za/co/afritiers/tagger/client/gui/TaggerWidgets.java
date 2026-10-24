package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pixel-aligned native widgets retain vanilla input, focus, narration and click sounds. */
final class TaggerWidgets {
    static final int TEXT = 0xFFF3F1FA, MUTED = 0xFFC0BCCD, GREEN = 0xFFC8ACF2, LINE = 0x35D9CBEF;
    private TaggerWidgets() { }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x85100E1A);
        g.fill(x, y, x + w, y + 1, LINE);
        g.fill(x, y + h - 1, x + w, y + h, 0x20D9CBEF);
        g.fill(x, y, x + 2, y + 28, GREEN);
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
            int fill = primary ? mix(0x806C4D95, 0xB08B67B8, over) : mix(0x50322C40, 0x904C405E, over);
            if (!active) fill = 0x352B2832;
            g.fill(getX(), getY(), getRight(), getBottom(), fill);
            g.fill(getX(), getBottom() - 1, getRight(), getBottom(), primary ? 0xAAC8ACF2 : LINE);
            if (isFocused()) g.outline(getX(), getY(), width, height, GREEN);
            g.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, active ? TEXT : MUTED);
        }
    }

    static final class Tab extends Action {
        private final boolean selected;
        Tab(String label, boolean selected, OnPress action) {
            super(label, 64, 18, selected, action);
            this.selected = selected;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            if (isHoveredOrFocused()) g.fill(getX(), getY(), getRight(), getBottom(), 0x405F4E75);
            if (selected || isFocused()) g.fill(getX() + 7, getBottom() - 1, getRight() - 7, getBottom(), GREEN);
            g.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + 4, selected ? TEXT : MUTED);
        }
    }

    static final class Input extends EditBox {
        Input(int width, int height, Component label) {
            super(Minecraft.getInstance().font, width, height, label);
            setBordered(false);
            setTextColor(TEXT);
        }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            g.fill(getX() - 4, getY() - 3, getRight() + 2, getBottom(), 0x50130F20);
            g.fill(getX() - 4, getBottom(), getRight() + 2, getBottom() + 1, isFocused() ? GREEN : LINE);
            super.extractWidgetRenderState(g, mouseX, mouseY, delta);
        }
    }

    static final class Toggle extends Action {
        private final String label, description;
        private final BooleanSupplier value;
        Toggle(String label, String description, int width, boolean compact, BooleanSupplier value, Consumer<Boolean> change) {
            super(label + ": " + (value.getAsBoolean() ? "ON" : "OFF"), width, compact ? 26 : 34, false, button -> {
                change.accept(!value.getAsBoolean());
                button.setMessage(Component.literal(label + ": " + (value.getAsBoolean() ? "ON" : "OFF")));
            });
            this.label = label; this.description = description; this.value = value;
            setTooltip(Tooltip.create(Component.literal(label + ": " + description)));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            row(g, this, hoverAmount());
            var font = Minecraft.getInstance().font;
            g.text(font, font.plainSubstrByWidth(label, width - 48), getX() + 8, getY() + (height < 34 ? 9 : 6), TEXT);
            if (height >= 34) g.text(font, font.plainSubstrByWidth(description, width - 16), getX() + 8, getY() + 21, MUTED);
            int right = getRight() - 7;
            boolean on = value.getAsBoolean();
            int chipY = getY() + (height < 34 ? 7 : 5);
            g.fill(right - 27, chipY, right, chipY + 13, on ? 0x706C4D95 : 0x402B2832);
            g.fill(right - 27, chipY, right - 25, chipY + 13, on ? GREEN : 0xFF787280);
            g.centeredText(font, on ? "ON" : "OFF", right - 12, chipY + 3, on ? TEXT : MUTED);
        }
    }

    static final class Mode extends Action {
        private final Supplier<Component> value;
        Mode(int width, boolean compact, Supplier<Component> value, OnPress action) {
            super("Ranking mode", width, compact ? 26 : 34, false, button -> {
                action.onPress(button);
                button.setMessage(Component.literal("Ranking mode: ").append(value.get()));
            });
            this.value = value;
            setMessage(Component.literal("Ranking mode: ").append(value.get()));
            setTooltip(Tooltip.create(Component.literal("Choose from every mode. Strongest tier selects each player's best eligible ranking.")));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            row(g, this, hoverAmount());
            var font = Minecraft.getInstance().font;
            if (height >= 34) g.text(font, "Ranking mode", getX() + 8, getY() + 5, MUTED);
            int textY = getY() + (height < 34 ? 9 : 21);
            g.enableScissor(getX() + 7, textY - 2, getRight() - 19, getBottom());
            g.text(font, value.get(), getX() + 8, textY, TEXT);
            g.disableScissor();
            g.text(font, ">", getRight() - 13, textY, MUTED, false);
        }
    }

    private static void row(GuiGraphicsExtractor g, Button widget, float hover) {
        g.fill(widget.getX(), widget.getY(), widget.getRight(), widget.getBottom(), mix(0x251E192A, 0x70604A7C, hover));
        g.fill(widget.getX() + 7, widget.getBottom() - 1, widget.getRight() - 7, widget.getBottom(), LINE);
        if (widget.isFocused()) g.outline(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), GREEN);
    }
    private static int mix(int from, int to, float amount) {
        int r = (int) (((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int g = (int) (((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int b = (int) ((from & 255) * (1 - amount) + (to & 255) * amount);
        int a = (int) ((from >>> 24) * (1 - amount) + (to >>> 24) * amount);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
