package net.minecraft.client.gui.screens;

public final class PauseScreen extends Screen {
    @Override protected void init() {
        add("menu.returnToGame", width / 2 - 100, 60, 200);
        add("gui.advancements", 60, 84, 98);
        add("gui.stats", 162, 84, 98);
        for (int i = 0; i < 4; i++) add("pause.icon." + i, 90 + i * 24, 108, 20);
        add("menu.options", 60, 132, 98);
        add("menu.worldOptions", 162, 132, 98);
        add("menu.shareToLan", 162, 156, 98);
        add("menu.sendFeedback", 60, 156, 98);
        add("menu.reportBugs", 60, 180, 98);
        add("menu.returnToMenu", 60, 204, 200);
    }
}
