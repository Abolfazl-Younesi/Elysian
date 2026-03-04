package io.elysian.dfd;

import io.elysian.core.config.DfdConfig;
import io.elysian.core.model.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Dynamic Functional Decomposition (DFD) Manager.
 *
 * <p>Monitors operator runtime profiles and decides whether to split a
 * monolithic operator into micro-operators based on CPU hot-path threshold
 * and minimum state size. After decomposition, the new micro-operators are
 * returned for MAGTOP to place independently.
 */
public class DfdManager {

    private static final Logger LOG = LoggerFactory.getLogger(DfdManager.class);

    private final DfdConfig cfg;
    // Maps operatorId → profiled CPU fraction of hot path
    private final Map<String, Double> hotPathCpuFraction = new HashMap<>();
    // Maps operatorId → observed state size in MB
    private final Map<String, Double> observedStateSizes = new HashMap<>();

    public DfdManager(DfdConfig cfg) {
        this.cfg = cfg;
    }

    /** Update profiling data for an operator. */
    public void updateProfile(String operatorId, double hotPathCpu, double stateSizeMb) {
        hotPathCpuFraction.put(operatorId, hotPathCpu);
        observedStateSizes.put(operatorId, stateSizeMb);
    }

    /**
     * Evaluates whether the operator should be decomposed.
     * Returns the original operator (unchanged) or a list of two micro-operators.
     *
     * @param operator the monolithic operator to evaluate
     * @return list with 1 element (no split) or 2 micro-operators (split)
     */
    public List<Operator> evaluateDecomposition(Operator operator) {
        if (!cfg.isEnabled()) return List.of(operator);

        double hotFrac   = hotPathCpuFraction.getOrDefault(operator.getId(), 0.0);
        double stateSize = observedStateSizes.getOrDefault(operator.getId(), 0.0);

        if (hotFrac < cfg.getCpuHotPathThreshold()
                || stateSize < cfg.getMinStateSizeMb()) {
            return List.of(operator);   // no decomposition warranted
        }

        // Decompose: hot path (stateless-ish) + cold path (stateful)
        LOG.info("[DFD] Decomposing op={} hotFrac={:.2f} stateSize={:.0f}MB",
                operator.getId(), hotFrac, stateSize);

        Operator hotOp = new Operator(
                operator.getId() + "_hot",
                false,                  // hot path is typically stateless after split
                stateSize * 0.10,       // small slice of state
                operator.getCurrentNodeId(),
                operator.getCurrentParallelism(),
                operator.getParallelismOptions());

        Operator coldOp = new Operator(
                operator.getId() + "_cold",
                true,                   // cold path holds the bulk of state
                stateSize * 0.90,
                operator.getCurrentNodeId(),
                1,
                List.of(1, 2));

        // Wire DAG: upstream → hot → cold → downstream
        for (String up : operator.getUpstreamIds()) hotOp.addUpstream(up);
        hotOp.addDownstream(coldOp.getId());
        coldOp.addUpstream(hotOp.getId());
        for (String down : operator.getDownstreamIds()) coldOp.addDownstream(down);

        return List.of(hotOp, coldOp);
    }
}
