package net.minecraft.client.gui.screens.worldselection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Simulation of Snapshot 3's private create-world selection flow. Not a real game. */
public class CreateWorldScreen {
    private final Path staging;
    private final PackRepository repository = new PackRepository();
    private int applyCount;

    public CreateWorldScreen(Path staging) { this.staging = staging; }
    public void init() { /* production H.O.W.L. ASM injects on return */ }
    public WorldCreationUiState getUiState() { return new WorldCreationUiState(); }

    private Pair getDataPackSelectionSettings(Object config) throws Exception {
        Files.createDirectories(staging);
        return new Pair(staging, repository);
    }

    @SuppressWarnings("unused")
    private void tryApplyNewDataPacks(PackRepository packs, boolean experiment,
                                      Consumer<Object> refusal) {
        applyCount++;
        if (!packs.getSelectedIds().contains("file/hw-quiet-underground.zip"))
            throw new AssertionError("World pack must be selected before first data reload");
    }

    public int applyCount() { return applyCount; }
    public PackRepository repository() { return repository; }

    public static final class Pair {
        private final Path first;
        private final PackRepository second;
        public Pair(Path first, PackRepository second) { this.first = first; this.second = second; }
        public Path getFirst() { return first; }
        public PackRepository getSecond() { return second; }
    }
    public static final class PackRepository {
        private final Set<String> ids = new LinkedHashSet<>();
        private final Set<String> selected = new LinkedHashSet<>();
        public void reload() { ids.add("file/hw-quiet-underground.zip"); }
        public Collection<String> getAvailableIds() { return ids; }
        public Collection<String> getSelectedIds() { return selected; }
        public void setSelected(Collection<String> all) { selected.clear(); selected.addAll(all); }
    }
    public static final class WorldCreationUiState {
        public WorldCreationContext getSettings() { return new WorldCreationContext(); }
    }
    public static final class WorldCreationContext {
        public Object dataConfiguration() { return new Object(); }
    }
}
