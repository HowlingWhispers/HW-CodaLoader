package dev.howlingwhispers.codaloader.bootstrap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Updates shipped presentation text without changing a player's custom splash file. */
final class HowlSplashes {
    static final String CODA_HUFF = "COWL?! ...no. *huff* H.O.W.L.!";

    static String shipped(String source) {
        Set<String> lines = new LinkedHashSet<>();
        source.lines().map(line -> line.replace("CodaLoader", "H.O.W.L.")
                .replace("C.M.L.", "H.O.W.L.").replace("CML", "H.O.W.L."))
                .filter(line -> !line.isBlank()).forEach(lines::add);
        lines.addAll(List.of(CODA_HUFF, "Howling Open Works Loader!",
                "Reviving the past. Modding the future."));
        return String.join("\n", lines) + "\n";
    }

    private HowlSplashes() {}
}
