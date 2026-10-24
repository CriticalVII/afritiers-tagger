package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import za.co.afritiers.tagger.client.AfriTiersClient;
import za.co.afritiers.tagger.client.ModeIcons;
import za.co.afritiers.tagger.network.RankingRepository;
import za.co.afritiers.tagger.ranking.*;

import java.util.Comparator;

/** A transparent profile sheet with an account skin and searchable, scrollable rankings. */
public final class ProfilesScreen extends Screen {
    private final TaggerSettingsScreen owner;
    private String input = "", requested = "", apiName = "", feedback = "";
    private Rankings snapshot = Rankings.EMPTY;
    private RankingRepository.Phase phase;
    private EditBox searchField;
    private Button searchButton;
    private int x, y, w, h;

    public ProfilesScreen(TaggerSettingsScreen owner) {
        super(Component.literal("AfriTiers profiles"));
        this.owner = owner;
        input = net.minecraft.client.Minecraft.getInstance().getUser().getName();
        requested = input;
    }

    @Override protected void init() {
        var mod = AfriTiersClient.instance();
        mod.beginPlayerSearch();
        snapshot = mod.playerSearchRepository().state().rankings();
        phase = mod.playerSearchRepository().state().phase();
        apiName = requested.isEmpty() ? "" : mod.rankingUsername(requested, snapshot);
        w = Math.min(520, width - 20);
        h = Math.min(348, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        Button settings = new TaggerWidgets.Tab("Settings", false, pressed -> onClose());
        settings.setPosition(x + w - 148, y + 9); addRenderableWidget(settings);
        Button players = new TaggerWidgets.Tab("Profiles", true, pressed -> { });
        players.setPosition(x + w - 78, y + 9); addRenderableWidget(players);
        searchButton = TaggerWidgets.action("Search", 60, true, pressed -> search());
        searchButton.setPosition(x + w - 148, y + 42);
        searchField = new EditBox(font, w - 166, 20, Component.literal("Player username"));
        searchField.setPosition(x + 12, y + 42);
        searchField.setMaxLength(16);
        searchField.setBordered(false);
        searchField.setTextColor(TaggerWidgets.TEXT);
        searchField.setHint(Component.literal("Player username"));
        searchField.setValue(input);
        searchField.setResponder(value -> { input = value; searchButton.active = validInput(); });
        searchField.setTooltip(Tooltip.create(Component.literal("Search a Minecraft username or a saved alias. Paid name changes are resolved automatically.")));
        addRenderableWidget(searchField); addRenderableWidget(searchButton);
        Button self = TaggerWidgets.action("My profile", 70, false, pressed -> {
            input = minecraft.getUser().getName();
            search();
        });
        self.setPosition(x + w - 82, y + 42); addRenderableWidget(self);
        searchButton.active = validInput();
        if (requested.isEmpty()) setInitialFocus(searchField);

        snapshot.profile(apiName).ifPresent(this::addProfile);
        if (!requested.isEmpty() && w >= 340) addSkin();
        Button refresh = TaggerWidgets.action("Refresh", 64, false, pressed -> feedback = mod.playerSearchRepository().refresh());
        refresh.setTooltip(Tooltip.create(Component.literal("Refresh player stats. API cooldowns still apply.")));
        refresh.setPosition(x + 12, y + h - 30); addRenderableWidget(refresh);
        Button back = TaggerWidgets.action("Back", 80, false, pressed -> onClose());
        back.setPosition(x + w - 92, y + h - 30); addRenderableWidget(back);
    }

    private boolean validInput() { return input.trim().matches("[A-Za-z0-9_]{1,16}"); }

    private void search() {
        if (!validInput()) return;
        requested = input.trim();
        feedback = "";
        AfriTiersClient.instance().requestPlayerSearch(requested);
        rebuildWidgets();
    }

    private void addProfile(PlayerStats profile) {
        boolean showSkin = w >= 340;
        int contentX = x + (showSkin ? 158 : 12), contentWidth = w - (showSkin ? 182 : 36);
        LinearLayout rows = LinearLayout.vertical().spacing(3);
        if (!requested.equalsIgnoreCase(profile.name())) line(rows, contentWidth, "API name: ", profile.name(), TaggerWidgets.MUTED);
        line(rows, contentWidth, "Region: ", profile.region(), TaggerWidgets.GREEN);
        line(rows, contentWidth, "Points: ", profile.points() + " (" + profile.combatTitle() + ")", 0xFFFFD700);
        line(rows, contentWidth, "Overall rank: ", profile.position() == 0 ? "Unranked" : "#" + profile.position(), 0xFFD700);
        rows.addChild(new ProfileLine(contentWidth, Component.literal("RANKINGS").withColor(TaggerWidgets.GREEN)));
        var tiers = snapshot.players().getOrDefault(apiName, java.util.List.of());
        tiers.stream().sorted(Comparator.comparing(Tier::retired).thenComparingInt(Tier::strength).thenComparing(Tier::mode)).forEach(tier -> {
            Component text = Component.empty().append(ModeIcons.component(tier.mode()))
                    .append(Component.literal(" " + Tier.modeLabel(tier.mode()) + ": ").withColor(TaggerWidgets.TEXT))
                    .append(Component.literal(tier.level()).withColor(AfriTiersClient.instance().config().colour(tier.level())))
                    .append(Component.literal(tier.retired() ? " (retired)" : "").withColor(TaggerWidgets.MUTED));
            rows.addChild(new ProfileLine(contentWidth, text));
        });
        if (tiers.isEmpty()) rows.addChild(new ProfileLine(contentWidth, Component.literal("No recorded tiers").withColor(TaggerWidgets.MUTED)));
        ScrollableLayout scroll = new ScrollableLayout(minecraft, rows, Math.max(32, h - 148), ScrollableLayout.ReserveStrategy.RIGHT);
        scroll.setMinWidth(contentWidth);
        scroll.arrangeElements(); scroll.setX(contentX); scroll.setY(y + 100);
        scroll.visitWidgets(this::addRenderableWidget);

    }

    private void addSkin() {
            var online = minecraft.getConnection() == null ? null : minecraft.getConnection().getListedOnlinePlayers().stream()
                    .filter(info -> info.getProfile().name().equalsIgnoreCase(requested)).findFirst().orElse(null);
            boolean self = requested.equalsIgnoreCase(minecraft.getUser().getName());
            java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> skinSource = online != null
                    ? online::getSkin : self ? minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), true)
                    : DefaultPlayerSkin::getDefaultSkin;
            int skinHeight = Math.max(50, Math.min(220, h - 162));
            var skin = new PlayerSkinWidget(132, skinHeight, minecraft.getEntityModels(), skinSource);
            skin.setPosition(x + 12, y + 102); addRenderableWidget(skin);
            var caption = new ProfileLine(132, Component.literal(self ? "YOUR PROFILE" : online == null ? "Default skin" : "Online skin").withColor(TaggerWidgets.MUTED));
            caption.setPosition(x + 16, y + 105 + skinHeight); addRenderableWidget(caption);
    }

    private void line(LinearLayout rows, int width, String label, String value, int colour) {
        rows.addChild(new ProfileLine(width, Component.literal(label).withColor(TaggerWidgets.TEXT)
                .append(Component.literal(value).withColor(colour))));
    }

    @Override public void tick() {
        var mod = AfriTiersClient.instance();
        var state = mod.playerSearchRepository().state();
        if (!requested.isEmpty()) mod.requestPlayerSearch(requested);
        String resolved = requested.isEmpty() ? "" : mod.rankingUsername(requested, state.rankings());
        if (snapshot != state.rankings() || phase != state.phase() || !apiName.equals(resolved)) rebuildWidgets();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && searchField.isFocused()) {
            search(); return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x28090A13);
        TaggerWidgets.panel(g, x, y, w, h);
        g.text(font, Component.literal("AfriTiers").withStyle(style -> style.withBold(true)), x + 12, y + 10, TaggerWidgets.TEXT);
        g.text(font, "PLAYER PROFILES", x + 12, y + 22, TaggerWidgets.MUTED);
        g.fill(x + 12, y + 32, x + w - 12, y + 33, TaggerWidgets.LINE);
        g.fill(searchField.getX() - 4, searchField.getY() - 3, searchField.getRight() + 2, searchField.getBottom(), 0x50130F20);
        g.fill(searchField.getX() - 4, searchField.getBottom(), searchField.getRight() + 2, searchField.getBottom() + 1, TaggerWidgets.LINE);
        var state = AfriTiersClient.instance().playerSearchRepository().state();
        var profile = snapshot.profile(apiName);
        int contentX = x + (w >= 340 ? 158 : 12), contentWidth = w - (w >= 340 ? 182 : 36);
        if (!requested.isEmpty()) {
            String name = profile.isPresent() && requested.equalsIgnoreCase(profile.get().name()) ? profile.get().name() : requested;
            g.text(font, Component.literal(name).withStyle(style -> style.withBold(true)), contentX, y + 80, TaggerWidgets.TEXT);
            g.fill(contentX, y + 94, x + w - 18, y + 95, TaggerWidgets.LINE);
        }
        if (profile.isEmpty()) {
            String message = requested.isEmpty() ? "Search a player to see their stats" : "No ranked player found";
            if (phase == RankingRepository.Phase.LOADING) message = "Loading player stats...";
            if (phase == RankingRepository.Phase.ERROR) message = "Stats unavailable — use Refresh to retry";
            g.text(font, font.plainSubstrByWidth(message, contentWidth), contentX, y + 110, TaggerWidgets.MUTED);
            if (!requested.isEmpty() && phase != RankingRepository.Phase.ERROR && phase != RankingRepository.Phase.LOADING) {
                g.text(font, font.plainSubstrByWidth("No recorded tiers for this username.", contentWidth), contentX, y + 130, TaggerWidgets.MUTED);
                g.text(font, font.plainSubstrByWidth(AfriTiersClient.instance().paidNames().status(), contentWidth), contentX, y + 148, TaggerWidgets.MUTED);
            }
        }
        String note = !feedback.isEmpty() ? feedback : state.phase() == RankingRepository.Phase.ERROR ? state.message()
                : snapshot.stale() ? "Source out of date · placements may change"
                : profile.isPresent() ? "Points count active tiers; retired tiers earn 0."
                : "Search uses API usernames and verified paid names.";
        g.text(font, font.plainSubstrByWidth(note, w - 24), x + 12, y + h - 45, TaggerWidgets.MUTED);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public void removed() { AfriTiersClient.instance().endPlayerSearch(); }
    @Override public void onClose() { minecraft.gui.setScreen(owner); }
    @Override public boolean isPauseScreen() { return false; }

    private final class ProfileLine extends AbstractWidget {
        ProfileLine(int width, Component text) {
            super(0, 0, width, 18, text);
            setTooltip(Tooltip.create(text));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            g.enableScissor(getX(), getY() - 2, getRight(), getBottom());
            g.text(font, getMessage(), getX(), getY() + 4, TaggerWidgets.TEXT);
            g.disableScissor();
        }
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
