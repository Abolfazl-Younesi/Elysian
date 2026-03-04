package io.elysian.core.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of the current operator-to-(node, parallelism) mapping.
 * MAGTOP works on an in-memory copy and swaps it atomically on commit.
 */
public class Placement {

    public record Assignment(String nodeId, int parallelism) {}

    private final Map<String, Assignment> map;

    public Placement() {
        this.map = new HashMap<>();
    }

    /** Copy constructor (for tentative evaluations). */
    public Placement(Placement other) {
        this.map = new HashMap<>(other.map);
    }

    public void assign(String operatorId, String nodeId, int parallelism) {
        map.put(operatorId, new Assignment(nodeId, parallelism));
    }

    public Optional<Assignment> get(String operatorId) {
        return Optional.ofNullable(map.get(operatorId));
    }

    public String nodeOf(String operatorId) {
        return Optional.ofNullable(map.get(operatorId))
                .map(Assignment::nodeId)
                .orElse(null);
    }

    public int parallelismOf(String operatorId) {
        return Optional.ofNullable(map.get(operatorId))
                .map(Assignment::parallelism)
                .orElse(1);
    }

    public Map<String, Assignment> asMap() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String toString() {
        return "Placement" + map.toString();
    }
}
