package za.co.afritiers.tagger.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Team;
import za.co.afritiers.tagger.network.RankingRepository;
import za.co.afritiers.tagger.ranking.Rankings;
import za.co.afritiers.tagger.ranking.ModeIcon;
import za.co.afritiers.tagger.ranking.Tier;

import java.lang.reflect.Field;
import java.util.UUID;

/** Runs inside a real Minecraft client; synthetic API records never enter the shipped mod. */
public final class TaggerClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try { runChecked(context); }
        catch (Exception exception) { throw new RuntimeException("Client test failed", exception); }
    }

    private void runChecked(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            AfriTiersClient mod = AfriTiersClient.instance();
            check(mod != null, "Client entrypoint was not loaded");
            // A production test directory may contain bindings from an earlier test build.
            var settingsKey = net.minecraft.client.KeyMapping.get("key.afritiers_tagger.settings");
            check(settingsKey.getDefaultKey().getValue() == org.lwjgl.glfw.GLFW.GLFW_KEY_K, "Wrong default shortcut");
            settingsKey.setKey(settingsKey.getDefaultKey());
            net.minecraft.client.KeyMapping.resetMapping();
            client.options.guiScale().set(2);
            mod.saveConfig(new za.co.afritiers.tagger.config.TaggerConfig());
            checkPresentationStyles();
            checkEnlargedIcon(client);
            if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                Class<?> modMenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
                Object screen = modMenu.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screens.Screen.class)
                        .invoke(null, "afritiers_tagger", new TitleScreen());
                check(screen instanceof TaggerSettingsScreen, "Mod Menu did not discover the configuration entrypoint");
            }
            // Replace transport only in this test mod; no production endpoint requests are made.
            Field field = AfriTiersClient.class.getDeclaredField("repository");
            field.setAccessible(true);
            mod.repository().close();
            field.set(mod, new RankingRepository(TaggerClientGameTest::fixtureRankings));
            Field browser = AfriTiersClient.class.getDeclaredField("browser");
            browser.setAccessible(true);
            ((RankingRepository) browser.get(mod)).close();
            browser.set(mod, new RankingRepository(TaggerClientGameTest::fixtureRankings));
            Field names = AfriTiersClient.class.getDeclaredField("paidNames");
            names.setAccessible(true);
            mod.paidNames().close();
            names.set(mod, new za.co.afritiers.tagger.network.PaidNameResolver(username -> {
                if (username.equalsIgnoreCase("RenamedPaid")) return java.util.Optional.of(new za.co.afritiers.tagger.network.ProfileLookup.Profile(
                        "11111111111111111111111111111111", username));
                if (username.equalsIgnoreCase("OldPaidName")) return java.util.Optional.of(new za.co.afritiers.tagger.network.ProfileLookup.Profile(
                        "22222222222222222222222222222222", username));
                return java.util.Optional.empty();
            }));
        });
        var reloaded = context.computeOnClient(client -> client.reloadResourcePacks());
        context.waitFor(client -> reloaded.isDone());
        reloaded.join();
        context.waitFor(client -> client.gui.overlay() == null);
        context.runOnClient(TaggerClientGameTest::checkEnlargedIcon);
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            context.runOnClient(client -> {
                try {
                    var container = FabricLoader.getInstance().getModContainer("afritiers_tagger").orElseThrow();
                    String iconPath = container.getMetadata().getIconPath(32).orElseThrow();
                    check(iconPath.equals("assets/afritiers_tagger/icon.png"), "Mod Menu icon metadata missing");
                    check(java.nio.file.Files.size(container.findPath(iconPath).orElseThrow()) > 0, "Mod Menu icon not packaged");
                    var menu = (net.minecraft.client.gui.screens.Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                            .getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(new TitleScreen());
                    client.gui.setScreen(menu);
                    Field search = menu.getClass().getDeclaredField("searchBox");
                    search.setAccessible(true);
                    ((net.minecraft.client.gui.components.EditBox) search.get(menu)).setValue("AfriTiers Tier Tagger");
                    // Mod Menu uses fuzzy search; select the shipped mod explicitly, not this test mod.
                    Field listField = menu.getClass().getDeclaredField("modList");
                    listField.setAccessible(true);
                    Object list = listField.get(menu);
                    for (Object candidate : (java.util.List<?>) list.getClass().getMethod("children").invoke(list)) {
                        Object candidateMod = candidate.getClass().getMethod("getMod").invoke(candidate);
                        if (candidateMod.getClass().getMethod("getId").invoke(candidateMod).equals("afritiers_tagger")) {
                            for (var method : list.getClass().getMethods()) {
                                if (method.getName().equals("select") && method.getParameterCount() == 1
                                        && method.getParameterTypes()[0].isInstance(candidate)) {
                                    method.invoke(list, candidate);
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    Object entry = menu.getClass().getMethod("getSelectedEntry").invoke(menu);
                    check(entry != null, "AfriTiers not present in Mod Menu");
                    Object selectedMod = entry.getClass().getMethod("getMod").invoke(entry);
                    check(selectedMod.getClass().getMethod("getId").invoke(selectedMod).equals("afritiers_tagger"),
                            "Wrong mod selected in Mod Menu");
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            context.waitTicks(10);
            context.takeScreenshot("mod-menu-icon");
        }
        context.setScreen(IconGalleryScreen::new);
        context.waitTicks(5);
        context.takeScreenshot("mode-icons-and-tier-palette");
        context.setScreen(() -> new TaggerSettingsScreen(new TitleScreen()));
        context.waitForScreen(TaggerSettingsScreen.class);
        context.waitTicks(5);
        context.takeScreenshot("settings-default");
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            assertScreenBounds(screen);
            try {
                Field previewField = TaggerSettingsScreen.class.getDeclaredField("preview");
                previewField.setAccessible(true);
                String initialPreview = ((Component) previewField.get(screen)).getString();
                var previews = new java.util.HashSet<String>();
                for (int i = 0; i < 10; i++) {
                    previews.add(((Component) previewField.get(screen)).getString());
                    findButton(screen, ">").onPress(new net.minecraft.client.input.MouseButtonInfo(-1, 0));
                }
                check(previews.size() == 10 && ((Component) previewField.get(screen)).getString().equals(initialPreview),
                        "Preview arrows must cycle all ten ranks without changing the selection");
            } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            check(findEditBox(screen) != null, "Colour field was lost in the redesign");
            findEditBox(screen).setValue("ZZZZZZ");
            check(!findButton(screen, "Save").active, "Invalid colour must disable Save");
        });
        context.waitTicks(2);
        context.takeScreenshot("settings-invalid-colour");
        context.runOnClient(client -> findEditBox(client.gui.screen()).setValue("#12AB34"));
        context.runOnClient(client -> check(findButton(client.gui.screen(), "Save").active, "Valid hex colour did not enable Save"));
        context.clickScreenButton("Details");
        context.waitTicks(3);
        context.takeScreenshot("settings-connection-details");
        context.clickScreenButton("Back");
        context.waitForScreen(TaggerSettingsScreen.class);
        context.runOnClient(client -> {
            check(findEditBox(client.gui.screen()).getValue().equals("#12AB34"), "Opening details lost draft colour");
            findEditBox(client.gui.screen()).setValue("");
            client.options.guiScale().set(3);
        });
        context.waitTicks(3);
        context.runOnClient(client -> assertScreenBounds(client.gui.screen()));
        context.takeScreenshot("settings-compact");
        context.runOnClient(client -> {
            client.gui.screen().resize(320, 240);
            assertScreenBounds(client.gui.screen());
            client.resizeGui();
            client.options.guiScale().set(2);
        });
        click(context, "Enabled: ON");
        context.clickScreenButton("Cancel");
        context.runOnClient(client -> check(AfriTiersClient.instance().config().enabled, "Cancel applied draft settings"));
        context.setScreen(() -> new TaggerSettingsScreen(new TitleScreen()));
        click(context, "Nameplates: ON");
        context.clickScreenButton("Save");
        context.runOnClient(client -> {
            check(!AfriTiersClient.instance().config().nameplates, "Save did not apply settings");
            AfriTiersClient.instance().saveConfig(new za.co.afritiers.tagger.config.TaggerConfig());
        });

        // Search is usable from Mod Menu without joining a world, even when tier tags are disabled.
        context.runOnClient(client -> {
            var config = AfriTiersClient.instance().config(); config.enabled = false;
            try { AfriTiersClient.instance().saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        });
        context.setScreen(() -> new TaggerSettingsScreen(new TitleScreen()));
        context.clickScreenButton("Players");
        context.waitFor(client -> AfriTiersClient.instance().playerSearchRepository().state().phase() == RankingRepository.Phase.READY);
        context.runOnClient(client -> findEditBox(client.gui.screen()).setValue("StatsExplorer"));
        context.clickScreenButton("Search");
        context.runOnClient(client -> check(hasWidgetText(client.gui.screen(), "Combat Master"), "Offline/disabled player search failed"));
        context.takeScreenshot("player-search-outside-world");
        context.clickScreenButton("Back");
        context.clickScreenButton("Cancel");
        context.runOnClient(client -> {
            try { AfriTiersClient.instance().saveConfig(new za.co.afritiers.tagger.config.TaggerConfig()); }
            catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        });

        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> AfriTiersClient.instance().repository().state().phase() == RankingRepository.Phase.READY);
            context.runOnClient(client -> {
                AfriTiersClient.instance().paidNames().request("RenamedPaid");
                AfriTiersClient.instance().paidNames().request("OldPaidName");
            });
            context.waitFor(client -> AfriTiersClient.instance().paidNames().profileId("RenamedPaid").isPresent()
                    && AfriTiersClient.instance().paidNames().profileId("OldPaidName").isPresent());
            context.waitTicks(10);
            context.runOnClient(client -> {
                var tab = client.gui.hud.getTabList();
                var info = new PlayerInfo(new GameProfile(UUID.randomUUID(), "RANKEDFIGHTER"), false);
                info.setTabListDisplayName(Component.literal("[VIP] Nickname"));
                check(tab.getNameForDisplay(info).getString().equals(ModeIcon.MACE.glyph() + " HT3 [VIP] Nickname"), "Tab tag must omit the pipe and preserve server formatting");
                check(tab.getNameForDisplay(new PlayerInfo(new GameProfile(UUID.randomUUID(), "Unranked"), false))
                        .getString().equals("Unranked"), "Unranked player was decorated");
                check(tab.getNameForDisplay(new PlayerInfo(new GameProfile(UUID.randomUUID(), "RenamedPaid"), false)).getString()
                        .equals(ModeIcon.SWORD.glyph() + " HT2 RenamedPaid"), "Paid rename recovery depended on the unrelated server UUID");
                check(tab.getNameForDisplay(new PlayerInfo(new GameProfile(UUID.randomUUID(), "OldPaidName"), false)).getString()
                        .equals("OldPaidName"), "A reused old paid username inherited somebody else's tiers");
                var renamed = new RemotePlayer(client.level, new GameProfile(UUID.randomUUID(), "RenamedPaid"));
                renamed.setId(1_000_003);
                renamed.setPos(client.player.getX(), client.player.getY(), client.player.getZ() + 4);
                check(client.getEntityRenderDispatcher().getPlayerRenderer(renamed).createRenderState(renamed, 1).nameTag.getString()
                        .equals(ModeIcon.SWORD.glyph() + " HT2 | RenamedPaid"), "Renamed player's nameplate was not decorated");

                var player = new RemotePlayer(client.level, new GameProfile(UUID.randomUUID(), "RankedFighter"));
                player.setId(1_000_000);
                player.setPos(client.player.getX(), client.player.getY(), client.player.getZ() + 4);
                client.level.addEntity(player);
                var renderer = client.getEntityRenderDispatcher().getPlayerRenderer(player);
                var state = renderer.createRenderState(player, 1);
                check(state.nameTag != null && state.nameTag.getString().equals(ModeIcon.MACE.glyph() + " HT3 | RankedFighter"), "Nameplate icon tag was not extracted");

                var team = client.level.getScoreboard().addPlayerTeam("afritiers_hidden");
                team.setPlayerPrefix(Component.literal("[VIP] "));
                client.level.getScoreboard().addPlayerToTeam("RankedFighter", team);
                check(renderer.createRenderState(player, 1).nameTag.getString().equals(ModeIcon.MACE.glyph() + " HT3 | [VIP] RankedFighter"), "Nameplate team prefix was lost");
                team.setNameTagVisibility(Team.Visibility.NEVER);
                check(renderer.createRenderState(player, 1).nameTag == null, "Tag bypassed team nameplate hiding");
                checkServerLabels(client, player);
                client.level.getScoreboard().removePlayerTeam(team);
                Component tagged = renderer.createRenderState(player, 1).nameTag;
                check(TagFormatter.decorate("RankedFighter", tagged, false) == tagged, "A reused name component was tagged twice");
            });
            context.getInput().lookAt(0, 0);
            context.waitTicks(5);
            context.takeScreenshot("ranked-nameplate");
            context.runOnClient(client -> {
                try {
                    var listener = client.getConnection();
                    Field mapField = net.minecraft.client.multiplayer.ClientPacketListener.class.getDeclaredField("playerInfoMap");
                    mapField.setAccessible(true);
                    @SuppressWarnings("unchecked") var map = (java.util.Map<UUID, PlayerInfo>) mapField.get(listener);
                    var listed = listener.getListedOnlinePlayers();
                    var ranked = new PlayerInfo(new GameProfile(UUID.randomUUID(), "RankedFighter"), false);
                    ranked.setTabListDisplayName(Component.literal("[VIP] RankedFighter"));
                    map.put(ranked.getProfile().id(), ranked); listed.add(ranked);
                    for (int i = 0; i < ModeIcon.values().length; i++) {
                        var previewInfo = new PlayerInfo(new GameProfile(UUID.randomUUID(), String.format("Fighter%02d", i)), false);
                        map.put(previewInfo.getProfile().id(), previewInfo); listed.add(previewInfo);
                    }
                } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            });
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
            context.waitTicks(5);
            context.takeScreenshot("tablist-icon-preview");
            context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH);
            context.getInput().typeChars("afritiers alias set NewCracked RankedFighter");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            context.waitFor(client -> AfriTiersClient.instance().config().usernameAliases.containsKey("newcracked"));
            context.runOnClient(client -> check(client.gui.hud.getTabList()
                    .getNameForDisplay(new PlayerInfo(new GameProfile(UUID.randomUUID(), "NewCracked"), false)).getString()
                    .equals(ModeIcon.MACE.glyph() + " HT3 NewCracked"), "Saved cracked username alias did not recover the tier"));
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH);
            context.getInput().typeChars("afritiers alias remove NewCracked");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            context.waitFor(client -> !AfriTiersClient.instance().config().usernameAliases.containsKey("newcracked"));
            context.runOnClient(client -> {
                var player = (RemotePlayer) client.level.getEntity(1_000_000);
                var hidden = client.level.getScoreboard().addPlayerTeam("server_labels");
                hidden.setNameTagVisibility(Team.Visibility.NEVER);
                client.level.getScoreboard().addPlayerToTeam("RankedFighter", hidden);
                var stand = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, client.level);
                stand.setId(1_000_001);
                stand.setPos(player.getX(), player.getY() + 0.3, player.getZ());
                stand.setInvisible(true);
                stand.setCustomNameVisible(true);
                stand.setCustomName(Component.literal("[VIP] RankedFighter"));
                stand.setNoGravity(true);
                client.level.addEntity(stand);
            });
            context.waitTicks(3);
            context.takeScreenshot("server-armor-stand-nameplate");
            context.runOnClient(client -> {
                var player = (RemotePlayer) client.level.getEntity(1_000_000);
                client.level.getEntity(1_000_001).discard();
                var text = new net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY, client.level);
                text.setId(1_000_002);
                text.setPos(player.getX(), player.getY() + 2.5, player.getZ());
                text.setBillboardConstraints(net.minecraft.world.entity.Display.BillboardConstraints.CENTER);
                text.setText(Component.literal("[VIP] RankedFighter"));
                client.level.addEntity(text);
            });
            context.waitTicks(3);
            context.takeScreenshot("server-text-display-nameplate");
            context.runOnClient(client -> {
                client.level.getEntity(1_000_002).discard();
                client.level.getScoreboard().removePlayerTeam(client.level.getScoreboard().getPlayerTeam("server_labels"));
                var mod = AfriTiersClient.instance();
                var config = mod.config();
                config.nameplates = false;
                try { mod.saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
                var player = (RemotePlayer) client.level.getEntity(1_000_000);
                check(client.getEntityRenderDispatcher().getPlayerRenderer(player).createRenderState(player, 1)
                        .nameTag.getString().equals("RankedFighter"), "Nameplate OFF setting was ignored");
                check(client.gui.hud.getTabList().getNameForDisplay(new PlayerInfo(player.getGameProfile(), false)).getString()
                        .equals(ModeIcon.MACE.glyph() + " HT3 RankedFighter"), "Nameplate toggle disabled Tab tags");
                config.nameplates = true;
                try { mod.saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            });
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_K);
            context.waitForScreen(TaggerSettingsScreen.class);
            context.waitTicks(3);
            context.takeScreenshot("settings-connected");
            checkPlayerSearchAndPlacements(context);
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH);
            context.getInput().typeChars("afritiers config");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            context.waitForScreen(TaggerSettingsScreen.class);
            context.clickScreenButton("Cancel");
        }
        AfriTiersClient.LOGGER.info("AFRITIERS_CLIENT_TEST_OK modmenu={}", FabricLoader.getInstance().isModLoaded("modmenu"));
    }

    private static void checkPlayerSearchAndPlacements(ClientGameTestContext context) {
        context.runOnClient(client -> findEditBox(client.gui.screen()).setValue("#34AB56"));
        context.clickScreenButton("Players");
        context.waitForScreen(PlayerSearchScreen.class);
        context.runOnClient(client -> {
            check(!findButton(client.gui.screen(), "Search").active, "Empty username enabled search");
            findEditBox(client.gui.screen()).setValue("bad name");
            check(!findButton(client.gui.screen(), "Search").active, "Invalid username enabled search");
            findEditBox(client.gui.screen()).setValue("sTaTsExPlOrEr");
        });
        context.clickScreenButton("Search");
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(3);
        context.runOnClient(client -> {
            assertScreenBounds(client.gui.screen());
            check(hasWidgetText(client.gui.screen(), "Region: NA"), "Player region missing");
            check(hasWidgetText(client.gui.screen(), "Combat Master"), "Combat title/points missing");
            check(hasWidgetText(client.gui.screen(), "Overall rank: #1"), "Overall placement missing");
            check(hasWidgetText(client.gui.screen(), "(retired)"), "Retired tiers were not labelled in stats");
        });
        context.takeScreenshot("player-search-stats");
        context.getInput().setCursorPos(700, 450);
        context.getInput().scroll(-6);
        context.waitTicks(3);
        context.getInput().setCursorPos(0, 0);
        context.takeScreenshot("player-search-scrolled");
        context.runOnClient(client -> {
            findEditBox(client.gui.screen()).setValue("RenamedPaid");
            findEditBox(client.gui.screen()).setFocused(true);
        });
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        context.waitTicks(3);
        context.runOnClient(client -> check(hasWidgetText(client.gui.screen(), "API name: OldPaidName"), "Player search did not recover a paid rename"));
        context.takeScreenshot("player-search-renamed");
        context.runOnClient(client -> findEditBox(client.gui.screen()).setValue("NobodyRanked"));
        context.clickScreenButton("Search");
        context.waitTicks(3);
        context.runOnClient(client -> check(!hasWidgetText(client.gui.screen(), "Region:"), "Not-found search retained another player's stats"));
        context.takeScreenshot("player-search-not-found");
        context.runOnClient(client -> findEditBox(client.gui.screen()).setValue("StatsExplorer"));
        context.clickScreenButton("Search");
        context.runOnClient(client -> client.options.guiScale().set(3));
        context.waitTicks(3);
        context.runOnClient(client -> {
            assertScreenBounds(client.gui.screen());
            client.gui.screen().resize(320, 240);
            assertScreenBounds(client.gui.screen());
        });
        context.takeScreenshot("player-search-compact");
        context.runOnClient(client -> { client.resizeGui(); client.options.guiScale().set(2); });
        context.clickScreenButton("Settings");
        context.waitForScreen(TaggerSettingsScreen.class);
        context.runOnClient(client -> {
            check(findEditBox(client.gui.screen()).getValue().equals("#34AB56"), "Player tab lost unsaved settings");
            findEditBox(client.gui.screen()).setValue("");
        });
        click(context, "Top 10 rank: OFF");
        context.clickScreenButton("Save");
        context.runOnClient(client -> {
            var mod = AfriTiersClient.instance();
            check(mod.config().showTopTen, "Top-ten setting did not persist");
            var rankings = mod.repository().state().rankings();
            int position = rankings.topTenPosition("Fighter00");
            check(position > 0, "Missing top-ten fixture");
            String prefix = "#" + position + " ";
            Component tagged = TagFormatter.decorate("Fighter00", Component.literal("Fighter00"), true);
            check(tagged.getString().equals(prefix + ModeIcon.AXE.glyph() + " HT1 Fighter00"), "Top-ten Tab prefix is wrong");
            check(TagFormatter.decorate("Fighter00", tagged, true) == tagged, "Placement was applied twice");
            check(TagFormatter.decorate("Fighter00", Component.literal("Fighter00"), false).getString()
                    .equals(prefix + ModeIcon.AXE.glyph() + " HT1 | Fighter00"), "Top-ten nameplate lost the pipe");
            var outsider = rankings.stats().entrySet().stream().filter(entry -> entry.getValue().position() == 11).findFirst().orElseThrow();
            String outsideTag = TagFormatter.decorate(outsider.getKey(), Component.literal("Outside"), true).getString();
            check(!outsideTag.startsWith("#11 "), "Eleventh place received a placement prefix");
            var config = mod.config(); config.mode = "missing_mode";
            try { mod.saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            var rankOnly = TagFormatter.decorate("Fighter00", Component.literal("Fighter00"), true);
            check(rankOnly.getString().equals(prefix + "Fighter00"), "Overall rank depended on the selected mode");
            check(TagFormatter.decorate("Fighter00", rankOnly, true) == rankOnly, "Rank-only component was applied twice");
            config.mode = "strongest";
            try { mod.saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            checkTabColumns(client);
        });
        context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
        context.waitTicks(3);
        context.takeScreenshot("top-ten-tablist");
        context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
        context.runOnClient(client -> {
            var existing = client.level.getEntity(1_000_000);
            existing.setPos(existing.getX() + 10, existing.getY(), existing.getZ());
            var top = new RemotePlayer(client.level, new GameProfile(UUID.randomUUID(), "Fighter00"));
            top.setId(1_000_004); top.setPos(client.player.getX(), client.player.getY(), client.player.getZ() + 4);
            client.level.addEntity(top);
        });
        context.waitTicks(3);
        context.takeScreenshot("top-ten-nameplate");
        context.runOnClient(client -> {
            client.level.getEntity(1_000_004).discard();
            var config = AfriTiersClient.instance().config(); config.showTopTen = false;
            try { AfriTiersClient.instance().saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        });
    }

    private static boolean hasWidgetText(net.minecraft.client.gui.components.events.ContainerEventHandler container, String text) {
        for (var child : container.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getMessage().getString().contains(text)) return true;
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested && hasWidgetText(nested, text)) return true;
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Rankings fixtureRankings() {
        var root = new com.google.gson.JsonObject();
        root.addProperty("stale", false);
        var players = new com.google.gson.JsonArray();
        root.add("players", players);
        for (int i = -1; i < ModeIcon.values().length; i++) {
            var player = new com.google.gson.JsonObject();
            player.addProperty("name", i < 0 ? "RankedFighter" : String.format("Fighter%02d", i));
            player.addProperty("accountType", "cracked");
            player.addProperty("minecraftProfileUuid", "00000000000000000000000000000000");
            var tiers = new com.google.gson.JsonArray();
            var tier = new com.google.gson.JsonObject();
            tier.addProperty("mode", i < 0 ? "mace" : ModeIcon.values()[i].texture());
            tier.addProperty("level", i < 0 ? "HT3" : Tier.ORDER.get(i % Tier.ORDER.size()));
            tier.addProperty("retired", false);
            tiers.add(tier); player.add("tiers", tiers); players.add(player);
        }
        var paid = com.google.gson.JsonParser.parseString("""
            {"name":"OldPaidName","accountType":"paid","minecraftProfileUuid":"11111111111111111111111111111111",
             "tiers":[{"mode":"sword","level":"HT2","retired":false}]}
            """).getAsJsonObject();
        players.add(paid);
        var explorer = new com.google.gson.JsonObject();
        explorer.addProperty("name", "StatsExplorer"); explorer.addProperty("region", "NA");
        explorer.addProperty("accountType", "cracked");
        var explorerTiers = new com.google.gson.JsonArray();
        for (int i = 0; i < ModeIcon.values().length; i++) {
            var tier = new com.google.gson.JsonObject();
            tier.addProperty("mode", ModeIcon.values()[i].texture());
            tier.addProperty("level", Tier.ORDER.get(i % Tier.ORDER.size()));
            tier.addProperty("retired", i == 0);
            explorerTiers.add(tier);
        }
        explorer.add("tiers", explorerTiers); players.add(explorer);
        return Rankings.parse(root.toString());
    }

    private static void checkEnlargedIcon(net.minecraft.client.Minecraft client) {
        for (int pixels : new int[]{1,2,3,4,9,10,13,31,64,127,255}) {
            check(client.font.width(TabColumns.spaces(pixels)) == pixels, "Tab spacing must reserve the exact pixel advance");
        }
        for (var icon : ModeIcon.values()) {
            check(client.font.width(ModeIcons.component(icon.texture())) == 13, "Nameplate icon advance must reserve 13 pixels: " + icon);
            check(client.font.width(ModeIcons.component(icon.texture(), true)) == 10, "Tab icon advance must reserve 10 pixels: " + icon);
            var tabText = client.font.prepareText(ModeIcons.component(icon.texture(), true).getVisualOrderText(), 0, 0, 0xFFFFFFFF, false, false, 0);
            tabText.visit(new net.minecraft.client.gui.Font.GlyphVisitor() {
                @Override public void acceptRenderable(net.minecraft.client.gui.font.TextRenderable glyph) {
                    check(glyph.top() >= 0 && glyph.bottom() <= 9, "Tab artwork overlaps another row: " + icon);
                }
            });
        }
        var vanillaSprite = Component.literal("x").withStyle(style -> style.withFont(
                new net.minecraft.network.chat.FontDescription.AtlasSprite(net.minecraft.data.AtlasIds.GUI,
                        net.minecraft.resources.Identifier.withDefaultNamespace("icon/ping_5"))));
        check(client.font.width(vanillaSprite) == 8, "A vanilla sprite was enlarged by the mod");
        var iconText = client.font.prepareText(ModeIcons.component("mace").getVisualOrderText(), 0, 0, 0xFFFFFFFF, false, false, 0);
        iconText.visit(new net.minecraft.client.gui.Font.GlyphVisitor() {
            @Override public void acceptRenderable(net.minecraft.client.gui.font.TextRenderable glyph) {
                check(Math.abs(glyph.right() - glyph.left() - 13) < 0.01F, "Icon bounds have the wrong width");
                check(Math.abs(glyph.bottom() - glyph.top() - 13) < 0.01F, "Icon bounds have the wrong height");
            }
        });
    }

    private static void checkTabColumns(net.minecraft.client.Minecraft client) {
        var infos = java.util.List.of("Fighter00", "Fighter13", "StatsExplorer", "RankedFighter", "Unranked").stream()
                .map(name -> new PlayerInfo(new GameProfile(UUID.randomUUID(), name), false)).toList();
        var compact = new java.util.HashMap<String, Component>();
        for (var info : infos) compact.put(info.getProfile().name(), client.gui.hud.getTabList().getNameForDisplay(info));
        TabColumns.begin(infos);
        try {
            check(TabColumns.active(), "Tab columns were not initialized");
            int nameStart = -1;
            for (var info : infos) {
                String name = info.getProfile().name();
                Component text = client.gui.hud.getTabList().getNameForDisplay(info);
                int prefixWidth = client.font.width(text) - client.font.width(name);
                int position = AfriTiersClient.instance().repository().state().rankings().topTenPosition(name);
                if (position > 0) {
                    if (nameStart < 0) nameStart = prefixWidth;
                    check(prefixWidth == nameStart, "Top-ten Tab username columns differ: " + name);
                } else {
                    check(text.getString().equals(compact.get(name).getString()), "Non-top-ten player received indentation: " + name);
                    check(client.font.width(text) == client.font.width(compact.get(name)), "Non-top-ten spacing changed: " + name);
                }
                check(TagFormatter.decorate(name, text, true) == text, "Aligned Tab component was indented twice");
                check(!text.getString().contains("|"), "Tab columns introduced a pipe");
            }
        } finally { TabColumns.end(); }
        check(!TabColumns.active(), "Tab column scope leaked outside the Tab render");
        var config = AfriTiersClient.instance().config();
        config.showTopTen = false;
        try { AfriTiersClient.instance().saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        TabColumns.begin(infos);
        check(!TabColumns.active(), "Disabling top-ten ranks left indentation enabled");
        config.showTopTen = true;
        try { AfriTiersClient.instance().saveConfig(config); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
    }

    private static void checkPresentationStyles() {
        var config = new za.co.afritiers.tagger.config.TaggerConfig();
        for (int position = 1; position <= 10; position++) {
            Component placement = TagFormatter.placement(position);
            check(placement.getString().equals("#" + position + " "), "Placement must use #rank format");
            placement.visit((style, text) -> {
                check(style.getColor().getValue() == 0xFFD700, "Every placement must use HT1 gold");
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
        }
        check(TagFormatter.placement(11).getString().isEmpty(), "Placement beyond top ten must be hidden");
        for (String rank : Tier.ORDER) {
            Component tag = TagFormatter.tag(new Tier("mace", rank, false), config);
            check(tag.getString().equals(ModeIcon.MACE.glyph() + " " + rank + " | "), "Tag must use icon, tier, separator with no brackets");
            tag.visit((style, text) -> {
                if (text.equals(ModeIcon.MACE.glyph())) {
                    check(style.getColor().getValue() == 0xFFFFFF, "Tier colour tinted the icon");
                    check(Integer.valueOf(0).equals(style.getShadowColor()), "Icon must not have an added shadow or border");
                    check(style.getFont() instanceof net.minecraft.network.chat.FontDescription.AtlasSprite font
                            && font.atlasId().equals(net.minecraft.data.AtlasIds.GUI)
                            && font.spriteId().toString().equals("afritiers_tagger:mode/remade/mace"), "Remade mace asset is not used");
                }
                if (text.equals(rank)) {
                    check(style.getColor().getValue() == config.colour(rank), "Rank colour is wrong");
                    check(style.getFont().equals(net.minecraft.network.chat.FontDescription.DEFAULT), "Icon font leaked into tier text");
                }
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
        }
        config.customColour = "123456";
        TagFormatter.tag(new Tier("mace", "LT1", false), config).visit((style, text) -> {
            if (text.equals(ModeIcon.MACE.glyph())) check(style.getColor().getValue() == 0xFFFFFF, "Custom colour tinted the icon");
            if (text.equals("LT1")) check(style.getColor().getValue() == 0x123456, "Explicit custom text colour was lost");
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
    }

    private static void checkServerLabels(net.minecraft.client.Minecraft client, RemotePlayer player) {
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, client.level);
        stand.setId(1_000_001);
        stand.setPos(player.getX(), player.getY() + 0.4, player.getZ());
        stand.setInvisible(true);
        stand.setCustomNameVisible(true);
        Component serverText = Component.literal("[VIP] rAnKeDfIgHtEr").withColor(0x55FFFF);
        stand.setCustomName(serverText);
        var renderer = client.getEntityRenderDispatcher().getRenderer(stand);
        check(renderer.createRenderState(stand, 1).nameTag.getString().equals(ModeIcon.MACE.glyph() + " HT3 | [VIP] rAnKeDfIgHtEr"),
                "Server armor-stand nameplate was not decorated while vanilla player name was hidden");
        check(stand.getCustomName() == serverText, "Server armor-stand data was mutated");
        stand.setPos(player.getX() + 5, player.getY() + 0.4, player.getZ());
        check(renderer.createRenderState(stand, 1).nameTag.getString().equals(serverText.getString()), "Unrelated hologram was decorated");
        stand.setPos(player.getX(), player.getY() + 0.4, player.getZ());
        stand.setCustomName(Component.literal("RankedFighterExtra"));
        check(renderer.createRenderState(stand, 1).nameTag.getString().equals("RankedFighterExtra"), "Partial username was matched");
        stand.setCustomNameVisible(false);
        check(renderer.createRenderState(stand, 1).nameTag == null, "Hidden armor-stand name was made visible");

        var text = new net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY, client.level);
        text.setId(1_000_002);
        text.setPos(player.getX(), player.getY() + 2.2, player.getZ());
        text.setText(serverText);
        text.tick();
        var displayRenderer = client.getEntityRenderDispatcher().getRenderer(text);
        var state = (net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState) displayRenderer.createRenderState(text, 1);
        check(state.cachedInfo != null, "Text display did not initialize");
        StringBuilder rendered = new StringBuilder();
        for (var line : state.cachedInfo.lines()) line.contents().accept((index, style, codepoint) -> {
            rendered.appendCodePoint(codepoint);
            return true;
        });
        check(rendered.toString().equals(ModeIcon.MACE.glyph() + " HT3 | [VIP] rAnKeDfIgHtEr"), "Server text-display nameplate was not decorated");
        check(text.getText() == serverText, "Server text display data was mutated");
    }

    /** Screenshot-only gallery; no test UI is packaged in the installable mod. */
    private static final class IconGalleryScreen extends net.minecraft.client.gui.screens.Screen {
        IconGalleryScreen() { super(Component.literal("AfriTiers mode icons")); }

        @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.fill(8, 8, width - 8, height - 8, 0xF0141820);
            graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
            ModeIcon[] icons = ModeIcon.values();
            for (int i = 0; i < icons.length; i++) {
                ModeIcon icon = icons[i];
                int x = 18 + (i / 9) * ((width - 36) / 2);
                int y = 38 + (i % 9) * 15;
                Component line = Component.empty().append(ModeIcons.component(icon.texture())).append(" " + icon.displayName());
                graphics.text(font, line, x, y, 0xFFFFFFFF);
            }
            var config = new za.co.afritiers.tagger.config.TaggerConfig();
            for (int tier = 1; tier <= 5; tier++) {
                int x = 16 + (tier - 1) * ((width - 32) / 5);
                graphics.text(font, TagFormatter.tag(new Tier("mace", "HT" + tier, false), config), x, 186, 0xFFFFFFFF);
                graphics.text(font, TagFormatter.tag(new Tier("mace", "LT" + tier, false), config), x, 203, 0xFFFFFFFF);
            }
        }
    }

    private static void click(ClientGameTestContext context, String text) {
        context.runOnClient(client -> check(clickNested(client.gui.screen(), text), "Missing nested button: " + text));
    }

    private static boolean clickNested(net.minecraft.client.gui.components.events.ContainerEventHandler container, String text) {
        for (var child : container.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(text)) {
                button.onPress(new net.minecraft.client.input.MouseButtonInfo(-1, 0));
                return true;
            }
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested && clickNested(nested, text)) return true;
        }
        return false;
    }

    private static net.minecraft.client.gui.components.Button findButton(net.minecraft.client.gui.components.events.ContainerEventHandler container, String text) {
        for (var child : container.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(text)) return button;
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested) {
                var found = findButton(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static net.minecraft.client.gui.components.EditBox findEditBox(net.minecraft.client.gui.components.events.ContainerEventHandler container) {
        for (var child : container.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox field) return field;
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested) {
                var found = findEditBox(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertScreenBounds(net.minecraft.client.gui.screens.Screen screen) {
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                    "Settings widget leaves the viewport: " + widget.getMessage().getString());
        }
    }
}
