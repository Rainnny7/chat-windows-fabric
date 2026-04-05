package me.braydon.window.chat;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.regex.PatternSyntaxException;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ChatWindowsCommands {
    private ChatWindowsCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("chatwindow")
                        .then(literal("create")
                                .then(argument("id", StringArgumentType.word())
                                        .then(argument("pattern", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ChatWindowManager mgr = ChatWindowManager.get();
                                                    mgr.pushClientCommandFeedback();
                                                    try {
                                                        String id = StringArgumentType.getString(ctx, "id");
                                                        String pattern = StringArgumentType.getString(ctx, "pattern");
                                                        try {
                                                            boolean replaced = mgr.createWindow(id, pattern);
                                                            ctx.getSource().sendFeedback(Component.literal(
                                                                    replaced ? "Updated chat window '" + id + "'" : "Created chat window '" + id + "'"));
                                                            ctx.getSource().sendFeedback(Component.literal(
                                                                    "Match text: type it literally (+ . * etc. are not special). For a Java regex, use regex: e.g. regex:.*join.*"));
                                                        } catch (PatternSyntaxException e) {
                                                            ctx.getSource().sendError(Component.literal("Invalid pattern: " + e.getMessage()));
                                                            return 0;
                                                        }
                                                        return 1;
                                                    } finally {
                                                        mgr.popClientCommandFeedback();
                                                    }
                                                }))))
                        .then(literal("add-pattern")
                                .then(argument("id", StringArgumentType.word())
                                        .suggests(idSuggestions())
                                        .then(argument("pattern", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ChatWindowManager mgr = ChatWindowManager.get();
                                                    mgr.pushClientCommandFeedback();
                                                    try {
                                                        String id = StringArgumentType.getString(ctx, "id");
                                                        String pattern = StringArgumentType.getString(ctx, "pattern");
                                                        if (!mgr.hasWindow(id)) {
                                                            ctx.getSource().sendError(Component.literal("Unknown window '" + id + "'"));
                                                            return 0;
                                                        }
                                                        try {
                                                            mgr.addPattern(id, pattern);
                                                        } catch (PatternSyntaxException e) {
                                                            ctx.getSource().sendError(Component.literal("Invalid pattern: " + e.getMessage()));
                                                            return 0;
                                                        }
                                                        ctx.getSource().sendFeedback(Component.literal(
                                                                "Added pattern to '" + id + "' (now " + mgr.getWindow(id).getPatternCount() + " patterns)"));
                                                        return 1;
                                                    } finally {
                                                        mgr.popClientCommandFeedback();
                                                    }
                                                }))))
                        .then(literal("list-patterns")
                                .then(argument("id", StringArgumentType.word())
                                        .suggests(idSuggestions())
                                        .executes(ctx -> {
                                            ChatWindowManager mgr = ChatWindowManager.get();
                                            mgr.pushClientCommandFeedback();
                                            try {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                ChatWindow w = mgr.getWindow(id);
                                                if (w == null) {
                                                    ctx.getSource().sendError(Component.literal("Unknown window '" + id + "'"));
                                                    return 0;
                                                }
                                                int n = w.getPatternCount();
                                                if (n == 0) {
                                                    ctx.getSource().sendFeedback(Component.literal("No patterns (unexpected)."));
                                                    return 1;
                                                }
                                                ctx.getSource().sendFeedback(Component.literal("Patterns for '" + id + "':"));
                                                int i = 1;
                                                for (String src : w.getPatternSources()) {
                                                    ctx.getSource().sendFeedback(Component.literal(i + ". /" + src + "/"));
                                                    i++;
                                                }
                                                return 1;
                                            } finally {
                                                mgr.popClientCommandFeedback();
                                            }
                                        })))
                        .then(literal("remove-pattern")
                                .then(argument("id", StringArgumentType.word())
                                        .suggests(idSuggestions())
                                        .then(argument("position", IntegerArgumentType.integer(1))
                                                .suggests(patternPositionSuggestions())
                                                .executes(ctx -> {
                                                    ChatWindowManager mgr = ChatWindowManager.get();
                                                    mgr.pushClientCommandFeedback();
                                                    try {
                                                        String id = StringArgumentType.getString(ctx, "id");
                                                        int pos = IntegerArgumentType.getInteger(ctx, "position");
                                                        if (!mgr.removePattern(id, pos)) {
                                                            ctx.getSource().sendError(Component.literal(
                                                                    "Could not remove pattern " + pos + " (bad index or last pattern)."));
                                                            return 0;
                                                        }
                                                        ctx.getSource().sendFeedback(Component.literal("Removed pattern " + pos + " from '" + id + "'"));
                                                        return 1;
                                                    } finally {
                                                        mgr.popClientCommandFeedback();
                                                    }
                                                }))))
                        .then(literal("position")
                                .then(argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ChatWindowManager.get().getWindowIds(), builder))
                                        .executes(ctx -> {
                                            ChatWindowManager mgr = ChatWindowManager.get();
                                            mgr.pushClientCommandFeedback();
                                            try {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                if (!mgr.hasWindow(id)) {
                                                    ctx.getSource().sendError(Component.literal("Unknown window '" + id + "'"));
                                                    return 0;
                                                }
                                                mgr.togglePosition(id);
                                                boolean on = mgr.getWindow(id).isPositioningMode();
                                                ctx.getSource().sendFeedback(Component.literal(
                                                        on
                                                                ? "Position mode ON — drag center to move; top/right/corner to resize; Escape to exit"
                                                                : "Position mode OFF for '" + id + "'"));
                                                return 1;
                                            } finally {
                                                mgr.popClientCommandFeedback();
                                            }
                                        })))
                        .then(literal("toggle")
                                .then(argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ChatWindowManager.get().getWindowIds(), builder))
                                        .executes(ctx -> {
                                            ChatWindowManager mgr = ChatWindowManager.get();
                                            mgr.pushClientCommandFeedback();
                                            try {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                if (!mgr.toggleVisibility(id)) {
                                                    ctx.getSource().sendError(Component.literal("Unknown window '" + id + "'"));
                                                    return 0;
                                                }
                                                String state = mgr.getWindow(id).isVisible() ? "visible" : "hidden";
                                                ctx.getSource().sendFeedback(
                                                        Component.literal("Window '" + id + "' is now " + state));
                                                return 1;
                                            } finally {
                                                mgr.popClientCommandFeedback();
                                            }
                                        })))
                        .then(literal("remove")
                                .then(argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ChatWindowManager.get().getWindowIds(), builder))
                                        .executes(ctx -> {
                                            ChatWindowManager mgr = ChatWindowManager.get();
                                            mgr.pushClientCommandFeedback();
                                            try {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                if (!mgr.removeWindow(id)) {
                                                    ctx.getSource().sendError(Component.literal("Unknown window '" + id + "'"));
                                                    return 0;
                                                }
                                                ctx.getSource().sendFeedback(Component.literal("Removed chat window '" + id + "'"));
                                                return 1;
                                            } finally {
                                                mgr.popClientCommandFeedback();
                                            }
                                        })))
                        .then(literal("list")
                                .executes(ctx -> {
                                    ChatWindowManager mgr = ChatWindowManager.get();
                                    mgr.pushClientCommandFeedback();
                                    try {
                                        if (mgr.getWindows().isEmpty()) {
                                            ctx.getSource().sendFeedback(Component.literal("No chat windows."));
                                            return 1;
                                        }
                                        for (ChatWindow w : mgr.getWindows()) {
                                            String pos = w.isPositioningMode() ? " [positioning]" : "";
                                            String vis = w.isVisible() ? "visible" : "hidden";
                                            int pc = w.getPatternCount();
                                            String re = w.getPrimaryRegexSource();
                                            if (re.length() > 32) {
                                                re = re.substring(0, 29) + "...";
                                            }
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "'" + w.getId() + "' — " + vis + pos + " — " + pc + " pattern(s), first: /" + re + "/"));
                                        }
                                        return 1;
                                    } finally {
                                        mgr.popClientCommandFeedback();
                                    }
                                }))));
    }

    private static SuggestionProvider<FabricClientCommandSource> idSuggestions() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(ChatWindowManager.get().getWindowIds(), builder);
    }

    private static SuggestionProvider<FabricClientCommandSource> patternPositionSuggestions() {
        return (ctx, builder) -> {
            try {
                String id = StringArgumentType.getString(ctx, "id");
                ChatWindow w = ChatWindowManager.get().getWindow(id);
                if (w != null) {
                    for (int i = 1; i <= w.getPatternCount(); i++) {
                        builder.suggest(String.valueOf(i));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // id not parsed yet
            }
            return builder.buildFuture();
        };
    }
}
