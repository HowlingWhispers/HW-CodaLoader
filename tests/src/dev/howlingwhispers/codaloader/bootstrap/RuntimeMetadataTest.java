package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import java.nio.charset.StandardCharsets;

/** The launcher checks compatibility without running Java in the player's profile. */
public final class RuntimeMetadataTest {
    public static void main(String[] args) throws Exception {
        try (var input=RuntimeMetadataTest.class.getResourceAsStream("/codaloader/runtime-version.json")) {
            if(input==null) throw new AssertionError("Launcher-readable runtime version missing");
            String text=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            if(!text.contains("\""+CodaTarget.LOADER_VERSION+"\"") || !text.contains("\""+CodaTarget.MINECRAFT_VERSION+"\""))
                throw new AssertionError("Runtime metadata disagrees with loader target");
        }
        System.out.println("PASS: launcher-readable runtime compatibility metadata");
    }
}
