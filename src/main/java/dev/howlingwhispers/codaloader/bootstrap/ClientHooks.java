package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import dev.howlingwhispers.codaloader.api.CodaScreens;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Array;

/**
 * Reflection-only CML client hooks and Coda-flavoured title/pause menus.
 *
 * This intentionally avoids Fabric/Forge/Mixin. It waits for the readable Mojang client
 * classes, updates the window title, and tailors the native title/pause controls.
 */
final class ClientHooks {
    private static volatile boolean titleFailureReported;
    private static volatile boolean titleSuccessReported;
    private static volatile boolean menuFailureReported;
    private static volatile boolean screenDiscoveryReported;
    private static volatile boolean schedulerReported;
    private static volatile boolean screenSearchReported;
    private static volatile Object injectedTitleScreen;
    private static volatile Object[] injectedMenuWidgets = new Object[0];
    private static final MenuSceneVisits sceneVisits = new MenuSceneVisits();
    private static volatile boolean sceneReloadFailureReported;

    private ClientHooks() {}

    static void start(int modCount) {
        Thread thread = new Thread(() -> run(modCount), "CodaLoader-ClientHooks");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(int modCount) {
        try {
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft", false, loader);
            Object minecraft = waitForMinecraft(minecraftClass);
            if (minecraft == null) {
                System.err.println("[CodaLoader] Client hook timed out waiting for Minecraft instance.");
                return;
            }

            System.out.println("[CodaLoader] Client hook connected to net.minecraft.client.Minecraft.");

            Class<?> screenClass = Class.forName(
                    "net.minecraft.client.gui.screens.Screen",
                    false,
                    loader);

            int screenSearchMisses = 0;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    applyWindowTitle(minecraft);

                    Object screen = findActiveScreen(minecraft, screenClass);
                    boolean isTitleScreen = screen != null
                            && "net.minecraft.client.gui.screens.TitleScreen".equals(screen.getClass().getName());

                    // Initialization often runs before Minecraft assigns its active screen.
                    // Retry ownership only after the screen is visibly active, on the GUI thread.
                    if (screen != null) {
                        String screenId = isTitleScreen ? CodaScreens.TITLE
                                : "net.minecraft.client.gui.screens.PauseScreen".equals(screen.getClass().getName())
                                ? CodaScreens.PAUSE : null;
                        if (screenId != null && !CodaScreens.global().providers(screenId).isEmpty()) {
                            Object target = screen;
                            schedule(minecraft, () -> {
                                if (findActiveScreen(minecraft, screenClass) == target)
                                    CodaScreenBridge.afterNativeInitialize(target, screenId);
                            });
                        }
                    }

                    if (sceneVisits.observe(isTitleScreen, hasClientWorld(minecraft))) {
                        Object target = screen;
                        schedule(minecraft, () -> {
                            if (findActiveScreen(minecraft, screenClass) == target && !hasClientWorld(minecraft))
                                switchMenuScene(minecraft);
                        });
                    }

                    if (screen == null) {
                        screenSearchMisses++;
                        if (!screenSearchReported && screenSearchMisses >= 8) {
                            screenSearchReported = true;
                            System.out.println("[CodaLoader] Title-screen search is still probing Snapshot 3 client state...");
                        }
                    } else if (isTitleScreen) {

                        if (!screenDiscoveryReported) {
                            screenDiscoveryReported = true;
                            System.out.println("[CodaLoader] Minecraft title screen located.");
                        }

                        boolean needsWidgets = screen != injectedTitleScreen
                                || injectedMenuWidgets.length != 3
                                || !allWidgetsPresent(screen, injectedMenuWidgets);

                        if (needsWidgets) {
                            Object target = screen;
                            schedule(minecraft, () -> {
                                if (findActiveScreen(minecraft, screenClass) != target) return;
                                if (injectedTitleScreen == target && injectedMenuWidgets.length == 3
                                        && allWidgetsPresent(target, injectedMenuWidgets)) return;
                                Object[] widgets = injectMenuWidgets(target, modCount);
                                if (widgets.length == 3) {
                                    injectedTitleScreen = target;
                                    injectedMenuWidgets = widgets;
                                }
                            });
                        }
                    }
                } catch (Throwable ex) {
                    if (!menuFailureReported) {
                        menuFailureReported = true;
                        System.err.println("[CodaLoader] Menu hook warning: " + ex);
                    }
                }

                Thread.sleep(750);
            }
        } catch (Throwable ex) {
            System.err.println("[CodaLoader] Client hook failed:");
            ex.printStackTrace(System.err);
        }
    }

    private static boolean hasClientWorld(Object minecraft) {
        Field level = findField(minecraft.getClass(), "level");
        if (level == null) return false; // Missing API must not cause unwanted resource reloads.
        try {
            level.setAccessible(true);
            return level.get(minecraft) != null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static void switchMenuScene(Object minecraft) {
        try {
            String configuredRoot = System.getProperty("codaloader.root", "run");
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            String selected = MenuSceneManager.activateRandom(root, true);
            if (selected == null) {
                System.out.println("[CodaLoader] No alternate menu scene is available.");
                return;
            }

            System.out.println("[CodaLoader] Menu visit selected scene: " + selected);

            Method reload = findMethod(minecraft.getClass(), "reloadResourcePacks");
            if (reload == null) {
                for (Class<?> current = minecraft.getClass();
                     current != null && reload == null;
                     current = current.getSuperclass()) {
                    for (Method method : current.getDeclaredMethods()) {
                        String name = method.getName().toLowerCase();
                        if (method.getParameterCount() == 0
                                && name.contains("reload")
                                && name.contains("resource")) {
                            method.setAccessible(true);
                            reload = method;
                            break;
                        }
                    }
                }
            }

            if (reload == null) {
                throw new NoSuchMethodException("Minecraft.reloadResourcePacks()");
            }

            Object result = reload.invoke(minecraft);
            System.out.println("[CodaLoader] Requested resource reload for menu scene: "
                    + selected
                    + (result == null ? "" : " (" + result.getClass().getSimpleName() + ")"));
        } catch (Throwable ex) {
            if (!sceneReloadFailureReported) {
                sceneReloadFailureReported = true;
                System.err.println("[CodaLoader] Menu scene switch warning:");
                ex.printStackTrace(System.err);
            }
        }
    }

    private static Object waitForMinecraft(Class<?> minecraftClass) throws Exception {
        Method getInstance = minecraftClass.getMethod("getInstance");
        for (int i = 0; i < 240; i++) {
            Object instance = getInstance.invoke(null);
            if (instance != null) return instance;
            Thread.sleep(250);
        }
        return null;
    }

    private static void applyWindowTitle(Object minecraft) {
        try {
            Object window;
            try {
                Method getWindow = minecraft.getClass().getMethod("getWindow");
                window = getWindow.invoke(minecraft);
            } catch (ReflectiveOperationException missingMethod) {
                window = readField(minecraft, "window");
            }
            if (window == null) return;

            Method setTitle = findMethod(window.getClass(), "setTitle", String.class);
            if (setTitle == null) return;
            setTitle.invoke(window, "CodaLoader " + CodaTarget.LOADER_VERSION
                    + " | " + CodaTarget.MINECRAFT_DISPLAY_NAME);
            if (!titleSuccessReported) {
                titleSuccessReported = true;
                System.out.println("[CodaLoader] Window title hook active.");
            }
        } catch (Throwable ex) {
            if (!titleFailureReported) {
                titleFailureReported = true;
                System.err.println("[CodaLoader] Window-title hook warning: " + ex);
            }
        }
    }

    private static Object[] injectMenuWidgets(Object screen, int modCount) {
        try {
            ClassLoader loader = screen.getClass().getClassLoader();
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component", true, loader);
            Class<?> buttonClass = Class.forName("net.minecraft.client.gui.components.Button", true, loader);
            Class<?> onPressClass = Class.forName("net.minecraft.client.gui.components.Button$OnPress", true, loader);

            Method literal = componentClass.getMethod("literal", String.class);
            Method builderMethod = buttonClass.getMethod("builder", componentClass, onPressClass);
            Method add = findAddRenderableWidget(screen.getClass(), buttonClass);
            if (add == null) throw new NoSuchMethodException("Screen.addRenderableWidget");

            int screenWidth = readIntMember(screen, "width", 320);
            int screenHeight = readIntMember(screen, "height", 240);
            int centerX = screenWidth / 2;
            int iconY = screenHeight / 4 + 96;

            String modWord = modCount == 1 ? "mod" : "mods";
            Object cml = createButton(
                    buttonClass, onPressClass, builderMethod, literal,
                    "Coda | " + modCount + " " + modWord,
                    4, Math.max(4, screenHeight - 36), 138, 16,
                    "CMLStatus",
                    clicked -> System.out.println("[CodaLoader] CML status button clicked: "
                            + CodaTarget.LOADER_VERSION + " | " + modCount + " " + modWord));

            Object discord;
            Object youtube;
            try {
                discord = createSocialImageButton(
                        loader, componentClass, onPressClass, literal,
                        centerX - 46, iconY,
                        "codaloader:social/discord",
                        "CMLDiscord",
                        clicked -> System.out.println("[CodaLoader] Coda: The community invite is still on my clipboard. Check the launcher for now."));

                youtube = createSocialImageButton(
                        loader, componentClass, onPressClass, literal,
                        centerX + 26, iconY,
                        "codaloader:social/youtube",
                        "CMLYouTube",
                        clicked -> openExternal("https://www.youtube.com/@HowlingWhispersOfficial"));
            } catch (Throwable iconError) {
                System.err.println("[CodaLoader] Social image buttons unavailable; using text fallbacks: " + iconError);
                discord = createButton(
                        buttonClass, onPressClass, builderMethod, literal,
                        "D",
                        centerX - 46, iconY, 20, 20,
                        "CMLDiscordFallback",
                        clicked -> System.out.println("[CodaLoader] Discord button clicked. Community link is not configured yet."));
                youtube = createButton(
                        buttonClass, onPressClass, builderMethod, literal,
                        "YT",
                        centerX + 26, iconY, 20, 20,
                        "CMLYouTubeFallback",
                        clicked -> openExternal("https://www.youtube.com/@HowlingWhispersOfficial"));
            }

            add.invoke(screen, cml);
            add.invoke(screen, discord);
            add.invoke(screen, youtube);

            // Reflow through the same owner used by the native initialization hook.
            CodaMenuLifecycle.afterInitialize(screen);

            System.out.println("[CodaLoader] Added compact CML status + Discord/YouTube social controls.");
            return new Object[]{cml, discord, youtube};
        } catch (Throwable ex) {
            if (!menuFailureReported) {
                menuFailureReported = true;
                System.err.println("[CodaLoader] Could not add title-screen CML widgets:");
                ex.printStackTrace(System.err);
            }
            return new Object[0];
        }
    }

    private static Object createSocialImageButton(
            ClassLoader loader,
            Class<?> componentClass,
            Class<?> onPressClass,
            Method literal,
            int x,
            int y,
            String spriteId,
            String proxyName,
            ButtonAction action) throws Exception {

        Class<?> imageButtonClass = Class.forName(
                "net.minecraft.client.gui.components.ImageButton", true, loader);
        Class<?> widgetSpritesClass = Class.forName(
                "net.minecraft.client.gui.components.WidgetSprites", true, loader);

        Class<?> identifierClass;
        try {
            identifierClass = Class.forName("net.minecraft.resources.Identifier", true, loader);
        } catch (ClassNotFoundException newerNameMissing) {
            identifierClass = Class.forName("net.minecraft.resources.ResourceLocation", true, loader);
        }

        Object identifier = createResourceIdentifier(identifierClass, spriteId);
        Object sprites;
        try {
            var ctor = widgetSpritesClass.getConstructor(identifierClass);
            sprites = ctor.newInstance(identifier);
        } catch (NoSuchMethodException oneSpriteMissing) {
            var ctor = widgetSpritesClass.getConstructor(identifierClass, identifierClass);
            sprites = ctor.newInstance(identifier, identifier);
        }

        Object onPress = Proxy.newProxyInstance(
                onPressClass.getClassLoader(),
                new Class<?>[]{onPressClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "onPress" -> {
                            action.run(args != null && args.length > 0 ? args[0] : null);
                            return null;
                        }
                        case "toString" -> { return proxyName; }
                        case "hashCode" -> { return System.identityHashCode(proxy); }
                        case "equals" -> { return proxy == (args == null ? null : args[0]); }
                        default -> { return null; }
                    }
                });

        for (var ctor : imageButtonClass.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length < 6 || p.length > 7) continue;
            if (p[0] != int.class || p[1] != int.class || p[2] != int.class || p[3] != int.class) continue;
            if (!p[4].isAssignableFrom(widgetSpritesClass) && !widgetSpritesClass.isAssignableFrom(p[4])) continue;
            if (!p[5].isAssignableFrom(onPressClass) && !onPressClass.isAssignableFrom(p[5])) continue;

            if (p.length == 6) {
                return ctor.newInstance(x, y, 20, 20, sprites, onPress);
            }

            Object message = literal.invoke(null, proxyName);
            return ctor.newInstance(x, y, 20, 20, sprites, onPress, message);
        }

        throw new NoSuchMethodException("ImageButton(int,int,int,int,WidgetSprites,OnPress[,Component])");
    }

    private static Object createResourceIdentifier(Class<?> identifierClass, String id) throws Exception {
        Method parse = findMethod(identifierClass, "parse", String.class);
        if (parse != null && Modifier.isStatic(parse.getModifiers())) {
            return parse.invoke(null, id);
        }

        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? id.substring(colon + 1) : id;

        Method fromParts = findMethod(identifierClass, "fromNamespaceAndPath", String.class, String.class);
        if (fromParts != null && Modifier.isStatic(fromParts.getModifiers())) {
            return fromParts.invoke(null, namespace, path);
        }

        try {
            var ctor = identifierClass.getConstructor(String.class, String.class);
            return ctor.newInstance(namespace, path);
        } catch (NoSuchMethodException ignored) {
        }

        var ctor = identifierClass.getConstructor(String.class);
        return ctor.newInstance(id);
    }

    private static void openExternal(String url) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) {
                System.err.println("[CodaLoader] Desktop URL opening is not supported: " + url);
                return;
            }
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            System.out.println("[CodaLoader] Opened: " + url);
        } catch (Throwable ex) {
            System.err.println("[CodaLoader] Could not open URL " + url + ": " + ex);
        }
    }

    private static Object createButton(
            Class<?> buttonClass,
            Class<?> onPressClass,
            Method builderMethod,
            Method literal,
            String text,
            int x,
            int y,
            int width,
            int height,
            String proxyName,
            ButtonAction action) throws Exception {

        Object label = literal.invoke(null, text);
        Object onPress = Proxy.newProxyInstance(
                onPressClass.getClassLoader(),
                new Class<?>[]{onPressClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "onPress" -> {
                            Object clicked = args != null && args.length > 0 ? args[0] : null;
                            action.run(clicked);
                            return null;
                        }
                        case "toString" -> { return proxyName; }
                        case "hashCode" -> { return System.identityHashCode(proxy); }
                        case "equals" -> { return proxy == (args == null ? null : args[0]); }
                        default -> { return null; }
                    }
                });

        Object builder = builderMethod.invoke(null, label, onPress);
        Method bounds = findMethod(builder.getClass(), "bounds", int.class, int.class, int.class, int.class);
        if (bounds == null) throw new NoSuchMethodException("Button.Builder.bounds");
        builder = bounds.invoke(builder, x, y, width, height);

        Method build = findMethod(builder.getClass(), "build");
        if (build == null) throw new NoSuchMethodException("Button.Builder.build");
        return build.invoke(builder);
    }

    private static void setButtonText(
            Object button,
            Class<?> componentClass,
            Method literal,
            String text) throws Exception {
        if (button == null) return;
        Method setMessage = findMethod(button.getClass(), "setMessage", componentClass);
        if (setMessage != null) {
            setMessage.invoke(button, literal.invoke(null, text));
        }
    }

    private static int readIntMember(Object target, String name, int fallback) {
        try {
            Field field = findField(target.getClass(), name);
            if (field != null && field.getType() == int.class) {
                field.setAccessible(true);
                return field.getInt(target);
            }

            Method getter = findMethod(target.getClass(), name);
            if (getter != null && getter.getReturnType() == int.class) {
                return (int) getter.invoke(target);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static boolean allWidgetsPresent(Object screen, Object[] widgets) {
        for (Object widget : widgets) {
            if (widget == null || !screenContainsObject(screen, widget)) return false;
        }
        return true;
    }

    @FunctionalInterface
    private interface ButtonAction {
        void run(Object clicked) throws Throwable;
    }

    private static boolean screenContainsObject(Object screen, Object needle) {
        if (screen == null || needle == null) return false;

        for (Class<?> current = screen.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;

                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(screen);
                } catch (Throwable ignored) {
                    continue;
                }

                if (value == needle) return true;
                if (containerContainsIdentity(value, needle)) return true;
            }
        }

        return false;
    }

    private static boolean containerContainsIdentity(Object value, Object needle) {
        if (value == null) return false;

        if (value instanceof Optional<?> optional) {
            return optional.orElse(null) == needle;
        }

        if (value instanceof AtomicReference<?> reference) {
            return reference.get() == needle;
        }

        if (value instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) if (key == needle) return true;
            for (Object nested : map.values()) if (nested == needle) return true;
            return false;
        }

        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                if (nested == needle) return true;
            }
            return false;
        }

        Class<?> type = value.getClass();
        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (Array.get(value, i) == needle) return true;
            }
        }

        return false;
    }

    private static void schedule(Object minecraft, Runnable task) {
        try {
            Method execute = findMethod(minecraft.getClass(), "execute", Runnable.class);
            if (execute == null) {
                execute = findRunnableScheduler(minecraft.getClass());
            }

            if (execute != null) {
                execute.invoke(minecraft, task);
                if (!schedulerReported) {
                    schedulerReported = true;
                    System.out.println("[CodaLoader] Client render-thread scheduler located: "
                            + execute.getDeclaringClass().getName() + "." + execute.getName());
                }
                return;
            }
        } catch (Throwable ex) {
            if (!menuFailureReported) {
                menuFailureReported = true;
                System.err.println("[CodaLoader] Render-thread scheduling warning: " + ex);
            }
        }

        if (!schedulerReported) {
            schedulerReported = true;
            System.err.println("[CodaLoader] No Minecraft Runnable scheduler found; menu task skipped to protect the GUI thread.");
        }
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) throw new NoSuchFieldException(target.getClass().getName() + "." + name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object findActiveScreen(Object minecraft, Class<?> screenClass) {
        Object direct = findDirectScreen(minecraft, screenClass);
        if (direct != null) return direct;

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        seen.add(minecraft);
        queue.add(new SearchNode(minecraft, 0));

        int visited = 0;
        while (!queue.isEmpty() && visited < 4096) {
            SearchNode node = queue.removeFirst();
            Object owner = node.value();
            visited++;

            Object holderScreen = unwrapScreenCandidate(owner, screenClass);
            if (holderScreen != null) return holderScreen;

            enqueueContainerChildren(owner, node.depth(), queue, seen);

            if (node.depth() >= 5 || !shouldReflectInto(owner.getClass())) {
                continue;
            }

            Object methodScreen = findScreenFromAccessors(owner, screenClass);
            if (methodScreen != null) return methodScreen;

            for (Class<?> current = owner.getClass(); current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;

                    Object value;
                    try {
                        field.setAccessible(true);
                        value = field.get(owner);
                    } catch (Throwable ignored) {
                        continue;
                    }

                    Object screen = unwrapScreenCandidate(value, screenClass);
                    if (screen != null) return screen;

                    enqueueSearchValue(value, node.depth() + 1, queue, seen);
                }
            }
        }

        return null;
    }

    private static Object findDirectScreen(Object target, Class<?> screenClass) {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    Object screen = unwrapScreenCandidate(value, screenClass);
                    if (screen != null) return screen;
                } catch (Throwable ignored) {
                }
            }
        }

        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;
                if (!screenClass.isAssignableFrom(method.getReturnType())) continue;
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    if (screenClass.isInstance(value)) return value;
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static Object findScreenFromAccessors(Object target, Class<?> screenClass) {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;

                Class<?> returnType = method.getReturnType();
                boolean candidate = screenClass.isAssignableFrom(returnType)
                        || Optional.class.isAssignableFrom(returnType)
                        || AtomicReference.class.isAssignableFrom(returnType);
                if (!candidate) continue;

                try {
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    Object screen = unwrapScreenCandidate(value, screenClass);
                    if (screen != null) return screen;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object unwrapScreenCandidate(Object value, Class<?> screenClass) {
        if (value == null) return null;
        if (screenClass.isInstance(value)) return value;

        if (value instanceof Optional<?> optional) {
            Object nested = optional.orElse(null);
            return screenClass.isInstance(nested) ? nested : null;
        }

        if (value instanceof AtomicReference<?> reference) {
            Object nested = reference.get();
            return screenClass.isInstance(nested) ? nested : null;
        }

        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                if (screenClass.isInstance(nested)) return nested;
            }
        }

        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                if (screenClass.isInstance(nested)) return nested;
            }
        }

        Class<?> type = value.getClass();
        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object nested = Array.get(value, i);
                if (screenClass.isInstance(nested)) return nested;
            }
        }

        return null;
    }

    private static void enqueueContainerChildren(
            Object value,
            int depth,
            ArrayDeque<SearchNode> queue,
            Set<Object> seen) {
        if (depth >= 5 || value == null) return;

        if (value instanceof Optional<?> optional) {
            enqueueSearchValue(optional.orElse(null), depth + 1, queue, seen);
            return;
        }

        if (value instanceof AtomicReference<?> reference) {
            enqueueSearchValue(reference.get(), depth + 1, queue, seen);
            return;
        }

        if (value instanceof Map<?, ?> map) {
            int count = 0;
            for (Object nested : map.values()) {
                enqueueSearchValue(nested, depth + 1, queue, seen);
                if (++count >= 256) break;
            }
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object nested : iterable) {
                enqueueSearchValue(nested, depth + 1, queue, seen);
                if (++count >= 256) break;
            }
            return;
        }

        Class<?> type = value.getClass();
        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            int length = Math.min(Array.getLength(value), 256);
            for (int i = 0; i < length; i++) {
                enqueueSearchValue(Array.get(value, i), depth + 1, queue, seen);
            }
        }
    }

    private static void enqueueSearchValue(
            Object value,
            int depth,
            ArrayDeque<SearchNode> queue,
            Set<Object> seen) {
        if (value == null || depth > 5 || isLeafValue(value.getClass())) return;
        if (seen.add(value)) {
            queue.addLast(new SearchNode(value, depth));
        }
    }

    private static boolean shouldReflectInto(Class<?> type) {
        String name = type.getName();
        return name.startsWith("net.minecraft.client.")
                || name.startsWith("net.minecraft.realms.")
                || name.startsWith("com.mojang.blaze3d.")
                || name.startsWith("com.mojang.realmsclient.");
    }

    private static boolean isLeafValue(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Class.class == type;
    }

    private static Method findRunnableScheduler(Class<?> type) {
        String[] preferredNames = {"execute", "schedule", "tell", "submit"};

        for (String preferred : preferredNames) {
            Method method = findSingleRunnableMethod(type, preferred);
            if (method != null) return method;
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (!parameter.isAssignableFrom(Runnable.class) && !Runnable.class.isAssignableFrom(parameter)) {
                    continue;
                }
                if (method.getReturnType() != void.class) continue;
                try {
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Method findSingleRunnableMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!name.equals(method.getName())
                        || Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 1) {
                    continue;
                }

                Class<?> parameter = method.getParameterTypes()[0];
                if (!parameter.isAssignableFrom(Runnable.class) && !Runnable.class.isAssignableFrom(parameter)) {
                    continue;
                }

                try {
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private record SearchNode(Object value, int depth) {}

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Method findAddRenderableWidget(Class<?> screenClass, Class<?> buttonClass) {
        for (Class<?> current = screenClass; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"addRenderableWidget".equals(method.getName()) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(buttonClass)) continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}

