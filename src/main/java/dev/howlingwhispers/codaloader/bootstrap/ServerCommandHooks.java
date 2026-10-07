package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaCommands;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Registers mod commands on each integrated server, including after leaving and changing worlds. */
final class ServerCommandHooks {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private ServerCommandHooks() {}

    static void start() {
        if (!STARTED.compareAndSet(false, true) || CodaCommands.registrations().isEmpty()) return;
        Thread thread = new Thread(ServerCommandHooks::poll, "CodaLoader-ServerCommands");
        thread.setDaemon(true);
        thread.start();
    }

    private static void poll() {
        AtomicReference<Object> pending = new AtomicReference<>();
        AtomicReference<Object> registeredServer = new AtomicReference<>();
        java.util.Set<Object> failedServers = java.util.Collections.synchronizedSet(
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Class<?> clientType = Class.forName("net.minecraft.client.Minecraft", false, ServerCommandHooks.class.getClassLoader());
                Object client = CommandReflection.call(clientType, "getInstance");
                Object server = client == null ? null : CommandReflection.call(client, "getSingleplayerServer");
                if (server == null) {
                    registeredServer.set(null);
                    pending.set(null);
                    failedServers.clear();
                } else {
                    if (registeredServer.get() != server && !failedServers.contains(server) && pending.get() != server) {
                        pending.set(server);
                        try {
                            CommandReflection.call(server, "execute", (Runnable) () -> {
                                try {
                                    register(server);
                                    registeredServer.set(server);
                                } catch (Exception ex) {
                                    failedServers.add(server);
                                    System.err.println("[CodaLoader] Snapshot 3 command bridge unavailable: " + ex);
                                } finally { pending.compareAndSet(server, null); }
                            });
                        } catch (Exception ex) {
                            pending.compareAndSet(server, null);
                            failedServers.add(server);
                            System.err.println("[CodaLoader] Cannot schedule commands on the server thread: " + ex);
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Minecraft has not loaded yet.
            } catch (Exception ex) {
                System.err.println("[CodaLoader] Snapshot 3 integrated-server lookup failed: " + ex);
                return;
            }
            try { Thread.sleep(1000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
    }

    static void register(Object server) throws Exception {
        Object commands = CommandReflection.call(server, "getCommands");
        Object dispatcher = CommandReflection.call(commands, "getDispatcher");
        ClassLoader loader = server.getClass().getClassLoader();
        Class<?> literalType = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder", true, loader);
        Class<?> argumentType = Class.forName("com.mojang.brigadier.builder.RequiredArgumentBuilder", true, loader);
        Class<?> stringType = Class.forName("com.mojang.brigadier.arguments.StringArgumentType", true, loader);
        Class<?> commandType = Class.forName("com.mojang.brigadier.Command", true, loader);
        int added = 0;
        for (CodaCommands.Registration registration : CodaCommands.registrations()) {
            if (CommandReflection.call(CommandReflection.call(dispatcher, "getRoot"), "getChild", registration.name()) != null) {
                System.err.println("[CodaLoader] /" + registration.name() + " already exists; " + registration.owner() + " did not replace it.");
                continue;
            }
            Object handler = Proxy.newProxyInstance(loader, new Class<?>[]{commandType}, (proxy, method, arguments) -> {
                if (method.getName().equals("toString")) return "CML command /" + registration.name();
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == arguments[0];
                Object brigadierContext = arguments[0];
                Object source = CommandReflection.call(brigadierContext, "getSource");
                try {
                    String raw;
                    try { raw = (String) CommandReflection.call(stringType, "getString", brigadierContext, "arguments"); }
                    catch (IllegalArgumentException ex) { raw = ""; }
                    List<String> args = raw.isBlank() ? List.of() : List.of(raw.trim().split("\\s+"));
                    registration.command().execute(new MinecraftCommandContext(source), args);
                    return 1;
                } catch (Exception ex) {
                    String message = ex instanceof IllegalArgumentException || ex instanceof java.io.IOException
                            ? ex.getMessage() : "Coda couldn't complete that command. Your saved homes were kept. Check the launcher logs.";
                    try { MinecraftCommandContext.sendReply(source, message, true); }
                    catch (Exception replyError) { System.err.println("[CodaLoader] Command reply failed: " + replyError); }
                    System.err.println("[CodaLoader] /" + registration.name() + " failed: " + ex);
                    return 0;
                }
            });
            Object literal = CommandReflection.call(literalType, "literal", registration.name());
            CommandReflection.call(literal, "executes", handler);
            Object args = CommandReflection.call(argumentType, "argument", "arguments", CommandReflection.call(stringType, "greedyString"));
            CommandReflection.call(args, "executes", handler);
            CommandReflection.call(literal, "then", args);
            CommandReflection.call(dispatcher, "register", literal);
            added++;
            System.out.println("[CodaLoader] Registered /" + registration.name() + " from " + registration.owner());
        }
        if (added > 0) {
            Object players = CommandReflection.call(CommandReflection.call(server, "getPlayerList"), "getPlayers");
            for (Object player : (Iterable<?>) players) CommandReflection.call(commands, "sendCommands", player);
        }
    }
}
