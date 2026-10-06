package dev.howlingwhispers.codaloader.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DependencySorter {
    List<ModCandidate> sort(Map<String, ModCandidate> mods) {
        Map<String, Integer> incoming = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();

        for (var entry : mods.entrySet()) {
            String id = entry.getKey();
            incoming.put(id, 0);
            outgoing.put(id, new ArrayList<>());
        }

        for (var entry : mods.entrySet()) {
            String id = entry.getKey();
            for (String dep : entry.getValue().metadata().depends()) {
                if (!mods.containsKey(dep)) {
                    throw new IllegalStateException("Mod '" + id + "' requires missing mod '" + dep + "'");
                }
                incoming.put(id, incoming.get(id) + 1);
                outgoing.get(dep).add(id);
            }
        }

        ArrayDeque<String> ready = new ArrayDeque<>();
        incoming.forEach((id, count) -> { if (count == 0) ready.add(id); });

        List<ModCandidate> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            ordered.add(mods.get(id));
            for (String dependent : outgoing.get(id)) {
                int next = incoming.compute(dependent, (k, v) -> v - 1);
                if (next == 0) ready.addLast(dependent);
            }
        }

        if (ordered.size() != mods.size()) {
            List<String> cycle = incoming.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .toList();
            throw new IllegalStateException("Dependency cycle detected among: " + String.join(", ", cycle));
        }
        return ordered;
    }
}
