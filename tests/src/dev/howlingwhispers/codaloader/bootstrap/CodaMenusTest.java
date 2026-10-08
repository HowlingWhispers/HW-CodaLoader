package dev.howlingwhispers.codaloader.bootstrap;

import java.util.ArrayList;
import java.util.List;

public final class CodaMenusTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Screen title = new Screen();
        Widget worlds = title.add("menu.singleplayer", 60, 100, 200);
        Widget multiplayer = title.add("menu.multiplayer", 60, 124, 200);
        Widget realms = title.add("menu.online", 60, 148, 200);
        Widget settings = title.add("menu.options", 60, 172, 98);
        Widget quit = title.add("menu.quit", 162, 172, 98);
        Widget language = title.add("narrator.button.language", 36, 172, 20);
        Widget accessibility = title.add("narrator.button.accessibility", 264, 172, 20);
        Widget discord = title.add("codaloader.discord", 102, 148, 20);
        Widget youtube = title.add("codaloader.youtube", 198, 148, 20);
        Widget custom = title.add("another_mod.action", 4, 8, 80);
        CodaMenus.apply(title, true);
        check(!title.children.contains(multiplayer) && !title.renderables.contains(multiplayer), "multiplayer removed from input and rendering");
        check(!title.children.contains(realms) && !title.renderables.contains(realms), "realms removed from input and rendering");
        check(worlds.getMessage().getString().equals("My Worlds"), "world selector caption");
        worlds.press();
        check(worlds.presses == 1, "world selector keeps original handler");
        settings.press(); quit.press();
        check(settings.presses == 1 && quit.presses == 1, "settings and quit handlers preserved");
        check(settings.y == 124 && quit.y == 124, "title gap collapsed");
        List<Widget> icons = List.of(language, accessibility, discord, youtube);
        for (Widget icon : icons) {
            check(!overlap(icon, settings) && !overlap(icon, quit) && !overlap(icon, worlds), "small control does not overlap main actions");
            icon.press();
            check(icon.presses == 1, "small control retains original handler");
        }
        for (int i = 0; i < icons.size(); i++)
            for (int j = i + 1; j < icons.size(); j++)
                check(!overlap(icons.get(i), icons.get(j)), "small controls do not overlap each other");
        int left = icons.stream().mapToInt(w -> w.x).min().orElseThrow();
        int right = icons.stream().mapToInt(w -> w.x + w.width).max().orElseThrow();
        check((left + right) / 2 == worlds.x + worlds.width / 2, "icon row centred beneath worlds");
        check(title.children.contains(custom) && custom.message.getString().equals("another_mod.action"), "unknown mod controls untouched");
        CodaMenus.apply(title, true);
        check(title.children.size() == 8 && settings.y == 124 && language.y == 148, "repeated polls do not duplicate or drift");
        check(title.removed == 2, "repeated poll does not remove additional widgets");

        // Minecraft rebuilds widgets on resize/resource reload.
        title = new Screen();
        worlds = title.add("menu.singleplayer", 90, 80, 200);
        title.add("menu.online", 90, 128, 200);
        title.add("menu.multiplayer", 90, 104, 200);
        settings = title.add("menu.options", 90, 152, 98);
        CodaMenus.apply(title, true);
        check(title.children.size() == 2 && settings.y == 104, "reinitialized menu customized again");

        Screen pause = new Screen();
        Widget resume = pause.add("menu.returnToGame", 60, 60, 200);
        Widget achievements = pause.add("gui.advancements", 60, 84, 98);
        Widget stats = pause.add("gui.stats", 162, 84, 98);
        pause.add("menu.sendFeedback", 60, 108, 98);
        pause.add("menu.reportBugs", 162, 108, 98);
        Widget options = pause.add("menu.options", 60, 132, 98);
        pause.add("menu.shareToLan", 162, 132, 98);
        Widget save = pause.add("menu.returnToMenu", 60, 156, 200);
        CodaMenus.apply(pause, false);
        check(pause.children.size() == 5, "pause removes LAN and vanilla feedback links");
        check(resume.message.getString().equals("Back to Adventure"), "resume caption");
        check(save.message.getString().equals("Save & Curl Up"), "save and quit caption");
        save.press();
        check(save.presses == 1, "save and quit handler preserved");
        check(achievements.message.getString().equals("Pawprints") && stats.message.getString().equals("Coda's Ledger"), "progress captions");
        check(options.x == resume.x && options.width == resume.width && options.y == 108 && save.y == 132, "pause menu compacted with full-width settings");
        CodaMenus.apply(pause, false);
        check(options.width == 200 && save.y == 132, "pause layout is idempotent");

        // Snapshot 3 creates three native icons before CML adds its two socials.
        Screen five = new Screen();
        Widget fiveWorlds = five.add("menu.singleplayer", 60, 100, 200);
        Widget fiveSettings = five.add("menu.options", 60, 172, 98);
        Widget fiveQuit = five.add("menu.quit", 162, 172, 98);
        Widget account = five.add("narrator.button.account", 120, 172, 20);
        Widget fiveLanguage = five.add("narrator.button.language", 144, 172, 20);
        Widget fiveAccess = five.add("narrator.button.accessibility", 168, 172, 20);
        CodaMenus.apply(five, true);
        Widget fiveDiscord = five.add("codaloader.discord", 114, 148, 20);
        Widget fiveYoutube = five.add("codaloader.youtube", 186, 148, 20);
        CodaMenus.apply(five, true);
        List<Widget> fiveIcons = List.of(fiveDiscord, account, fiveLanguage, fiveAccess, fiveYoutube);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < fiveIcons.size(); i++) {
                Widget icon = fiveIcons.get(i);
                check(icon.y == 148 && !overlap(icon, fiveWorlds) && !overlap(icon, fiveSettings)
                        && !overlap(icon, fiveQuit), "five-icon row stays below main controls");
                if (i > 0) check(icon.x - (fiveIcons.get(i - 1).x + fiveIcons.get(i - 1).width) >= 8,
                        "all five controls have at least eight pixels between hitboxes");
                int presses = icon.presses;
                icon.press();
                check(icon.presses == presses + 1, "five-icon row keeps original handlers");
            }
            check((fiveIcons.get(0).x + fiveIcons.get(4).x + fiveIcons.get(4).width) / 2
                    == fiveWorlds.x + fiveWorlds.width / 2, "complete five-icon row is centered");
            CodaMenus.apply(five, true);
        }

        HiddenScreen fallback = new HiddenScreen();
        Widget lan = new Widget("menu.shareToLan", 0, 0, 98);
        fallback.children.add(lan);
        CodaMenus.apply(fallback, false);
        check(!lan.visible && !lan.active, "unsupported remove API hides and disables LAN");
        check(fallback.children.size() == 1, "fallback does not mutate unknown collections");
        System.out.println("Coda menu tests passed: " + checks + " checks; live Snapshot 3 GUI remains unverified.");
    }

    private static boolean overlap(Widget a, Widget b) {
        return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + 20 && a.y + 20 > b.y;
    }

    public static class Screen {
        final List<Widget> children = new ArrayList<>(), renderables = new ArrayList<>();
        int removed;
        public Widget add(String key, int x, int y, int width) { Widget widget = new Widget(key, x, y, width); children.add(widget); renderables.add(widget); return widget; }
        public List<Widget> children() { return children; }
        protected void removeWidget(Widget widget) { children.remove(widget); renderables.remove(widget); removed++; }
    }
    public static final class HiddenScreen {
        final List<Widget> children = new ArrayList<>();
        public List<Widget> children() { return children; }
    }
    public static final class Widget {
        Message message; int x, y, width, presses; public boolean visible = true, active = true;
        Widget(String key, int x, int y, int width) { this.message = new Message(key, true); this.x = x; this.y = y; this.width = width; }
        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
        public int getY() { return y; } public int getX() { return x; }
        public int getWidth() { return width; } public int getHeight() { return 20; }
        public void setY(int y) { this.y = y; } public void setX(int x) { this.x = x; }
        public void setWidth(int width) { this.width = width; }
        public int pressCount() { return presses; }
        public void press() { if (visible && active) presses++; }
    }
    public record Contents(String key) { public String getKey() { return key; } }
    public record Message(String text, boolean translated) {
        public static Message literal(String text) { return new Message(text, false); }
        public Object getContents() { return translated ? new Contents(text) : text; }
        public String getString() { return text; }
    }
}

