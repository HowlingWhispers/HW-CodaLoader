package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Tiny reflection-only client hook proof.
 *
 * This intentionally avoids Fabric/Forge/Mixin. It waits for the readable Mojang client
 * classes, updates the window title, and adds one CodaLoader button to TitleScreen.
 */
final class ClientHooks {
    private static volatile boolean titleFailureReported;
    private static volatile boolean titleSuccessReported;
    private static volatile boolean menuFailureReported;

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

            Object lastTitleScreen = null;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    applyWindowTitle(minecraft);

                    Object screen = readByTypeName(
                            minecraft,
                            "net.minecraft.client.gui.screens.Screen");
                    if (screen != null && "net.minecraft.client.gui.screens.TitleScreen".equals(screen.getClass().getName())) {
                        if (screen != lastTitleScreen) {
                            Object target = screen;
                            schedule(minecraft, () -> injectMenuButton(target, modCount));
                            lastTitleScreen = screen;
                        }
                    }
                } catch (Throwable ex) {
                    if (!menuFailureReported) {
                        menuFailureReported = true;
                        System.err.println("[CodaLoader] Menu hook warning: " + ex);
                    }
                }

                Thread.sleep(1000);
            }
        } catch (Throwable ex) {
            System.err.println("[CodaLoader] Client hook failed:");
            ex.printStackTrace(System.err);
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

    private static void injectMenuButton(Object screen, int modCount) {
        try {
            ClassLoader loader = screen.getClass().getClassLoader();
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component", true, loader);
            Class<?> buttonClass = Class.forName("net.minecraft.client.gui.components.Button", true, loader);
            Class<?> onPressClass = Class.forName("net.minecraft.client.gui.components.Button$OnPress", true, loader);

            Method literal = componentClass.getMethod("literal", String.class);
            String modWord = modCount == 1 ? "mod" : "mods";
            Object label = literal.invoke(null,
                    "CodaLoader " + CodaTarget.LOADER_VERSION + " | " + modCount + " " + modWord);

            Object onPress = Proxy.newProxyInstance(
                    onPressClass.getClassLoader(),
                    new Class<?>[]{onPressClass},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "onPress" -> {
                                Object clicked = args != null && args.length > 0 ? args[0] : null;
                                Object alive = literal.invoke(null, "CodaLoader is alive! | " + modCount + " " + modWord);
                                if (clicked != null) {
                                    Method setMessage = findMethod(clicked.getClass(), "setMessage", componentClass);
                                    if (setMessage != null) setMessage.invoke(clicked, alive);
                                }
                                System.out.println("[CodaLoader] Main-menu button clicked. Hooks are alive.");
                                return null;
                            }
                            case "toString" -> { return "CodaLoaderOnPress"; }
                            case "hashCode" -> { return System.identityHashCode(proxy); }
                            case "equals" -> { return proxy == (args == null ? null : args[0]); }
                            default -> { return null; }
                        }
                    });

            Method builderMethod = buttonClass.getMethod("builder", componentClass, onPressClass);
            Object builder = builderMethod.invoke(null, label, onPress);

            int x = 6;
            int y = 6;

            Method bounds = findMethod(builder.getClass(), "bounds", int.class, int.class, int.class, int.class);
            if (bounds == null) throw new NoSuchMethodException("Button.Builder.bounds");
            builder = bounds.invoke(builder, x, y, 200, 20);

            Method build = findMethod(builder.getClass(), "build");
            if (build == null) throw new NoSuchMethodException("Button.Builder.build");
            Object button = build.invoke(builder);

            Method add = findAddRenderableWidget(screen.getClass(), buttonClass);
            if (add == null) throw new NoSuchMethodException("Screen.addRenderableWidget");
            add.invoke(screen, button);

            System.out.println("[CodaLoader] Added CodaLoader button to Minecraft title screen.");
        } catch (Throwable ex) {
            if (!menuFailureReported) {
                menuFailureReported = true;
                System.err.println("[CodaLoader] Could not add title-screen button:");
                ex.printStackTrace(System.err);
            }
        }
    }

    private static void schedule(Object minecraft, Runnable task) {
        try {
            Method execute = findMethod(minecraft.getClass(), "execute", Runnable.class);
            if (execute != null) {
                execute.invoke(minecraft, task);
                return;
            }
        } catch (Throwable ignored) {
        }
        task.run();
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) throw new NoSuchFieldException(target.getClass().getName() + "." + name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object readByTypeName(Object target, String typeName) throws Exception {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.getType().getName().equals(typeName)) continue;
                field.setAccessible(true);
                return field.get(target);
            }
        }

        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 0) continue;
                if (!method.getReturnType().getName().equals(typeName)) continue;
                method.setAccessible(true);
                return method.invoke(target);
            }
        }

        throw new NoSuchFieldException(
                target.getClass().getName() + " has no field/getter of type " + typeName);
    }

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
