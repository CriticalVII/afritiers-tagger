package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import za.co.afritiers.tagger.client.AfriTiersClient;
import za.co.afritiers.tagger.client.ModeIcons;
import za.co.afritiers.tagger.ranking.ModeIcon;
import za.co.afritiers.tagger.ranking.Tier;
import java.util.*;

/** All bundled modes remain selectable even before rankings have downloaded. */
public final class ModePickerScreen extends Screen {
    private final TaggerSettingsScreen owner;
    private final String selected;
    private int x, y, w, h;

    ModePickerScreen(TaggerSettingsScreen owner, String selected) {
        super(Component.literal("Choose a mode"));
        this.owner = owner;
        this.selected = selected;
    }

    @Override protected void init() {
        w = Math.min(520, width - 20); h = Math.min(348, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        var available = AfriTiersClient.instance().repository().state().rankings().modes();
        Set<String> modes = new LinkedHashSet<>();
        modes.add("strongest");
        for (ModeIcon icon : ModeIcon.values()) {
            if (icon == ModeIcon.OVERALL) continue;
            modes.add(available.stream().filter(mode -> ModeIcon.find(mode).orElse(null) == icon)
                    .sorted().findFirst().orElse(icon.primaryMode()));
        }
        available.stream().filter(mode -> ModeIcon.find(mode).isEmpty()).sorted().forEach(modes::add);
        if (!selected.equals("strongest") && modes.stream().noneMatch(this::isSelected)) modes.add(selected);
        int columns = w >= 320 ? 2 : 1;
        int cellWidth = (w - 36 - (columns - 1) * 8) / columns;
        LinearLayout list = LinearLayout.vertical().spacing(3);
        LinearLayout row = null;
        int index = 0;
        for (String mode : modes) {
            if (index++ % columns == 0) { row = LinearLayout.horizontal().spacing(8); list.addChild(row); }
            String name = mode.equals("strongest") ? "Strongest tier" : Tier.modeLabel(mode);
            var button = new TaggerWidgets.Action(name, cellWidth, 24, isSelected(mode), pressed -> {
                owner.selectMode(mode); onClose();
            });
            button.setMessage(Component.empty().append(ModeIcons.component(mode)).append(" " + name));
            button.setTooltip(Tooltip.create(Component.literal(mode.equals("strongest")
                    ? "Each player's best eligible tier across all modes" : "Display " + name + " tiers only")));
            row.addChild(button);
        }
        var scroll = new ScrollableLayout(minecraft, list, Math.max(32, h - 102), ScrollableLayout.ReserveStrategy.RIGHT);
        scroll.setMinWidth(w - 24); scroll.arrangeElements(); scroll.setX(x + 12); scroll.setY(y + 58);
        scroll.visitWidgets(this::addRenderableWidget);
        var back = TaggerWidgets.action("Back", 80, false, pressed -> onClose());
        back.setPosition(x + w - 92, y + h - 30); addRenderableWidget(back);
    }

    private boolean isSelected(String mode) {
        return selected.equals(mode) || !selected.equals("strongest") && ModeIcon.find(selected).isPresent()
                && ModeIcon.find(selected).equals(ModeIcon.find(mode));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x35090A13);
        TaggerWidgets.panel(g, x, y, w, h);
        g.text(font, "Choose a mode", x + 12, y + 14, TaggerWidgets.TEXT);
        g.text(font, "One mode, or everyone's strongest tier.", x + 12, y + 34, TaggerWidgets.MUTED);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(owner); }
    @Override public boolean isPauseScreen() { return false; }
}
