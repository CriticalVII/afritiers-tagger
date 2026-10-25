package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import za.co.afritiers.tagger.client.AfriTiersClient;
import za.co.afritiers.tagger.client.ModeIcons;
import za.co.afritiers.tagger.config.TaggerConfig;
import za.co.afritiers.tagger.network.QueueRepository;
import za.co.afritiers.tagger.ranking.*;
import java.util.*;
import java.util.function.Consumer;

/** Per-mode subscriptions save immediately and work independently of tier-tag visibility. */
public final class WaitlistsScreen extends Screen {
    private final TaggerSettingsScreen owner;
    private TaggerConfig config;
    private Set<String> knownModes = Set.of();
    private final Map<String, TaggerWidgets.Toggle> modeToggles = new HashMap<>();
    private QueueRepository.State lastState;
    private int x, y, w, h;
    private String feedback = "";
    private long feedbackUntil;

    WaitlistsScreen(TaggerSettingsScreen owner) {
        super(Component.literal("Waitlist Notifications")); this.owner = owner;
    }

    @Override protected void init() {
        var mod = AfriTiersClient.instance();
        mod.browseWaitlists(true);
        config = mod.config();
        modeToggles.clear();
        lastState = null;
        w = Math.min(520, width - 20); h = Math.min(348, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        TaggerTabs.add(this::addRenderableWidget, owner, "Waitlists", x, y, w);
        int column = (w - 36) / 2;
        var enabled = new TaggerWidgets.Toggle("Notifications", "Alert when watched waitlists open", column, true,
                () -> config.waitlistNotifications, value -> save(edited -> edited.waitlistNotifications = value));
        enabled.setPosition(x + 12, y + 61); addRenderableWidget(enabled);
        var sound = new TaggerWidgets.Toggle("Sound", "Play a chime with chat alerts", column, true,
                () -> config.waitlistSound, value -> save(edited -> edited.waitlistSound = value));
        sound.setPosition(x + 24 + column, y + 61); addRenderableWidget(sound);

        Set<String> modes = new TreeSet<>(Comparator.comparing(Tier::modeLabel).thenComparing(Comparator.naturalOrder()));
        for (var icon : ModeIcon.values()) if (icon != ModeIcon.OVERALL) modes.add(icon.primaryMode());
        modes.addAll(mod.waitlists().state().snapshot().entries().keySet());
        modes.addAll(config.waitlistModes);
        knownModes = Set.copyOf(modes);
        var rows = LinearLayout.vertical().spacing(2);
        LinearLayout row = null;
        int index = 0;
        for (String mode : modes) {
            if (index++ % 2 == 0) { row = LinearLayout.horizontal().spacing(12); rows.addChild(row); }
            var toggle = new TaggerWidgets.Toggle(Tier.modeLabel(mode), "Notify when this waitlist opens", column, true,
                    () -> config.waitlistModes.contains(mode), value -> save(edited -> {
                        if (value) edited.waitlistModes.add(mode); else edited.waitlistModes.remove(mode);
                    }));
            toggle.setHeight(22);
            toggle.setIcon(ModeIcons.component(mode));
            modeToggles.put(mode, toggle);
            row.addChild(toggle);
        }
        var scroll = new ScrollableLayout(minecraft, rows, Math.max(32, h - 151), ScrollableLayout.ReserveStrategy.RIGHT);
        scroll.setMinWidth(w - 24); scroll.arrangeElements(); scroll.setX(x + 12); scroll.setY(y + 105);
        scroll.visitWidgets(this::addRenderableWidget);
        button("Refresh", 64, x + 12, pressed -> feedback(mod.waitlists().refresh()));
        button("All", 42, x + 82, pressed -> { save(edited -> edited.waitlistModes.addAll(knownModes)); rebuildWidgets(); });
        button("None", 42, x + 130, pressed -> { save(edited -> edited.waitlistModes.clear()); rebuildWidgets(); });
        button("Back", 80, x + w - 92, pressed -> onClose());
    }

    private void button(String label, int width, int left, Button.OnPress action) {
        var button = TaggerWidgets.action(label, width, false, action);
        button.setPosition(left, y + h - 28); addRenderableWidget(button);
    }

    private void save(Consumer<TaggerConfig> change) {
        var edited = AfriTiersClient.instance().config();
        change.accept(edited);
        try {
            AfriTiersClient.instance().saveConfig(edited);
            config = AfriTiersClient.instance().config();
            feedback("Saved automatically");
        } catch (java.io.IOException | IllegalArgumentException exception) {
            feedback("Could not save waitlist settings. Check config permissions.");
        }
    }

    private void feedback(String message) {
        feedback = message;
        feedbackUntil = System.nanoTime() + 4_000_000_000L;
    }

    private String queueStatus(String mode) {
        var state = AfriTiersClient.instance().waitlists().state();
        var entry = state.snapshot().entries().get(mode);
        return state.phase() != QueueRepository.Phase.READY || entry == null ? "Status unavailable" : entry.open() ? "Open now" : "Closed";
    }

    @Override public void tick() {
        var state = AfriTiersClient.instance().waitlists().state();
        if (!knownModes.containsAll(state.snapshot().entries().keySet())) { rebuildWidgets(); return; }
        if (lastState != state) {
            lastState = state;
            modeToggles.forEach((mode, toggle) -> toggle.setTooltip(Tooltip.create(Component.empty()
                    .append(ModeIcons.component(mode)).append(" " + Tier.modeLabel(mode) + ": " + queueStatus(mode) + ". Notify once per opening."))));
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x28090A13);
        TaggerWidgets.panel(g, x, y, w, h);
        g.text(font, Component.literal("AfriTiers").withStyle(style -> style.withBold(true)), x + 12, y + 10, TaggerWidgets.TEXT);
        if (w >= 380) g.text(font, "WAITLIST NOTIFICATIONS", x + 12, y + 22, TaggerWidgets.MUTED);
        g.fill(x + 12, y + 32, x + w - 12, y + 33, TaggerWidgets.LINE);
        g.text(font, font.plainSubstrByWidth("Choose which waitlists should notify you in game.", w - 24), x + 12, y + 43, TaggerWidgets.MUTED);
        String summary = config.waitlistModes.size() + " watched · " + (config.waitlistNotifications ? "one alert per opening" : "notifications paused");
        g.text(font, font.plainSubstrByWidth(summary, w - 24), x + 12, y + 94, TaggerWidgets.GREEN);
        var state = AfriTiersClient.instance().waitlists().state();
        String note = System.nanoTime() < feedbackUntil ? feedback : state.message();
        g.text(font, font.plainSubstrByWidth(note, w - 24), x + 12, y + h - 43, TaggerWidgets.MUTED);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public void removed() { AfriTiersClient.instance().browseWaitlists(false); }
    @Override public void onClose() { minecraft.gui.setScreen(owner); }
    @Override public boolean isPauseScreen() { return false; }
}
