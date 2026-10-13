package dev.howlingwhispers.codaloader.api;

import java.util.List;

/** Standalone regression fixture for HW's shared recipe-book category declarations. */
public final class RecipeBookCategoriesTest {
    public static void main(String[] args) {
        CodaRecipeBookCategories.resetForTests();
        CodaRecipeBookCategories.register("howl_sorting_pipes", "pipes", "Sorting Pipes",
                "howl_sorting_pipes:sorting_pipe",
                List.of("howl_sorting_pipes:transport_pipe",
                        "howl_sorting_pipes:sorting_pipe"));
        CodaRecipeBookCategories.register("hw_waypoints", "waypoints", "Waypoints",
                "minecraft:compass", List.of("minecraft:compass"));
        var snapshot = CodaRecipeBookCategories.snapshot();
        check(snapshot.size() == 2, "missing categories");
        check(snapshot.get(0).id().equals("howl_sorting_pipes:pipes"), "wrong ordering");
        check(snapshot.get(1).id().equals("hw_waypoints:waypoints"), "wrong namespace");
        check(snapshot.get(0).itemIds().size() == 2, "missing registered items");
        try {
            snapshot.get(0).itemIds().add("minecraft:dirt");
            throw new AssertionError("snapshot must be immutable");
        } catch (UnsupportedOperationException expected) { }
        fails(() -> CodaRecipeBookCategories.register("howl_sorting_pipes", "pipes",
                "Duplicate", "minecraft:stone", List.of("minecraft:stone")));
        fails(() -> CodaRecipeBookCategories.register("hw_waypoints", "other:pipes",
                "Stolen", "minecraft:stone", List.of("minecraft:stone")));
        fails(() -> CodaRecipeBookCategories.register("hw_waypoints", "bad",
                "Invalid", "minecraft:stone", List.of("INVALID ITEM")));
        CodaRecipeBookCategories.freeze();
        fails(() -> CodaRecipeBookCategories.register("hw_waypoints", "after",
                "Late registration", "minecraft:stone", List.of("minecraft:stone")));
        CodaRecipeBookCategories.resetForTests();
        check(CodaRecipeBookCategories.snapshot().isEmpty(), "test reset");
        System.out.println("HW recipe category registry OK");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void fails(Runnable callback) {
        try { callback.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected registration rejection");
    }
}
