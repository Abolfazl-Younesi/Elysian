package io.elysian.coordination;

import io.elysian.core.model.Operator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores pre-migration operator snapshots for rollback.
 * Production stores these in ZooKeeper with TTL; here an in-memory map suffices.
 */
public class MigrationCheckpointStore {

    private record Snapshot(String nodeId, int parallelism, double stateSizeMb) {}

    private final Map<String, Snapshot> store = new ConcurrentHashMap<>();

    /** Take a snapshot of the operator's current placement before migration. */
    public void save(Operator op) {
        store.put(op.getId(),
                new Snapshot(op.getCurrentNodeId(), op.getCurrentParallelism(), op.getStateSizeMb()));
    }

    /** Restore the operator to its pre-migration state. Returns true if snapshot existed. */
    public boolean restore(Operator op) {
        Snapshot snap = store.remove(op.getId());
        if (snap == null) return false;
        op.setCurrentNodeId(snap.nodeId());
        op.setCurrentParallelism(snap.parallelism());
        op.setStateSizeMb(snap.stateSizeMb());
        return true;
    }
}
