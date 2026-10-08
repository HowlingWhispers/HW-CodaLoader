package dev.howlingwhispers.codaloader.bootstrap;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import java.util.List;

public final class MenuLifecycleTest {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static CodaMenusTest.Widget named(Screen screen, String name) {
        return screen.children().stream().filter(w -> name.equals(w.getMessage().getString()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + name));
    }
    private static void title(TitleScreen screen) {
        check(screen.children().stream().noneMatch(w -> List.of("menu.multiplayer", "menu.online")
                .contains(w.getMessage().getString())), "native buttons removed before init returns");
        check(named(screen, "Coda's Settings").getY() == 124, "outer initialization also hooked");
        check(named(screen, "narrator.button.language").getY() == 148, "icon row ready before rendering");
        named(screen, "My Worlds").press();
    }
    private static void pause(PauseScreen screen) {
        check(screen.children().size() == 10, "removed pause controls gone synchronously");
        check(named(screen, "Coda's Settings").getY() == 132, "settings below small icons");
        check(named(screen, "World Rules").getY() == 132, "world options preserved beside settings");
        check(named(screen, "Save & Curl Up").getY() == 156, "quit below world options");
        for (int i = 0; i < 4; i++)
            check(named(screen, "pause.icon." + i).getY() == 108, "small icon kept on its own row");
        List<CodaMenusTest.Widget> widgets = screen.children();
        for (int i = 0; i < widgets.size(); i++)
            for (int j = i + 1; j < widgets.size(); j++) {
                var a = widgets.get(i); var b = widgets.get(j);
                check(!(a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                        && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY()),
                        "pause controls do not overlap");
            }
        for (var widget : widgets) {
            int previous = widget.pressCount();
            widget.press();
            check(widget.pressCount() == previous + 1, "retained action works");
        }
    }
    public static void main(String[] args) {
        TitleScreen title = new TitleScreen();
        title.init(320, 240); title(title);
        title.resize(420, 260); title(title);
        title.rebuildWidgets(); title(title);
        title.repositionForTest(); title(title);
        for (int i = 0; i < 8; i++) {
            PauseScreen pause = new PauseScreen();
            pause.init(320, 240); pause(pause); // Escape opens a fresh screen.
            pause.rebuildWidgets(); pause(pause);
            pause.resize(320, 260); pause(pause);
            pause.repositionForTest(); pause(pause);
        }
        Screen unrelated = new Screen();
        unrelated.init(320, 240);
        check(unrelated.children().size() == 1 && named(unrelated, "menu.multiplayer") != null,
                "base hook leaves unrelated screens untouched");
        System.out.println("Menu lifecycle agent tests passed: " + checks + " checks.");
    }
}
