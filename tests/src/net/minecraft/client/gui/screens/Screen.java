package net.minecraft.client.gui.screens;

import dev.howlingwhispers.codaloader.bootstrap.CodaMenusTest;

/** Native lifecycle fixture; no test calls the customization helper. */
public class Screen extends CodaMenusTest.Screen {
    protected int width;
    public void init(int width, int height) {
        this.width = width;
        children().clear();
        init();
        // Outer native initialization can reposition widgets after subclass init.
        resetSettings();
    }
    protected void init() {
        add("menu.multiplayer", 60, 124, 200);
    }
    private void resetSettings() {
        for (CodaMenusTest.Widget widget : children())
            if (widget.getMessage().getString().equals("Coda's Settings")) widget.setY(180);
    }
    public void rebuildWidgets() { init(width, 240); }
    public void resize(int width, int height) { init(width, height); resetSettings(); }
    protected void repositionElements() { resetSettings(); }
    public void repositionForTest() { repositionElements(); }
}
