package dev.howlingwhispers.codaloader.bootstrap;

import java.util.ArrayList;
import java.util.List;

/** Contract tests for title/pause ownership without launching Minecraft. */
public final class CodaOwnedMenusTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static boolean overlap(CodaMenusTest.Widget a, CodaMenusTest.Widget b) {
        return a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }
    private static void noOverlaps(List<CodaMenusTest.Widget> widgets) {
        for (int i = 0; i < widgets.size(); i++)
            for (int j = i + 1; j < widgets.size(); j++)
                check(!overlap(widgets.get(i), widgets.get(j)), "owned controls must not overlap");
    }
    public static void main(String[] args) throws Exception {
        var title = new CodaMenusTest.Screen();
        var worlds = title.add("menu.singleplayer", 60, 100, 200);
        var multiplayer = title.add("menu.multiplayer", 60, 124, 200);
        var realms = title.add("menu.online", 60, 148, 200);
        var settings = title.add("menu.options", 60, 172, 98);
        var quit = title.add("menu.quit", 162, 172, 98);
        var lang = title.add("narrator.button.language", 36, 172, 20);
        var access = title.add("narrator.button.accessibility", 264, 172, 20);
        var discord = title.add("codaloader.discord", 102, 148, 20);
        var youtube = title.add("codaloader.youtube", 198, 148, 20);
        var thirdParty = title.add("thirdparty.action", 5, 10, 80);
        check(CodaOwnedMenus.apply(title, true), "title ownership accepted");
        check(!title.children().contains(multiplayer) && !title.renderables.contains(multiplayer),
                "multiplayer removed from both input and renderer");
        check(!title.children().contains(realms), "realms removed");
        check(title.children().contains(thirdParty) && thirdParty.getX() == 5, "unknown third-party controls kept");
        check(worlds.getMessage().getString().equals("My Worlds"), "Coda worlds caption");
        check(settings.getMessage().getString().equals("Coda's Settings"), "Coda settings caption");
        check(quit.getMessage().getString().equals("Clock Out"), "Coda exit caption");
        check(worlds.getY() == 100 && settings.getY() == 124 && quit.getY() == 124, "title action rows");
        var ownedTitle = List.of(worlds, settings, quit, lang, access, discord, youtube);
        noOverlaps(ownedTitle);
        for (var widget : ownedTitle) { widget.press(); check(widget.pressCount() == 1, "native click retained"); }
        check(CodaOwnedMenus.apply(title, true), "title reinitialization accepted");
        noOverlaps(ownedTitle);
        check(title.removed == 2, "title does not repeatedly remove widgets");
        check(lang.getY() == 148 && youtube.getY() == 148, "small icons form one row");

        var pause = new CodaMenusTest.Screen();
        var resume = pause.add("menu.returnToGame", 60, 60, 200);
        var advances = pause.add("gui.advancements", 60, 84, 98);
        var stats = pause.add("gui.stats", 162, 84, 98);
        List<CodaMenusTest.Widget> icons = new ArrayList<>();
        for (int i = 0; i < 4; i++) icons.add(pause.add("pause.icon." + i, 90 + 24 * i, 108, 20));
        var options = pause.add("menu.options", 60, 132, 98);
        var rules = pause.add("menu.worldOptions", 162, 132, 98);
        var oldLan = pause.add("menu.shareToLan", 162, 156, 98);
        var oldFeedback = pause.add("menu.sendFeedback", 60, 156, 98);
        var exit = pause.add("menu.returnToMenu", 60, 204, 200);
        check(CodaOwnedMenus.apply(pause, false), "pause ownership accepted");
        check(!pause.children().contains(oldLan) && !pause.children().contains(oldFeedback), "unused native actions gone");
        check(resume.getMessage().getString().equals("Back to Adventure"), "native resume caption");
        check(exit.getMessage().getString().equals("Save & Curl Up"), "native save caption");
        check(advances.getMessage().getString().equals("Pawprints"), "advancements caption");
        check(stats.getMessage().getString().equals("Coda's Ledger"), "statistics caption");
        check(options.getY() == 132 && rules.getY() == 132 && exit.getY() == 156,
                "pause rows are not crowded");
        List<CodaMenusTest.Widget> pauseWidgets = new ArrayList<>(List.of(resume, advances, stats, options, rules, exit));
        pauseWidgets.addAll(icons);
        noOverlaps(pauseWidgets);
        check(CodaOwnedMenus.apply(pause, false), "pause survives repeated layout");
        noOverlaps(pauseWidgets);
        for (var w : pauseWidgets) { w.press(); check(w.pressCount() == 1, "pause callbacks preserved"); }

        var incompatible = new CodaMenusTest.Screen();
        var surviving = incompatible.add("menu.singleplayer", 60, 100, 200);
        incompatible.add("menu.multiplayer", 60, 124, 200);
        check(!CodaOwnedMenus.apply(incompatible, true), "missing essential action declines ownership");
        check(incompatible.children().size() == 2 && incompatible.children().contains(surviving),
                "failed preflight leaves vanilla widgets untouched");
        System.out.println("Owned Coda menu tests passed: " + checks + " checks.");
    }
}
