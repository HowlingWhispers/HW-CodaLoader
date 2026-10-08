package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * CML-owned widget layouts for the native title and pause screens.
 *
 * All actions remain Minecraft's original widgets/callbacks. CML owns their
 * visibility, labels, hitboxes and ordering, not account/world operations.
 * Unknown third-party widgets are never removed. If an essential native action
 * is missing, refuse ownership so the legacy menu adapter can handle it.
 *
 * Run only on the GUI thread, after native widget initialization/rebuild.
 */
final class CodaOwnedMenus {
    private static final Set<String> TITLE_BLOCKED = Set.of("menu.multiplayer", "menu.online", "menu.realms");
    private static final Set<String> PAUSE_BLOCKED = Set.of("menu.shareToLan", "menu.sendFeedback", "menu.reportBugs");
    private static final int GAP = 4;
    private static final int ROW = 24;
    private static final int ICON_GAP = 8;

    private CodaOwnedMenus() {}

    /** True when the full native action set was recognized and laid out. */
    static boolean apply(Object screen, boolean title) throws Exception {
        List<Object> original = CodaMenus.children(screen);
        Object principal = CodaMenus.find(original, title ? "menu.singleplayer" : "menu.returnToGame");
        Object settings = CodaMenus.find(original, "menu.options");
        Object exit = CodaMenus.find(original, title ? "menu.quit" : "menu.returnToMenu");
        if (principal == null || settings == null || exit == null) return false;

        // Require functional, moveable buttons before removing anything. A
        // snapshot incompatibility falls back to the legacy implementation.
        for (Object action : List.of(principal, settings, exit)) {
            if (CodaMenus.number(action, "getWidth", -1) <= 0
                    || CodaMenus.number(action, "getHeight", -1) <= 0
                    || CodaMenus.number(action, "getX", -1) < 0
                    || CodaMenus.number(action, "getY", -1) < 0) return false;
        }

        Set<String> blocked = title ? TITLE_BLOCKED : PAUSE_BLOCKED;
        List<Object> active = new ArrayList<>();
        for (Object widget : original) {
            if (blocked.contains(CodaMenus.key(widget))) {
                CodaMenus.remove(screen, widget);
                continue;
            }
            active.add(widget);
        }

        // Relabel only native, known actions. Cached translation keys let the
        // menu survive repeated initialization and resize without drift.
        for (Object widget : active) {
            String key = CodaMenus.key(widget);
            String caption = label(key);
            if (caption != null) CodaMenus.setLabel(widget, caption);
        }

        int center = CodaMenus.number(principal, "getX", 0)
                + CodaMenus.number(principal, "getWidth", 200) / 2;
        int y = CodaMenus.number(principal, "getY", 0);
        if (title) {
            place(principal, center - 100, y, 200);
            place(settings, center - 100, y + ROW, 98);
            place(exit, center + 2, y + ROW, 98);
            placeIcons(active, center, y + 2 * ROW, List.of(principal, settings, exit));
        } else {
            place(principal, center - 100, y, 200);
            Object advancements = CodaMenus.findEither(active, "gui.advancements", "menu.advancements");
            Object statistics = CodaMenus.findEither(active, "gui.stats", "menu.stats");
            int line = y + ROW;
            if (advancements != null && statistics != null) {
                place(advancements, center - 100, line, 98);
                place(statistics, center + 2, line, 98);
                line += ROW;
            } else if (advancements != null || statistics != null) {
                place(advancements != null ? advancements : statistics, center - 100, line, 200);
                line += ROW;
            }

            Object rules = CodaMenus.find(active, "menu.worldOptions");
            if (rules == null) rules = findWorldOptions(active, settings, exit);
            if (rules != null) {
                place(settings, center - 100, line, 98);
                place(rules, center + 2, line, 98);
            } else {
                place(settings, center - 100, line, 200);
            }
            line += ROW;
            List<Object> reserved = new ArrayList<>(List.of(principal, settings, exit));
            if (advancements != null) reserved.add(advancements);
            if (statistics != null) reserved.add(statistics);
            if (rules != null) reserved.add(rules);
            if (placeIcons(active, center, line, reserved)) line += ROW;
            place(exit, center - 100, line, 200);
        }
        return true;
    }

    private static String label(String key) {
        return switch (key) {
            case "menu.singleplayer" -> "My Worlds";
            case "menu.options" -> "Coda's Settings";
            case "menu.quit" -> "Clock Out";
            case "menu.returnToGame" -> "Back to Adventure";
            case "gui.advancements", "menu.advancements" -> "Pawprints";
            case "gui.stats", "menu.stats" -> "Coda's Ledger";
            case "menu.worldOptions" -> "World Rules";
            case "menu.returnToMenu" -> "Save & Curl Up";
            default -> null;
        };
    }

    /** Returns true if a compact-icon row was placed. */
    private static boolean placeIcons(List<Object> active, int center, int y, List<Object> reserved) throws Exception {
        List<Object> icons = new ArrayList<>();
        for (Object widget : active) {
            if (reserved.contains(widget)) continue;
            int width = CodaMenus.number(widget, "getWidth", -1);
            int height = CodaMenus.number(widget, "getHeight", -1);
            if (width > 0 && width <= 40 && height > 0 && height <= 20)
                icons.add(widget);
        }
        // Use the pre-layout native positions as ordering hints. Subsequent
        // calls re-use the same order because layout is deterministic.
        icons.sort(Comparator.comparingInt(widget -> {
            try { return CodaMenus.number(widget, "getX", 0); }
            catch (Exception e) { return 0; }
        }));
        int width = (icons.size() - 1) * ICON_GAP;
        for (Object icon : icons) width += CodaMenus.number(icon, "getWidth", 20);
        int x = center - Math.max(0, width) / 2;
        for (Object icon : icons) {
            place(icon, x, y, CodaMenus.number(icon, "getWidth", 20));
            x += CodaMenus.number(icon, "getWidth", 20) + ICON_GAP;
        }
        return !icons.isEmpty();
    }

    /**
     * Snapshot 3 sometimes changes the translation key of the adjacent World
     * Options action. Accept only a native full-size button in Settings' row.
     */
    private static Object findWorldOptions(List<Object> widgets, Object settings, Object exit) throws Exception {
        int y = CodaMenus.number(settings, "getY", -1);
        for (Object widget : widgets) {
            if (widget == settings || widget == exit) continue;
            if (CodaMenus.number(widget, "getY", -2) == y
                    && CodaMenus.number(widget, "getWidth", 0) >= 80
                    && CodaMenus.number(widget, "getX", -1) > CodaMenus.number(settings, "getX", -1))
                return widget;
        }
        return null;
    }

    private static void place(Object widget, int x, int y, int width) throws Exception {
        CodaMenus.coordinate(widget, "setX", x);
        CodaMenus.coordinate(widget, "setY", y);
        CodaMenus.coordinate(widget, "setWidth", width);
    }
}
