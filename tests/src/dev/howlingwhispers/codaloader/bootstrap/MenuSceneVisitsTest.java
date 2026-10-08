package dev.howlingwhispers.codaloader.bootstrap;

public final class MenuSceneVisitsTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        MenuSceneVisits visits = new MenuSceneVisits();
        check(!visits.observe(true, false), "startup title needs no extra reload");
        for (int i = 0; i < 5; i++) {
            check(!visits.observe(false, false), "opening settings or world selector causes no reload");
            check(!visits.observe(true, false), "returning from title submenus leaves music playing");
        }
        check(!visits.observe(false, true), "gameplay records world visit without reloading");
        check(!visits.observe(false, true), "pause or in-world settings cause no reload");
        check(!visits.observe(false, false), "world unload does not reload before title");
        check(visits.observe(true, false), "world exit allows one scene rotation");
        check(!visits.observe(true, false), "subsequent title polls do not repeat rotation");
        check(!visits.observe(false, false) && !visits.observe(true, false),
                "settings after world exit do not repeat rotation");
        check(!visits.observe(false, true) && !visits.observe(true, true),
                "title transition waits for world to finish unloading");
        check(visits.observe(true, false), "next world session can rotate once");
        System.out.println("Menu scene visit tests passed: " + checks + " checks.");
    }
}
