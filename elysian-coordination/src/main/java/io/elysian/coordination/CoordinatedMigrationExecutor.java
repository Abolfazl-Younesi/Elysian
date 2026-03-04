package io.elysian.coordination;

import io.elysian.core.config.CoordinationConfig;
import io.elysian.core.model.Node;
import io.elysian.core.model.Operator;
import io.elysian.core.model.Placement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed Coordination Protocol (Algorithm: coordinatedMigration).
 *
 * <p>Guarantees serialised, conflict-free operator migrations via:
 * <ol>
 *   <li>Acquire distributed lock with timeout (ZooKeeper in production,
 *       in-JVM {@link InMemoryLockManager} in dev/test)</li>
 *   <li>Check for resource conflicts on the target node</li>
 *   <li>Reserve resources + broadcast decision to neighbours</li>
 *   <li>Execute migration; rollback on failure</li>
 *   <li>Exponential back-off retry (max 5 attempts)</li>
 * </ol>
 */
public class CoordinatedMigrationExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CoordinatedMigrationExecutor.class);

    public enum Result { SUCCESS, COORDINATION_FAILED }

    private final CoordinationConfig     cfg;
    private final DistributedLockManager lockManager;
    private final MigrationCheckpointStore checkpointStore;

    public CoordinatedMigrationExecutor(CoordinationConfig cfg,
                                         DistributedLockManager lockManager,
                                         MigrationCheckpointStore checkpointStore) {
        this.cfg             = cfg;
        this.lockManager     = lockManager;
        this.checkpointStore = checkpointStore;
    }

    /**
     * Attempts to migrate {@code operator} to {@code targetNode} with {@code targetParallelism}.
     *
     * @param operator          the operator to migrate
     * @param targetNode        the selected placement node
     * @param targetParallelism desired parallelism at the target
     * @param placement         current global placement (updated in-place on success)
     * @return {@link Result#SUCCESS} or {@link Result#COORDINATION_FAILED}
     */
    public Result execute(Operator operator, Node targetNode, int targetParallelism,
                          Placement placement) {
        long backoffMs = cfg.getInitialBackoffMs();

        for (int attempt = 0; attempt < cfg.getMaxRetries(); attempt++) {
            String lockToken = lockManager.acquireLock(
                    operator.getId(), cfg.getLockTimeoutSec() * 1000L);

            if (lockToken != null) {
                try {
                    if (!hasConflict(targetNode, targetParallelism)) {
                        // Reserve resources
                        reserveResources(targetNode, operator, targetParallelism);
                        // Broadcast decision
                        broadcastDecision(operator, targetNode, targetParallelism);
                        // Save checkpoint for rollback
                        checkpointStore.save(operator);
                        // Execute
                        boolean ok = executeMigration(operator, targetNode, targetParallelism);
                        if (ok) {
                            releaseOldResources(targetNode, operator);
                            LOG.info("[COORD] Migration SUCCESS op={} → node={} p={}",
                                    operator.getId(), targetNode.getId(), targetParallelism);
                            return Result.SUCCESS;
                        } else {
                            rollback(operator, targetNode);
                        }
                    } else {
                        LOG.debug("[COORD] Conflict for op={} on node={} attempt={}",
                                operator.getId(), targetNode.getId(), attempt + 1);
                    }
                } finally {
                    lockManager.releaseLock(lockToken);
                }
            }

            // Exponential back-off
            try { Thread.sleep(backoffMs); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = (long) (backoffMs * cfg.getBackoffMultiplier());
        }

        LOG.warn("[COORD] COORDINATION_FAILED op={} after {} retries",
                operator.getId(), cfg.getMaxRetries());
        return Result.COORDINATION_FAILED;
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private boolean hasConflict(Node targetNode, int targetParallelism) {
        // In simulation: always clear (production checks K8s resource reservations)
        return targetNode.cpuAvailFraction() < 0.05 || targetNode.memAvailFraction() < 0.05;
    }

    private void reserveResources(Node node, Operator op, int parallelism) {
        // Optimistic: mark 10% CPU per parallelism degree
        double extra = 0.10 * parallelism;
        node.setCpuUsedFraction(Math.min(1.0, node.getCpuUsedFraction() + extra));
    }

    private void broadcastDecision(Operator op, Node target, int parallelism) {
        LOG.debug("[COORD] Broadcast op={} → node={} p={}", op.getId(), target.getId(), parallelism);
    }

    private boolean executeMigration(Operator op, Node target, int parallelism) {
        // In production: call FlinkRescalingAdapter; simulate success here
        op.setCurrentNodeId(target.getId());
        op.setCurrentParallelism(parallelism);
        return true;
    }

    private void rollback(Operator op, Node failedNode) {
        LOG.warn("[COORD] Rolling back op={} from node={}", op.getId(), failedNode.getId());
        checkpointStore.restore(op);
        // Release any partially reserved resources
        failedNode.setCpuUsedFraction(Math.max(0, failedNode.getCpuUsedFraction() - 0.10));
    }

    private void releaseOldResources(Node node, Operator op) {
        // No-op in simulation; production sends K8s resource release
    }
}
