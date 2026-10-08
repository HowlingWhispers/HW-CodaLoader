import dev.howlingwhispers.codawolf.CodaWolfMod;
import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaCommands;
import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real public API command registration, no Minecraft installed: fail safely. */
public final class CodaWolfCommandTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static final class Player implements CodaCommandContext {
        final UUID id = UUID.randomUUID();
        final Path world;
        final List<String> messages = new ArrayList<>();
        Player(Path world) { this.world = world; }
        public UUID playerId() { return id; }
        public Path worldDirectory() { return world; }
        public CodaPosition position() { throw new UnsupportedOperationException(); }
        public void teleport(CodaPosition where) { throw new UnsupportedOperationException(); }
        public void reply(String message) { messages.add(message); }
        boolean said(String text) { return messages.stream().anyMatch(m -> m.contains(text)); }
        void clear() { messages.clear(); }
    }
    public static void main(String[] args) throws Exception {
        Path world = Files.createTempDirectory("coda-command-");
        new CodaWolfMod().onInitialize(new CodaContext("0.0.26", "26.4-snapshot-3",
                world, world.resolve("config"), "coda_wolf", List.of("coda_wolf")));
        var command = CodaCommands.registrations().stream()
                .filter(r -> r.name().equals("codawolf")).findFirst().orElseThrow().command();
        Player player = new Player(world);
        command.execute(player, List.of("status"));
        check(player.said("not spawned yet"), "new world correctly reports absent companion");
        player.clear();
        command.execute(player, List.of("diagnose"));
        check(player.said("server ticks=0"), "missing server tick hook detected in command");
        check(player.said("Native Minecraft bridge FAILED:"), "missing game API diagnosed safely");
        player.clear();
        command.execute(player, List.of("summon"));
        check(player.said("Cannot access Minecraft singleplayer server:"),
                "manual spawn gives actionable mapping error without crashing");
        check(!Files.exists(world.resolve("data")), "failed native spawn never writes bogus wolf state");
        player.clear();
        command.execute(player, List.of("help"));
        check(player.said("/codawolf summon"), "help exposes direct summon command");
        player.clear();
        command.execute(player, List.of("unsupported"));
        check(player.said("Usage:"), "invalid subcommand rejected");
        System.out.println("PASS: " + assertions + " no-hook command and diagnostic assertions");
    }
}
