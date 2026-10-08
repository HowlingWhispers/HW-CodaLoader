package dev.howlingwhispers.codaloader.bootstrap;

/** Rotate the title scene after gameplay, never after visiting title-menu submenus. */
final class MenuSceneVisits {
    private boolean worldVisited;
    boolean observe(boolean titleScreen, boolean worldLoaded) {
        if (worldLoaded) {
            worldVisited = true;
            return false;
        }
        if (titleScreen && worldVisited) {
            worldVisited = false;
            return true;
        }
        return false;
    }
}
