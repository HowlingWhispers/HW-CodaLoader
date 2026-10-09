package dev.howlingwhispers.codawolf;

/** Deterministic, offline combat and sleep rules. No Minecraft dependency. */
public final class CompanionRules {
    private CompanionRules() {}
    public static final double GUARD_RANGE_SQUARED = 16.0 * 16.0;
    public static final double RETREAT_HEALTH_RATIO = 0.25;

    /** Attack only an actual recent aggressor, while healthy and close to our owner. */
    public static boolean shouldDefend(boolean recentAggressor, boolean alive,
                                        double health, double maxHealth, double ownerDistanceSquared) {
        return recentAggressor && alive && maxHealth > 0
                && health > maxHealth * RETREAT_HEALTH_RATIO
                && ownerDistanceSquared <= GUARD_RANGE_SQUARED;
    }

    /** Never cancel vanilla targets just because the mod has no valid
     * manually assigned target. This preserves ordinary tame wolf AI goals. */
    public static boolean releaseAssignedTarget(boolean safe, boolean wasOurTarget) {
        return !safe && wasOurTarget;
    }

    /** Only a successful night skip after entering a bed can unlock respawn. */
    public static final class SleepGate {
        private boolean sawSleeping;
        private long lastDayTime = Long.MIN_VALUE;
        private int graceTicks;
        private boolean skippedWhileSleeping;
        public boolean tick(boolean sleeping, long dayTime) {
            boolean skippedNight = lastDayTime != Long.MIN_VALUE && dayTime - lastDayTime > 200;
            if (sleeping) { sawSleeping = true; graceTicks = 200; }
            if (sawSleeping && skippedNight) skippedWhileSleeping = true;
            boolean success = sawSleeping && skippedWhileSleeping && !sleeping;
            if (success) { sawSleeping = false; skippedWhileSleeping = false; graceTicks = 0; }
            else if (!sleeping && graceTicks > 0 && --graceTicks == 0) { sawSleeping = false; skippedWhileSleeping = false; }
            lastDayTime = dayTime;
            return success;
        }
    }
}
