package za.co.afritiers.tagger.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.co.afritiers.tagger.client.command.AfriTiersCommands;
import za.co.afritiers.tagger.client.gui.TaggerSettingsScreen;
import za.co.afritiers.tagger.config.ConfigStore;
import za.co.afritiers.tagger.config.TaggerConfig;
import za.co.afritiers.tagger.network.HttpProfileLookup;
import za.co.afritiers.tagger.network.HttpRankingSource;
import za.co.afritiers.tagger.network.PaidNameResolver;
import za.co.afritiers.tagger.network.RankingRepository;
import za.co.afritiers.tagger.network.HttpQueueSource;
import za.co.afritiers.tagger.network.QueueRepository;
import za.co.afritiers.tagger.ranking.Rankings;
import za.co.afritiers.tagger.ranking.Tier;

import java.io.IOException;
import java.util.LinkedHashSet;

public final class AfriTiersClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("afritiers_tagger");
    private static AfriTiersClient instance;
    private ConfigStore store;
    private volatile TaggerConfig config;
    private RankingRepository repository;
    private PaidNameResolver paidNames;
    private RankingRepository browser;
    private boolean browsingPlayers;
    private QueueRepository waitlists;
    private boolean browsingWaitlists;
    private net.minecraft.client.multiplayer.ClientLevel observedWorld;
    private net.minecraft.client.player.LocalPlayer observedPlayer;
    private boolean worldRefreshRequested;

    public static AfriTiersClient instance() { return instance; }
    public TaggerConfig config() { return config.copy(); }
    TaggerConfig activeConfig() { return config; }
    public RankingRepository repository() { return repository; }
    public PaidNameResolver paidNames() { return paidNames; }
    public QueueRepository waitlists() { return waitlists; }
    public void browseWaitlists(boolean browsing) { browsingWaitlists = browsing; }
    public String configWarning() { return store.warning(); }
    public void requestWorldRefresh() { worldRefreshRequested = true; }

    @Override public void onInitializeClient() {
        instance = this;
        store = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("afritiers_tagger.json"));
        config = store.load();
        repository = new RankingRepository(new HttpRankingSource());
        browser = new RankingRepository(new HttpRankingSource());
        paidNames = new PaidNameResolver(new HttpProfileLookup());
        waitlists = new QueueRepository(new HttpQueueSource());
        repository.setEnabled(config.enabled);
        KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.afritiers_tagger.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("afritiers_tagger", "controls"))));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            observedWorld = null;
            observedPlayer = null;
            worldRefreshRequested = false;
            paidNames.resetSession();
            repository.connect();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            observedWorld = null; observedPlayer = null; worldRefreshRequested = false;
            repository.disconnect(); paidNames.setActive(false);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { repository.close(); browser.close(); paidNames.close(); waitlists.close(); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateWorldSession(client);
            repository.tick();
            if (browsingPlayers) browser.tick();
            updatePaidNames(client);
            var watched = config.waitlistNotifications ? config.waitlistModes : java.util.Set.<String>of();
            var notifications = waitlists.tick(client.player != null && client.getConnection() != null, browsingWaitlists, watched);
            for (var queue : notifications) {
                Component message = Component.literal("[AfriTiers] ").withColor(0xC8ACF2)
                        .append(ModeIcons.component(queue.mode()))
                        .append(Component.literal(" " + Tier.modeLabel(queue.mode()) + " waitlist is open!").withColor(0xFFD700));
                if (queue.joinUrl() != null) message = message.copy().append(Component.literal(" [Join]").withStyle(style -> style
                        .withColor(0x85DBDA).withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(queue.joinUrl()))));
                client.gui.hud.getChat().addClientSystemMessage(message);
            }
            if (!notifications.isEmpty() && config.waitlistSound && client.player != null)
                client.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.65F, 1.2F);
            while (openSettings.consumeClick()) client.gui.setScreen(new TaggerSettingsScreen(client.gui.screen()));
        });
        AfriTiersCommands.register(this);
        LOGGER.info("AfriTiers ready: username matching, verified paid-account rename recovery");
        if (!store.warning().isEmpty()) LOGGER.warn("{}", store.warning());
    }

    public void saveConfig(TaggerConfig edited) throws IOException {
        TaggerConfig validated = edited.copy();
        validated.validate();
        store.save(validated);
        config = validated;
        repository.setEnabled(validated.enabled);
        if (!validated.enabled) paidNames.setActive(false);
    }

    public String rankingUsername(String username) {
        return rankingUsername(username, repository.state().rankings());
    }

    public String rankingUsername(String username, Rankings rankings) {
        String key = Tier.normalizeUsername(username);
        boolean direct = rankings.players().containsKey(key);
        // Cracked records and paid records without a usable paid ID remain username-only.
        if (direct && !rankings.paidProfiles().containsValue(key)) return key;
        if (!direct) {
            String alias = config.usernameAliases.get(key);
            if (alias != null && rankings.players().containsKey(alias)) return alias;
        }
        var verified = paidNames.profileId(key);
        if (verified.isPresent()) return rankings.paidProfiles().getOrDefault(verified.get(), "");
        if (direct && paidNames.notFound(key)) return "";
        return direct ? key : "";
    }

    public RankingRepository playerSearchRepository() {
        return Minecraft.getInstance().player != null && config.enabled ? repository : browser;
    }

    public void beginPlayerSearch() {
        browsingPlayers = true;
        if (playerSearchRepository() == browser && browser.state().phase() == RankingRepository.Phase.DISCONNECTED) browser.connect();
    }

    public void endPlayerSearch() {
        browsingPlayers = false;
        browser.disconnect();
    }

    public void requestPlayerSearch(String username) {
        var rankings = playerSearchRepository().state().rankings();
        String key = Tier.normalizeUsername(username);
        if (rankings.players().containsKey(key) && !rankings.paidProfiles().containsValue(key)) return;
        if (config.usernameAliases.containsKey(key)) return;
        if (!rankings.paidProfiles().isEmpty()) paidNames.request(username);
    }

    private void updateWorldSession(Minecraft client) {
        if (client.level == null || client.player == null || client.getConnection() == null) return;
        // JOIN already starts the first fetch; initial spawn packets belong to that same sync.
        if (observedWorld == null) {
            observedWorld = client.level;
            observedPlayer = client.player;
            worldRefreshRequested = false;
            return;
        }
        boolean replaced = observedWorld != client.level || observedPlayer != client.player;
        if (!replaced && !worldRefreshRequested) return;
        observedWorld = client.level;
        observedPlayer = client.player;
        worldRefreshRequested = false;
        if (replaced) paidNames.resetSession();
        repository.worldChanged();
    }

    private void updatePaidNames(Minecraft client) {
        boolean active = browsingPlayers || config.enabled && client.player != null && client.getConnection() != null;
        paidNames.setActive(active);
        var rankings = repository.state().rankings();
        if (!active || client.player == null || rankings.paidProfiles().isEmpty()) return;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(client.player.getGameProfile().name());
        client.level.players().stream().limit(80).forEach(player -> names.add(player.getGameProfile().name()));
        client.getConnection().getListedOnlinePlayers().stream().limit(80).forEach(info -> names.add(info.getProfile().name()));
        for (String username : names) {
            String key = Tier.normalizeUsername(username);
            if (rankings.players().containsKey(key) && !rankings.paidProfiles().containsValue(key)) continue;
            if (!rankings.players().containsKey(key) && config.usernameAliases.containsKey(key)) continue;
            paidNames.request(username);
        }
    }
}
