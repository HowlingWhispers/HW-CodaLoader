package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Reuses native handlers while tailoring title/pause controls to CML's personal-world model. */
final class CodaMenus {
    private static final Map<Object, String> ORIGINAL_KEYS = new WeakHashMap<>();
    private static final Set<String> TITLE_REMOVED = Set.of("menu.multiplayer", "menu.online", "menu.realms");
    private static final Set<String> PAUSE_REMOVED = Set.of("menu.shareToLan", "menu.sendFeedback", "menu.reportBugs");
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("menu.singleplayer", "My Worlds"),
            Map.entry("menu.worldOptions", "World Rules"),
            Map.entry("menu.options", "Coda's Settings"),
            Map.entry("menu.quit", "Clock Out"),
            Map.entry("menu.returnToGame", "Back to Adventure"),
            Map.entry("gui.advancements", "Pawprints"),
            Map.entry("menu.advancements", "Pawprints"),
            Map.entry("gui.stats", "Coda's Ledger"),
            Map.entry("menu.stats", "Coda's Ledger"),
            Map.entry("menu.returnToMenu", "Save & Curl Up"));
    private CodaMenus() {}

    /** Must be invoked on Minecraft's GUI thread, after this screen's widgets are initialized. */
    static void apply(Object screen, boolean title) throws Exception {
        List<Object> widgets = children(screen);
        Set<String> removed = title ? TITLE_REMOVED : PAUSE_REMOVED;
        List<Object> kept = new ArrayList<>();
        for (Object widget : widgets) {
            String key = key(widget);
            if (removed.contains(key)) {
                remove(screen, widget);
                continue;
            }
            String label = LABELS.get(key);
            if (label != null) {
                setLabel(widget, label);
                tooltip(widget, tooltipFor(key));
            }
            kept.add(widget);
        }
        if (title) layoutTitle(kept);
        else layoutPause(screen, kept);
    }

    static String tooltipFor(String key) {
        return switch (key) {
            case "menu.singleplayer" -> "Coda: Your worlds, filed and ready for adventure.";
            case "menu.worldOptions" -> "Coda: The rules of your world. Make yourself at home.";
            case "menu.options" -> "Coda: Sound, sights and controls. Your paws, your preferences.";
            case "menu.quit" -> "Coda: Clipboard closed. See you next adventure.";
            case "menu.returnToGame" -> "Coda: Enough paperwork. Back to your world.";
            case "menu.returnToMenu" -> "Coda: Save your world and head back to the den.";
            case "gui.stats", "menu.stats" -> "Coda: Your adventures, counted and filed.";
            default -> "Coda: A record of your finest pawprints.";
        };
    }

    static String key(Object widget) throws Exception {
        String cached = ORIGINAL_KEYS.get(widget);
        if (cached != null) return cached;
        Method messageMethod = method(widget.getClass(), "getMessage");
        if (messageMethod == null) return "";
        Object message = messageMethod.invoke(widget);
        if (message == null) return "";
        Method contentsMethod = method(message.getClass(), "getContents");
        if (contentsMethod == null) return "";
        Object contents = contentsMethod.invoke(message);
        if (contents == null) return "";
        Method getKey = method(contents.getClass(), "getKey");
        if (getKey == null) return "";
        Object value = getKey.invoke(contents);
        if (!(value instanceof String text)) return "";
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("world") && normalized.contains("option")) text = "menu.worldOptions";
        ORIGINAL_KEYS.put(widget, text);
        return text;
    }

    static List<Object> children(Object screen) throws Exception {
        Method children = method(screen.getClass(), "children");
        if (children == null) throw new NoSuchMethodException("Screen.children()");
        Object value = children.invoke(screen);
        List<Object> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) for (Object widget : iterable) result.add(widget);
        else throw new IllegalStateException("Screen.children() is not iterable");
        return result;
    }

    static void remove(Object screen, Object widget) throws Exception {
        Method remove = compatibleMethod(screen.getClass(), "removeWidget", widget);
        if (remove != null) remove.invoke(screen, widget);
        else {
            // If Snapshot 3 has changed removal, hide and disable without mutating unknown containers.
            boolean hidden = booleanField(widget, "visible", false);
            boolean disabled = booleanField(widget, "active", false);
            if (!hidden || !disabled) throw new NoSuchMethodException("Cannot safely hide unsupported menu widget");
        }
    }

    static void setLabel(Object widget, String label) throws Exception {
        Method getMessage = method(widget.getClass(), "getMessage");
        Class<?> component = getMessage.getReturnType();
        Method literal = method(component, "literal", String.class);
        if (literal == null) throw new NoSuchMethodException("Component.literal(String)");
        Object message = getMessage.invoke(widget);
        Method getString = method(message.getClass(), "getString");
        if (getString != null && label.equals(getString.invoke(message))) return;
        Method set = compatibleMethod(widget.getClass(), "setMessage", message);
        if (set == null) throw new NoSuchMethodException("Widget.setMessage(Component)");
        set.invoke(widget, literal.invoke(null, label));
    }

    static void tooltip(Object widget, String text) {
        try {
            Class<?> component = method(widget.getClass(), "getMessage").getReturnType();
            Class<?> tooltip = Class.forName("net.minecraft.client.gui.components.Tooltip", true, widget.getClass().getClassLoader());
            Object label = method(component, "literal", String.class).invoke(null, text);
            Object hint = method(tooltip, "create", component).invoke(null, label);
            Method set = compatibleMethod(widget.getClass(), "setTooltip", hint);
            if (set != null) set.invoke(widget, hint);
        } catch (ReflectiveOperationException | NullPointerException unavailable) {
            // Captions and native narration still work if the optional tooltip API changed.
        }
    }

    private static void layoutTitle(List<Object> widgets) throws Exception {
        Object worlds = find(widgets, "menu.singleplayer");
        if (worlds == null) return;
        int worldY = number(worlds, "getY", -1000);
        if (worldY < 0) return;
        int row = worldY + 24;
        List<Object> icons = new ArrayList<>();
        for (Object widget : widgets) {
            String key = key(widget);
            int y = number(widget, "getY", -1000);
            if (key.equals("menu.options") || key.equals("menu.quit")) {
                coordinate(widget, "setY", row);
            } else if (number(widget, "getHeight", 0) == 20 && number(widget, "getWidth", 1000) <= 40
                    && y >= row && y <= row + 96) {
                icons.add(widget);
            }
        }
        // Keep social/language/accessibility controls below the full-size buttons.
        // Sorting preserves their left-to-right order across repeated initialization and widget rebuilds.
        icons.sort(java.util.Comparator.comparingInt(widget -> {
            try { return number(widget, "getX", 0); }
            catch (Exception unavailable) { return 0; }
        }));
        int totalWidth = Math.max(0, icons.size() - 1) * 8;
        for (Object icon : icons) totalWidth += number(icon, "getWidth", 20);
        int x = number(worlds, "getX", 0) + number(worlds, "getWidth", 200) / 2 - totalWidth / 2;
        for (Object icon : icons) {
            coordinate(icon, "setX", x);
            coordinate(icon, "setY", worldY + 48);
            x += number(icon, "getWidth", 20) + 8;
        }
    }

    private static void layoutPause(Object screen, List<Object> widgets) throws Exception {
        Object resume = find(widgets, "menu.returnToGame");
        if (resume == null) return;
        int y = number(resume, "getY", -1000);
        int x = number(resume, "getX", -1000);
        if (y < 0 || x < 0) return;
        Object advancements = findEither(widgets, "gui.advancements", "menu.advancements");
        Object stats = findEither(widgets, "gui.stats", "menu.stats");
        if (advancements != null && stats != null) {
            coordinate(advancements, "setY", y + 24);
            coordinate(stats, "setY", y + 24);
        }
        Object options = find(widgets, "menu.options");
        Object quit = find(widgets, "menu.returnToMenu");
        Object worldOptions = find(widgets, "menu.worldOptions");
        // Preserve Snapshot 3's adjacent native world control even if its key changes.
        if (worldOptions == null && options != null) {
            int optionY = number(options, "getY", -1);
            for (Object widget : widgets) {
                if (widget != options && widget != quit && number(widget, "getY", -2) == optionY
                        && number(widget, "getX", -1) > x && number(widget, "getWidth", 0) >= 80) {
                    worldOptions = widget;
                    break;
                }
            }
        }
        List<Object> icons = new ArrayList<>();
        for (Object widget : widgets) {
            int iconY = number(widget, "getY", -1000);
            if (number(widget, "getHeight", 0) == 20 && number(widget, "getWidth", 1000) <= 40
                    && iconY >= y + 24 && iconY <= y + 200) icons.add(widget);
        }
        icons.sort(java.util.Comparator.comparingInt(widget -> {
            try { return number(widget, "getX", 0); }
            catch (Exception unavailable) { return 0; }
        }));
        int width = number(resume, "getWidth", 200);
        int totalWidth = Math.max(0, icons.size() - 1) * 8;
        for (Object icon : icons) totalWidth += number(icon, "getWidth", 20);
        int iconX = x + width / 2 - totalWidth / 2;
        for (Object icon : icons) {
            coordinate(icon, "setX", iconX);
            coordinate(icon, "setY", y + 48);
            iconX += number(icon, "getWidth", 20) + 8;
        }
        int settingsY = y + (icons.isEmpty() ? 48 : 72);
        if (options != null) {
            coordinate(options, "setX", x);
            coordinate(options, "setWidth", worldOptions == null ? width : (width - 4) / 2);
            coordinate(options, "setY", settingsY);
        }
        if (worldOptions != null) {
            coordinate(worldOptions, "setX", x + (width - 4) / 2 + 4);
            coordinate(worldOptions, "setWidth", (width - 4) / 2);
            coordinate(worldOptions, "setY", settingsY);
        }
        if (quit != null) {
            coordinate(quit, "setX", x);
            coordinate(quit, "setWidth", width);
            coordinate(quit, "setY", settingsY + 24);
        }
    }

    static Object find(List<Object> widgets, String key) throws Exception {
        for (Object widget : widgets) if (key.equals(key(widget))) return widget;
        return null;
    }
    static Object findEither(List<Object> widgets, String a, String b) throws Exception {
        Object result = find(widgets, a); return result == null ? find(widgets, b) : result;
    }
    static int number(Object target, String name, int fallback) throws Exception {
        Method method = method(target.getClass(), name);
        return method == null ? fallback : ((Number) method.invoke(target)).intValue();
    }
    static void coordinate(Object widget, String name, int value) throws Exception {
        Method method = method(widget.getClass(), name, int.class);
        if (method != null) method.invoke(widget, value);
    }
    private static boolean booleanField(Object target, String name, boolean value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.setBoolean(target, value); return true; }
            catch (NoSuchFieldException ignored) {}
        }
        return false;
    }
    private static Method compatibleMethod(Class<?> type, String name, Object argument) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            for (Method method : current.getDeclaredMethods())
                if (method.getName().equals(name) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(argument)) {
                    method.setAccessible(true); return method;
                }
        return null;
    }
    private static Method method(Class<?> type, String name, Class<?>... params) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, params); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
}

