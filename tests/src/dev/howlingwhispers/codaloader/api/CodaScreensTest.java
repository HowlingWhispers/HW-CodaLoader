package dev.howlingwhispers.codaloader.api;

import java.util.List;

public final class CodaScreensTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        CodaScreens registry = new CodaScreens();
        check(registry.resolve(CodaScreens.TITLE, null, null) == null, "empty registry preserves vanilla");
        registry.register(new CodaScreens.Provider(CodaScreens.TITLE, "z-low", 0, (client, previous) -> "low"));
        registry.register(new CodaScreens.Provider(CodaScreens.TITLE, "a-high", 100, (client, previous) -> null));
        registry.register(new CodaScreens.Provider(CodaScreens.TITLE, "b-high", 100, (client, previous) -> "high"));
        check("high".equals(registry.resolve(CodaScreens.TITLE, null, null)), "deferred provider falls through");
        check(List.of("a-high", "b-high", "z-low").equals(registry.providers(CodaScreens.TITLE)
                .stream().map(CodaScreens.Provider::modId).toList()), "priority and tie order deterministic");
        registry.register(new CodaScreens.Provider(CodaScreens.PAUSE, "broken", 100,
                (client, previous) -> { throw new IllegalStateException("test failure"); }));
        registry.register(new CodaScreens.Provider(CodaScreens.PAUSE, "working", 0,
                (client, previous) -> "pause"));
        check("pause".equals(registry.resolve(CodaScreens.PAUSE, null, null)), "broken provider isolated");
        check(registry.resolve("hw:clipboard", null, null) == null, "unregistered custom screen returns null");
        check(CodaScreens.isVanillaScreen(CodaScreens.TITLE), "native title recognized");
        check(!CodaScreens.isVanillaScreen("hw:clipboard"), "mod screen not reserved");
        try {
            registry.register(new CodaScreens.Provider(CodaScreens.TITLE, "a-high", 0, (c, p) -> "duplicate"));
            throw new AssertionError("expected duplicate rejection");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new CodaScreens.Provider("not-namespaced", "test", 0, (c, p) -> null);
            throw new AssertionError("expected namespace validation");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println("CodaScreens registry tests passed: " + checks + " checks.");
    }
}
