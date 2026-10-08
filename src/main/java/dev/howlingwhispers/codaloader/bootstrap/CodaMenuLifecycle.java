package dev.howlingwhispers.codaloader.bootstrap;

/** Called by the screen initialization hooks on Minecraft's GUI thread. */
public final class CodaMenuLifecycle {
    private static boolean failureReported;
    private CodaMenuLifecycle() {}

    public static void afterInitialize(Object screen) {
        String name = screen.getClass().getName();
        boolean title = name.equals("net.minecraft.client.gui.screens.TitleScreen");
        if (!title && !name.equals("net.minecraft.client.gui.screens.PauseScreen")) return;
        String screenId = title ? dev.howlingwhispers.codaloader.api.CodaScreens.TITLE
                : dev.howlingwhispers.codaloader.api.CodaScreens.PAUSE;
        try {
            // One owner of native widgets per lifecycle event. Compatibility mode
            // can be selected with -Dcodaloader.menu.mode=legacy.
            boolean owned = !"legacy".equalsIgnoreCase(System.getProperty("codaloader.menu.mode", "owned"));
            if (!owned || !CodaOwnedMenus.apply(screen, title))
                CodaMenus.apply(screen, title);
            CodaScreenBridge.afterNativeInitialize(screen, screenId);
        } catch (Throwable failure) {
            if (!failureReported) {
                failureReported = true;
                System.err.println("[CodaLoader] Menu initialization warning: " + failure);
            }
        }
    }
}
