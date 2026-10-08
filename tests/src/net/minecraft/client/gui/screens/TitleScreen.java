package net.minecraft.client.gui.screens;

public final class TitleScreen extends Screen {
    @Override protected void init() {
        add("menu.singleplayer", width / 2 - 100, 100, 200);
        add("menu.multiplayer", 60, 124, 200);
        add("menu.online", 60, 148, 200);
        add("menu.options", 60, 172, 98);
        add("menu.quit", 162, 172, 98);
        add("narrator.button.language", 36, 172, 20);
        add("narrator.button.accessibility", 264, 172, 20);
        if (width < 400) return; // A separate return must receive the callback too.
        add("another_mod.action", 4, 8, 80);
    }
}
