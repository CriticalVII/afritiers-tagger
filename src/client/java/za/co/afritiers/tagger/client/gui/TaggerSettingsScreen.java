package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import za.co.afritiers.tagger.client.AfriTiersClient;
import za.co.afritiers.tagger.client.ModeIcons;
import za.co.afritiers.tagger.client.TagFormatter;
import za.co.afritiers.tagger.config.TaggerConfig;
import za.co.afritiers.tagger.ranking.Tier;
import java.util.*;

/** Native settings with a pinned live preview, scrollable controls and fixed actions. */
public final class TaggerSettingsScreen extends Screen {
    private final Screen parent;
    private TaggerConfig draft;
    private Component preview = Component.empty();
    private Button saveButton;
    private EditBox colourField;
    private String feedback = "", validationError = "";
    private int previewTier = 4;
    private int panelX, panelY, panelWidth, panelHeight;
    private boolean compact;

    public TaggerSettingsScreen(Screen parent) {
        super(Component.translatable("afritiers_tagger.title"));
        this.parent = parent;
        draft = AfriTiersClient.instance().config();
    }

    @Override protected void init() {
        panelWidth = Math.min(460, width - 20);
        panelHeight = Math.min(326, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        compact = panelHeight < 300;
        int contentWidth = panelWidth - 34;
        boolean columns = panelWidth >= 320;
        int columnWidth = columns ? (contentWidth - 12) / 2 : contentWidth;
        LinearLayout display = section("Display", columnWidth);
        display.addChild(new TaggerWidgets.Toggle("Enabled", "Show tier tags", columnWidth, compact, () -> draft.enabled, value -> draft.enabled = value));
        display.addChild(new TaggerWidgets.Toggle("Nameplates", "Above players", columnWidth, compact, () -> draft.nameplates, value -> draft.nameplates = value));
        display.addChild(new TaggerWidgets.Toggle("Tab list", "In the player list", columnWidth, compact, () -> draft.tabList, value -> draft.tabList = value));
        display.addChild(new TaggerWidgets.Toggle("Top 10 rank", "Gold labels #1–#10", columnWidth, compact, () -> draft.showTopTen, value -> draft.showTopTen = value));
        LinearLayout appearance = section("Appearance", columnWidth);
        appearance.addChild(new TaggerWidgets.Mode(columnWidth, compact, this::modeText, pressed -> cycleMode()));
        appearance.addChild(new TaggerWidgets.Toggle("Retired tiers", "Include past tiers", columnWidth, compact, () -> draft.includeRetired, value -> draft.includeRetired = value));
        LinearLayout colour = compact ? LinearLayout.horizontal().spacing(4) : LinearLayout.vertical().spacing(3);
        colour.addChild(new StringWidget(compact ? 43 : columnWidth, compact ? 20 : 12,
                Component.literal(compact ? "Colour" : "Colour override").withColor(TaggerWidgets.MUTED), font));
        EditBox field = new EditBox(font, compact ? columnWidth - 47 : columnWidth, 20, Component.literal("Colour override; blank uses tier colours"));
        colourField = field;
        field.setMaxLength(7);
        field.setValue(draft.customColour);
        field.setHint(Component.literal(compact ? "Tier colours" : "Default tier colours"));
        field.setTextColor(0xFFE8ECE9);
        field.setResponder(value -> { draft.customColour = value; feedback = ""; updatePreview(); });
        field.setTooltip(Tooltip.create(Component.literal("Optional six-digit hex colour, for example FFD700. Leave blank for gold, silver, bronze and gray. Icons keep their own colours.")));
        colour.addChild(field);
        appearance.addChild(colour);
        LinearLayout controls = columns ? LinearLayout.horizontal().spacing(12) : LinearLayout.vertical().spacing(12);
        controls.addChild(display);
        controls.addChild(appearance);
        ScrollableLayout scroll = new ScrollableLayout(minecraft, controls, Math.max(32, panelHeight - (compact ? 128 : 174)), ScrollableLayout.ReserveStrategy.RIGHT);
        scroll.setMinWidth(contentWidth);
        scroll.arrangeElements();
        scroll.setX(panelX + 12);
        scroll.setY(panelY + (compact ? 78 : 104));
        scroll.visitWidgets(this::addRenderableWidget);

        Button previous = new TaggerWidgets.Action("<", 16, 16, false, pressed -> cyclePreview(-1));
        previous.setTooltip(Tooltip.create(Component.literal("Previous preview tier")));
        previous.setPosition(panelX + panelWidth - 50, panelY + (compact ? 38 : 48));
        addRenderableWidget(previous);
        Button next = new TaggerWidgets.Action(">", 16, 16, false, pressed -> cyclePreview(1));
        next.setTooltip(Tooltip.create(Component.literal("Next preview tier")));
        next.setPosition(panelX + panelWidth - 30, panelY + (compact ? 38 : 48));
        addRenderableWidget(next);
        Button refresh = TaggerWidgets.action("Refresh", 52, false, pressed -> feedback = AfriTiersClient.instance().repository().refresh());
        refresh.setTooltip(Tooltip.create(Component.literal("Refresh rankings using your saved settings.")));
        refresh.setPosition(panelX + panelWidth - 122, panelY + panelHeight - (compact ? 49 : 69));
        addRenderableWidget(refresh);
        Button details = TaggerWidgets.action("Details", 52, false, pressed -> minecraft.gui.setScreen(new ConnectionDetailsScreen(this)));
        details.setTooltip(Tooltip.create(Component.literal("Connection, freshness and username matching details")));
        details.setPosition(panelX + panelWidth - 64, panelY + panelHeight - (compact ? 49 : 69));
        addRenderableWidget(details);
        Button reset = TaggerWidgets.action("Reset", 52, false, pressed -> {
            draft = new TaggerConfig();
            feedback = "Defaults restored. Save to apply.";
            rebuildWidgets();
        });
        reset.setTooltip(Tooltip.create(Component.literal("Restore default settings in this draft")));
        reset.setPosition(panelX + 12, panelY + panelHeight - (compact ? 27 : 30));
        addRenderableWidget(reset);
        Button cancel = TaggerWidgets.action("Cancel", 66, false, pressed -> onClose());
        cancel.setPosition(panelX + panelWidth - 164, panelY + panelHeight - (compact ? 27 : 30));
        addRenderableWidget(cancel);
        saveButton = TaggerWidgets.action("Save", 80, true, pressed -> save());
        saveButton.setPosition(panelX + panelWidth - 92, panelY + panelHeight - (compact ? 27 : 30));
        addRenderableWidget(saveButton);
        addTabs();
        updatePreview();
    }

    private void addTabs() {
        Button settings = new TaggerWidgets.Action("Settings", 64, 18, true, pressed -> { });
        settings.setPosition(panelX + panelWidth - 148, panelY + 9);
        addRenderableWidget(settings);
        Button search = new TaggerWidgets.Action("Players", 64, 18, false,
                pressed -> minecraft.gui.setScreen(new PlayerSearchScreen(this)));
        search.setPosition(panelX + panelWidth - 78, panelY + 9);
        addRenderableWidget(search);
    }

    private LinearLayout section(String heading, int sectionWidth) {
        LinearLayout layout = LinearLayout.vertical().spacing(0);
        layout.addChild(new StringWidget(sectionWidth, 18, Component.literal(heading).withColor(TaggerWidgets.GREEN), font));
        return layout;
    }

    private void cycleMode() {
        List<String> modes = new ArrayList<>(AfriTiersClient.instance().repository().state().rankings().modes());
        if (!draft.mode.equals("strongest") && !modes.contains(draft.mode)) modes.add(draft.mode);
        Collections.sort(modes);
        modes.addFirst("strongest");
        draft.mode = modes.get((modes.indexOf(draft.mode) + 1) % modes.size());
        updatePreview();
    }

    private Component modeText() {
        return Component.empty().append(ModeIcons.component(draft.mode))
                .append(" " + (draft.mode.equals("strongest") ? "Strongest tier" : Tier.modeLabel(draft.mode)));
    }

    private void cyclePreview(int direction) {
        previewTier = Math.floorMod(previewTier + direction, Tier.ORDER.size());
        updatePreview();
    }

    private void updatePreview() {
        String previousError = validationError;
        TaggerConfig sample = draft.copy();
        validationError = "";
        try { sample.validate(); }
        catch (IllegalArgumentException exception) { validationError = exception.getMessage(); sample.customColour = ""; }
        preview = Component.empty().append(TagFormatter.placement(draft.showTopTen ? 1 : 0)).append(TagFormatter.tag(new Tier(draft.mode.equals("strongest") ? "mace" : draft.mode,
                Tier.ORDER.get(previewTier), false), sample)).append("PlayerName");
        if (saveButton != null) saveButton.active = validationError.isEmpty();
        if (colourField != null) {
            colourField.setTextColor(validationError.isEmpty() ? TaggerWidgets.TEXT : 0xFFE0BE83);
            if (!previousError.equals(validationError)) colourField.setTooltip(Tooltip.create(Component.literal(validationError.isEmpty()
                    ? "Leave blank for tier colours, or enter a six-digit hex colour." : validationError)));
        }
    }

    private void save() {
        try { AfriTiersClient.instance().saveConfig(draft); onClose(); }
        catch (java.io.IOException | IllegalArgumentException exception) {
            feedback = exception instanceof IllegalArgumentException ? exception.getMessage() : "Could not save. Check config folder permissions.";
        }
    }

    @Override public void tick() { updatePreview(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x65080D0F);
        TaggerWidgets.panel(g, panelX, panelY, panelWidth, panelHeight);
        g.text(font, Component.literal("AfriTiers").withStyle(style -> style.withBold(true)), panelX + 12, panelY + 13, TaggerWidgets.TEXT);
        g.fill(panelX + 12, panelY + (compact ? 26 : 32), panelX + panelWidth - 12, panelY + (compact ? 27 : 33), TaggerWidgets.LINE);
        int x = panelX + 12, y = panelY + (compact ? 34 : 42), w = panelWidth - 24;
        int previewHeight = compact ? 36 : 50;
        g.fill(x, y, x + w, y + previewHeight, 0xFF12191C);
        g.outline(x, y, w, previewHeight, 0xFF0C1113);
        g.text(font, "Name preview", x + 10, y + (compact ? 6 : 8), TaggerWidgets.MUTED, false);
        g.centeredText(font, preview, x + w / 2, y + (compact ? 22 : 29), TaggerWidgets.TEXT);
        var state = AfriTiersClient.instance().repository().state();
        int statusColor = switch (state.phase()) {
            case READY -> TaggerWidgets.GREEN;
            case STALE, LOADING -> 0xFFD1BB82;
            case ERROR -> 0xFFE1A094;
            default -> TaggerWidgets.MUTED;
        };
        String summary = switch (state.phase()) {
            case READY -> "Up to date | " + state.rankings().players().size() + (state.rankings().players().size() == 1 ? " player" : " players");
            case STALE -> "Source out of date";
            case LOADING -> "Updating rankings";
            case ERROR -> "Connection issue";
            case DISABLED -> "Tagger disabled";
            case DISCONNECTED -> "Join a world to sync";
        };
        int statusY = panelY + panelHeight - (compact ? 44 : 62);
        if (compact && !validationError.isEmpty()) summary = validationError;
        else if (compact && !feedback.isEmpty()) summary = feedback;
        g.fill(x, statusY + 2, x + 3, statusY + 5, statusColor);
        g.text(font, font.plainSubstrByWidth(summary, panelWidth - 152), x + 9, statusY, TaggerWidgets.MUTED, false);
        String note = !validationError.isEmpty() ? validationError : !feedback.isEmpty() ? feedback : "Changes apply when saved";
        if (!compact) g.text(font, font.plainSubstrByWidth(note, panelWidth - 24), x, panelY + panelHeight - 43,
                validationError.isEmpty() && feedback.isEmpty() ? TaggerWidgets.MUTED : 0xFFE0BE83, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
