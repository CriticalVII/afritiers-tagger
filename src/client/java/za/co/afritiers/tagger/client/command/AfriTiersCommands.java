package za.co.afritiers.tagger.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import za.co.afritiers.tagger.client.AfriTiersClient;
import za.co.afritiers.tagger.client.gui.TaggerSettingsScreen;

import java.io.IOException;
import java.util.Locale;
import java.util.TreeMap;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Client commands delegate to the same validated settings and ranking services as the GUI. */
public final class AfriTiersCommands {
    private final AfriTiersClient mod;

    private AfriTiersCommands(AfriTiersClient mod) {
        this.mod = mod;
    }

    public static void register(AfriTiersClient mod) {
        var commands = new AfriTiersCommands(mod);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("afritiers")
                        .then(commands.aliases())
                        .then(literal("config").requires(FabricClientCommandSource::attended).executes(context -> {
                            Minecraft client = context.getSource().getClient();
                            // Chat closes after command execution; open the screen on the next client task.
                            client.schedule(() -> client.gui.setScreen(new TaggerSettingsScreen(null)));
                            return 1;
                        }))
                        .then(literal("refresh").requires(FabricClientCommandSource::attended).executes(context -> {
                            context.getSource().sendFeedback(Component.literal(mod.repository().refresh()));
                            return 1;
                        }))
                        .then(literal("status").executes(context -> commands.status(context.getSource()))
                                .then(argument("username", StringArgumentType.word()).executes(context ->
                                        commands.playerStatus(context.getSource(), StringArgumentType.getString(context, "username")))))));
    }

    private int status(FabricClientCommandSource source) {
        var state = mod.repository().state();
        var config = mod.config();
        source.sendFeedback(Component.literal("AfriTiers: " + state.message()
                + " | " + state.rankings().players().size() + " usernames"
                + " | ambiguous: " + state.rankings().ambiguousNames()
                + " | Nameplates: " + (config.nameplates ? "ON" : "OFF")
                + " | Tab: " + (config.tabList ? "ON" : "OFF")
                + " | Mode: " + config.mode));
        return 1;
    }

    private int playerStatus(FabricClientCommandSource source, String username) {
        var rankings = mod.repository().state().rankings();
        var config = mod.config();
        String apiName = mod.rankingUsername(username);
        String result = rankings.select(apiName, config.mode, config.includeRetired)
                .map(tier -> tier.label())
                .orElseGet(() -> rankings.players().containsKey(apiName)
                        ? "No tier matches the selected mode/retired settings"
                        : "No matching ranking yet; " + mod.paidNames().status());
        source.sendFeedback(Component.literal("AfriTiers: " + username + " | " + result
                + (apiName.isEmpty() || apiName.equalsIgnoreCase(username) ? "" : " | API name: " + apiName)
                + " | Nameplates: " + (config.nameplates ? "ON" : "OFF")));
        return 1;
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> aliases() {
        return literal("alias")
                .then(literal("set").requires(FabricClientCommandSource::attended)
                        .then(argument("current", StringArgumentType.word())
                                .then(argument("apiName", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            mod.repository().state().rankings().players().keySet().stream().sorted()
                                                    .filter(name -> name.startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> setAlias(context.getSource(),
                                                StringArgumentType.getString(context, "current"),
                                                StringArgumentType.getString(context, "apiName"))))))
                .then(literal("remove").requires(FabricClientCommandSource::attended)
                        .then(argument("current", StringArgumentType.word()).executes(context ->
                                removeAlias(context.getSource(), StringArgumentType.getString(context, "current")))))
                .then(literal("list").executes(context -> {
                    context.getSource().sendFeedback(Component.literal("Username aliases: " + new TreeMap<>(mod.config().usernameAliases)));
                    return 1;
                }));
    }

    private int setAlias(FabricClientCommandSource source, String current, String apiName) {
        var edited = mod.config();
        edited.usernameAliases.put(current.toLowerCase(Locale.ROOT), apiName);
        try {
            mod.saveConfig(edited);
            source.sendFeedback(Component.literal("Saved username alias: " + current + " -> " + apiName));
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            source.sendError(Component.literal("Could not save alias: " + exception.getMessage()));
            return 0;
        }
    }

    private int removeAlias(FabricClientCommandSource source, String current) {
        var edited = mod.config();
        edited.usernameAliases.remove(current.toLowerCase(Locale.ROOT));
        try {
            mod.saveConfig(edited);
            source.sendFeedback(Component.literal("Removed username alias: " + current));
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            source.sendError(Component.literal("Could not save aliases: " + exception.getMessage()));
            return 0;
        }
    }
}
