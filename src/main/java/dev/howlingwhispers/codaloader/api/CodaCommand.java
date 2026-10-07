package dev.howlingwhispers.codaloader.api;

import java.util.List;

/** Runs on the Minecraft server thread; no Minecraft classes leak into the mod API. */
@FunctionalInterface
public interface CodaCommand {
    void execute(CodaCommandContext context, List<String> arguments) throws Exception;
}
