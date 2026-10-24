package za.co.afritiers.tagger.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.co.afritiers.tagger.config.*;
import za.co.afritiers.tagger.network.*;

import java.io.IOException;

public final class AfriTiersClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("afritiers_tagger");
    private static AfriTiersClient instance;
    private ConfigStore store;
    private volatile TaggerConfig config;
    private RankingRepository repository;
    private PaidNameResolver paidNames;
    private RankingRepository browser;
    private boolean browsingPlayers;

    public static AfriTiersClient instance() { return instance; }
    public TaggerConfig config() { return config.copy(); }
    TaggerConfig activeConfig() { return config; }
    public RankingRepository repository() { return repository; }
    public PaidNameResolver paidNames() { return paidNames; }
    public String configWarning() { return store.warning(); }

    @Override public void onInitializeClient() {
        instance = this;
        store = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("afritiers_tagger.json"));
        config = store.load();
        repository = new RankingRepository(new HttpRankingSource());
        browser = new RankingRepository(new HttpRankingSource());
        paidNames = new PaidNameResolver(new HttpProfileLookup());
        repository.setEnabled(config.enabled);
        KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.afritiers_tagger.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("afritiers_tagger", "controls"))));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> repository.connect());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { repository.disconnect(); paidNames.setActive(false); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { repository.close(); browser.close(); paidNames.close(); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            repository.tick();
            if (browsingPlayers) browser.tick();
            updatePaidNames(client);
            while (openSettings.consumeClick()) client.gui.setScreen(new TaggerSettingsScreen(client.gui.screen()));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("afritiers")
                        .then(aliasCommand())
                        .then(ClientCommands.literal("config").requires(FabricClientCommandSource::attended).executes(context -> {
                            // Delay until chat has closed, otherwise chat replaces the new screen.
                            Minecraft client = context.getSource().getClient();
                            client.schedule(() -> client.gui.setScreen(new TaggerSettingsScreen(null)));
                            return 1;
                        }))
                        .then(ClientCommands.literal("refresh").requires(FabricClientCommandSource::attended).executes(context -> {
                            context.getSource().sendFeedback(Component.literal(repository.refresh()));
                            return 1;
                        }))
                        .then(ClientCommands.literal("status").executes(context -> {
                            var state = repository.state();
                            context.getSource().sendFeedback(Component.literal("AfriTiers: " + state.message()
                                    + " | " + state.rankings().players().size() + " usernames"
                                    + " | ambiguous: " + state.rankings().ambiguousNames()
                                    + " | Nameplates: " + (config.nameplates ? "ON" : "OFF")
                                    + " | Tab: " + (config.tabList ? "ON" : "OFF")
                                    + " | Mode: " + config.mode));
                            return 1;
                        }).then(ClientCommands.argument("username", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(context -> {
                                    String username = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "username");
                                    var rankings = repository.state().rankings();
                                    String apiName = rankingUsername(username);
                                    String result = rankings.select(apiName, config.mode, config.includeRetired)
                                            .map(tier -> tier.label())
                                            .orElseGet(() -> rankings.players().containsKey(apiName)
                                                    ? "No tier matches the selected mode/retired settings"
                                                    : "No matching ranking yet; " + paidNames.status());
                                    context.getSource().sendFeedback(Component.literal("AfriTiers: " + username + " | " + result
                                            + (apiName.isEmpty() || apiName.equalsIgnoreCase(username) ? "" : " | API name: " + apiName)
                                            + " | Nameplates: " + (config.nameplates ? "ON" : "OFF")));
                                    return 1;
                                })))));
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

    public String rankingUsername(String username, za.co.afritiers.tagger.ranking.Rankings rankings) {
        String key = za.co.afritiers.tagger.ranking.Tier.normalizeUsername(username);
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
        String key = za.co.afritiers.tagger.ranking.Tier.normalizeUsername(username);
        if (rankings.players().containsKey(key) && !rankings.paidProfiles().containsValue(key)) return;
        if (config.usernameAliases.containsKey(key)) return;
        if (!rankings.paidProfiles().isEmpty()) paidNames.request(username);
    }

    private void updatePaidNames(Minecraft client) {
        boolean active = browsingPlayers || config.enabled && client.player != null && client.getConnection() != null;
        paidNames.setActive(active);
        var rankings = repository.state().rankings();
        if (!active || client.player == null || rankings.paidProfiles().isEmpty()) return;
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        names.add(client.player.getGameProfile().name());
        client.level.players().stream().limit(80).forEach(player -> names.add(player.getGameProfile().name()));
        client.getConnection().getListedOnlinePlayers().stream().limit(80).forEach(info -> names.add(info.getProfile().name()));
        for (String username : names) {
            String key = za.co.afritiers.tagger.ranking.Tier.normalizeUsername(username);
            if (rankings.players().containsKey(key) && !rankings.paidProfiles().containsValue(key)) continue;
            if (!rankings.players().containsKey(key) && config.usernameAliases.containsKey(key)) continue;
            paidNames.request(username);
        }
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> aliasCommand() {
        return ClientCommands.literal("alias")
                .then(ClientCommands.literal("set").requires(FabricClientCommandSource::attended)
                        .then(ClientCommands.argument("current", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(ClientCommands.argument("apiName", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            repository.state().rankings().players().keySet().stream().sorted()
                                                    .filter(name -> name.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        }).executes(context -> {
                                            String current = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "current");
                                            String api = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "apiName");
                                            var edited = config();
                                            edited.usernameAliases.put(current.toLowerCase(java.util.Locale.ROOT), api);
                                            try {
                                                saveConfig(edited);
                                                context.getSource().sendFeedback(Component.literal("Saved username alias: " + current + " -> " + api));
                                                return 1;
                                            } catch (IOException | IllegalArgumentException exception) {
                                                context.getSource().sendError(Component.literal("Could not save alias: " + exception.getMessage())); return 0;
                                            }
                                        }))))
                .then(ClientCommands.literal("remove").requires(FabricClientCommandSource::attended)
                        .then(ClientCommands.argument("current", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                            String current = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "current");
                            var edited = config();
                            edited.usernameAliases.remove(current.toLowerCase(java.util.Locale.ROOT));
                            try { saveConfig(edited); context.getSource().sendFeedback(Component.literal("Removed username alias: " + current)); return 1; }
                            catch (IOException | IllegalArgumentException exception) {
                                context.getSource().sendError(Component.literal("Could not save aliases: " + exception.getMessage())); return 0;
                            }
                        })))
                .then(ClientCommands.literal("list").executes(context -> {
                    context.getSource().sendFeedback(Component.literal("Username aliases: " + new java.util.TreeMap<>(config.usernameAliases))); return 1;
                }));
    }
}
