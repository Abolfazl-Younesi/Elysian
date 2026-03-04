package io.elysian.magtop;

import io.elysian.core.config.MagtopConfig;
import io.elysian.core.model.Node;
import io.elysian.core.model.Operator;
import io.elysian.core.model.Placement;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Computes the multi-objective utility function:
 * <pre>
 *   U_i(n_j, p) = α·RU(i,j) + β·LU(i,j) - γ·MC(i,j) + θ·CA(i,j)
 * </pre>
 *
 * Weights (α,β,γ,θ) are read from {@link MagtopConfig} and can be re-tuned
 * by {@link MagtopPlanner} every {@code parameterUpdateInterval} iterations.
 */
public class UtilityCalculator {

    private final MagtopConfig cfg;

    /** Per-node current E2E latency (ms) as reported by Flink metrics. */
    private final Map<String, Double> e2eLatencyMs;

    /** Link bandwidth (Gbps) between node pairs — for migration cost. */
    private final Map<String, Map<String, Double>> bwTable;

    /** Data-flow volume (Mbps) between operator pairs — for CA term. */
    private final Map<String, Map<String, Double>> flowTable;

    /** Latency between nodes (ms) — for CA proximity term. */
    private final Map<String, Map<String, Double>> latencyTable;

    public UtilityCalculator(MagtopConfig cfg,
                              Map<String, Double> e2eLatencyMs,
                              Map<String, Map<String, Double>> bwTable,
                              Map<String, Map<String, Double>> flowTable,
                              Map<String, Map<String, Double>> latencyTable) {
        this.cfg          = cfg;
        this.e2eLatencyMs = e2eLatencyMs;
        this.bwTable      = bwTable;
        this.flowTable    = flowTable;
        this.latencyTable = latencyTable;
    }

    /**
     * Calculate utility for placing {@code operator} on {@code node}
     * with {@code parallelism} in the context of {@code placement}.
     */
    public double calcUtility(Operator operator, Node node, int parallelism,
                               Placement placement, Collection<Node> allNodes) {
        double ru = resourceUtility(node);
        double lu = latencyUtility(operator, node);
        double mc = migrationCost(operator, node);
        double ca = communicationAffinity(operator, node, placement, allNodes);

        return cfg.getAlpha() * ru
             + cfg.getBeta()  * lu
             - cfg.getGamma() * mc
             + cfg.getTheta() * ca;
    }

    // -----------------------------------------------------------------------

    /**
     * RU(i,j) = w_r*(1 - cpu_load/cpu_cap) + w_m*(1 - mem_load/mem_cap)
     * Using equal split 0.5/0.5 (can be made configurable).
     */
    double resourceUtility(Node node) {
        return 0.5 * node.cpuAvailFraction()
             + 0.5 * node.memAvailFraction();
    }

    /**
     * LU(i,j) = 1 - EL_ij / MaxAcceptableLatency
     * Clipped to [0, 1].
     */
    double latencyUtility(Operator operator, Node node) {
        double el  = e2eLatencyMs.getOrDefault(node.getId(), cfg.getLatencySloMs());
        double slo = cfg.getLatencySloMs();
        return Math.max(0.0, 1.0 - el / slo);
    }

    /**
     * MC(i,j) = 0                                      if already on j
     *         = (StateSize / BW(cur, j)) * importanceFactor   otherwise
     * Normalised to [0,1] by dividing by a reference cost of 10 s.
     */
    double migrationCost(Operator operator, Node targetNode) {
        if (operator.getCurrentNodeId().equals(targetNode.getId())) return 0.0;

        double bw = Optional.ofNullable(bwTable.get(operator.getCurrentNodeId()))
                .map(m -> m.get(targetNode.getId()))
                .orElse(1.0);   // 1 Gbps default

        double importanceFactor = operator.isStateful() ? 1.5 : 1.0;
        double rawCostSec = (operator.getStateSizeMb() / (bw * 1024.0)) * importanceFactor;
        // Normalise: 10 s reference cap
        return Math.min(1.0, rawCostSec / 10.0);
    }

    /**
     * CA(i,j) = Σ_k Flow(i,k) * Prox(n_j, loc(k))
     * Normalised by maximum possible flow.
     */
    double communicationAffinity(Operator operator, Node targetNode,
                                  Placement placement, Collection<Node> allNodes) {
        double total = 0.0;
        double flowSum = 0.0;

        Map<String, Double> flows = flowTable.getOrDefault(operator.getId(), Map.of());

        for (Map.Entry<String, Double> entry : flows.entrySet()) {
            String neighbourOpId = entry.getKey();
            double flow          = entry.getValue();
            flowSum += flow;

            String neighbourNodeId = placement.nodeOf(neighbourOpId);
            if (neighbourNodeId == null) continue;

            double latency = Optional.ofNullable(latencyTable.get(targetNode.getId()))
                    .map(m -> m.get(neighbourNodeId))
                    .orElse(20.0);
            double proximity = 1.0 / (1.0 + latency);
            total += flow * proximity;
        }
        return flowSum == 0 ? 0.0 : total / flowSum;     // normalise
    }

    // Called by MagtopPlanner for weight self-tuning
    public void updateAlpha(double a) { /* alpha tuning via reflection or subclass */ }
    public double getAlpha() { return cfg.getAlpha(); }
    public double getBeta()  { return cfg.getBeta(); }
    public double getGamma() { return cfg.getGamma(); }
    public double getTheta() { return cfg.getTheta(); }
}
