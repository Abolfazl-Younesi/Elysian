package io.elysian.magtop;

import io.elysian.core.config.MagtopConfig;
import io.elysian.core.model.Node;
import io.elysian.core.model.Operator;
import io.elysian.core.model.Placement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main MAGTOP optimisation loop (Algorithm: MAGTOP with Coordination).
 *
 * <p>Each outer iteration:
 * <ol>
 *   <li>Shuffle operators randomly</li>
 *   <li>Each operator evaluates top-k candidate nodes via {@link TopKSelector}</li>
 *   <li>Best-response: pick (node, parallelism) that maximises U_i</li>
 *   <li>Delegate migration to {@link MigrationDelegate} (coordination + rollback)</li>
 *   <li>Every {@code parameterUpdateInterval} iterations re-tune α,β,γ,θ</li>
 * </ol>
 *
 * <p>Convergence: no operator improves by more than ε, or maxIterations reached.
 */
public class MagtopPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(MagtopPlanner.class);

    private final MagtopConfig cfg;
    private final TopKSelector topKSelector;
    private final UtilityCalculator utilCalc;
    private final InitialPlacementStrategy initialStrategy;
    private final MigrationDelegate migrationDelegate;

    // Live copies of weights (tuned at runtime)
    private double alpha, beta, gamma, theta;

    public MagtopPlanner(MagtopConfig cfg,
                          TopKSelector topKSelector,
                          UtilityCalculator utilCalc,
                          InitialPlacementStrategy initialStrategy,
                          MigrationDelegate migrationDelegate) {
        this.cfg               = cfg;
        this.topKSelector      = topKSelector;
        this.utilCalc          = utilCalc;
        this.initialStrategy   = initialStrategy;
        this.migrationDelegate = migrationDelegate;
        this.alpha = cfg.getAlpha();
        this.beta  = cfg.getBeta();
        this.gamma = cfg.getGamma();
        this.theta = cfg.getTheta();
    }

    /**
     * Run MAGTOP starting from an initial greedy placement.
     *
     * @param operators all pipeline operators
     * @param nodes     all cluster nodes
     * @return the optimised {@link Placement}
     */
    public Placement optimise(List<Operator> operators, List<Node> nodes) {
        Placement placement = initialStrategy.computeInitialPlacement(operators, nodes);
        LOG.info("[MAGTOP] Starting with initial placement: {}", placement);

        int  iteration = 0;
        boolean improved = true;

        while (improved && iteration < cfg.getMaxIterations()) {
            improved = false;
            iteration++;

            // Shuffle operators to avoid systematic bias
            List<Operator> shuffled = new ArrayList<>(operators);
            Collections.shuffle(shuffled);

            for (Operator op : shuffled) {
                String currentNodeId  = op.getCurrentNodeId();
                int    currentP       = op.getCurrentParallelism();
                double currentUtility = utilCalc.calcUtility(op,
                        findNode(nodes, currentNodeId), currentP, placement, nodes);

                String bestNodeId  = currentNodeId;
                int    bestP       = currentP;
                double bestUtility = currentUtility;

                // Evaluate top-k candidate nodes
                List<Node> candidates = topKSelector.findTopK(nodes, op, cfg.getTopK().getK(), placement);

                for (Node candidateNode : candidates) {
                    for (int p : op.getParallelismOptions()) {
                        double u = utilCalc.calcUtility(op, candidateNode, p, placement, nodes);
                        if (u > bestUtility + cfg.getEpsilon()) {
                            bestUtility = u;
                            bestNodeId  = candidateNode.getId();
                            bestP       = p;
                        }
                    }
                }

                // Trigger migration if best is different from current
                if (!bestNodeId.equals(currentNodeId) || bestP != currentP) {
                    boolean success = migrationDelegate.migrate(op, findNode(nodes, bestNodeId), bestP, placement);
                    if (success) {
                        placement.assign(op.getId(), bestNodeId, bestP);
                        op.setCurrentNodeId(bestNodeId);
                        op.setCurrentParallelism(bestP);
                        improved = true;
                        LOG.debug("[MAGTOP] iter={} op={} moved {} → {} p={} ΔU=+{:.3f}",
                                iteration, op.getId(), currentNodeId, bestNodeId, bestP,
                                bestUtility - currentUtility);
                    }
                }
            }

            // Periodic weight tuning
            if (iteration % cfg.getParameterUpdateInterval() == 0) {
                adjustWeights(operators, nodes, placement);
            }
        }

        LOG.info("[MAGTOP] Converged at iteration {}/{}", iteration, cfg.getMaxIterations());
        return placement;
    }

    // -----------------------------------------------------------------------

    private Node findNode(List<Node> nodes, String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
    }

    /**
     * Simple adaptive weight tuning.
     * If avg E2E latency > SLO → increase β (penalise latency more).
     * If migration rate last interval > 0.3 → increase γ (discourage moves).
     */
    private void adjustWeights(List<Operator> operators, List<Node> nodes, Placement placement) {
        // Placeholder: production would read live Flink metrics here.
        // Demonstrate the re-normalisation pattern.
        double sum = alpha + beta + gamma + theta;
        alpha = alpha / sum;
        beta  = beta  / sum;
        gamma = gamma / sum;
        theta = theta / sum;
        LOG.debug("[MAGTOP] weights re-tuned α={:.3f} β={:.3f} γ={:.3f} θ={:.3f}",
                alpha, beta, gamma, theta);
    }

    /** Callback interface for migration execution (injected to break circular dep). */
    public interface MigrationDelegate {
        boolean migrate(Operator operator, Node targetNode, int targetParallelism, Placement current);
    }
}
