package io.elysian.magtop;

import io.elysian.core.config.TopKConfig;
import io.elysian.core.model.Node;
import io.elysian.core.model.Operator;
import io.elysian.core.model.Placement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implements the {@code findTopKCandidates} function from Algorithm (findtopk) in the paper.
 *
 * <p>Scoring formula (from §4 and supplementary):
 * <pre>
 *   score(n_j) = 0.40 * cpu_avail(n_j)
 *              + 0.30 * mem_avail(n_j)
 *              + 0.20 * locality(n_j, o_i, X)
 *              + 0.10 * hist_perf(n_j, o_i)
 * </pre>
 *
 * <p>Complexity: O(m) linear scan with a k-element min-heap.
 */
public class TopKSelector {

    private static final Logger LOG = LoggerFactory.getLogger(TopKSelector.class);

    /** Per-node inter-region average latency table (ms). Populated by FlinkMetricsCollector. */
    private final Map<String, Map<String, Double>> latencyTable;
    private final TopKConfig cfg;

    public TopKSelector(TopKConfig cfg,
                        Map<String, Map<String, Double>> latencyTable) {
        this.cfg          = cfg;
        this.latencyTable = latencyTable;
    }

    /**
     * Returns the top-k candidate nodes for placing {@code operator}.
     *
     * @param nodes    full set of cluster nodes
     * @param operator the operator seeking a new placement
     * @param k        number of candidates to return (from config)
     * @param placement the current global placement (for locality calc)
     * @return ordered list of at most k feasible nodes, highest score first
     */
    public List<Node> findTopK(Collection<Node> nodes, Operator operator,
                               int k, Placement placement) {
        // Min-heap keyed by score so we keep only the top-k
        PriorityQueue<Map.Entry<Node, Double>> heap =
                new PriorityQueue<>(k + 1, Comparator.comparingDouble(Map.Entry::getValue));

        for (Node node : nodes) {
            if (!basicResourceCheck(node, operator)) continue;

            double score = computeScore(node, operator, placement);

            heap.offer(Map.entry(node, score));
            if (heap.size() > k) heap.poll();   // evict lowest
        }

        // Drain heap into a descending list
        List<Node> result = new ArrayList<>(heap.size());
        while (!heap.isEmpty()) result.add(0, heap.poll().getKey());

        LOG.debug("TopK for op={}: selected {}/{} nodes (k={})",
                operator.getId(), result.size(), nodes.stream().count(), k);
        return result;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Fast O(1) feasibility check — does the node have any headroom for this operator?
     */
    private boolean basicResourceCheck(Node node, Operator operator) {
        return node.cpuAvailFraction() > 0.05
            && node.memAvailFraction() > 0.05;
    }

    private double computeScore(Node node, Operator operator, Placement placement) {
        double cpuAvail  = node.cpuAvailFraction();
        double memAvail  = node.memAvailFraction();
        double locality  = computeLocality(node, operator, placement);
        double histPerf  = node.getHistPerformanceScore();

        return cfg.getWeightCpu()      * cpuAvail
             + cfg.getWeightMemory()   * memAvail
             + cfg.getWeightLocality() * locality
             + cfg.getWeightHistPerf() * histPerf;
    }

    /**
     * locality(n_j, o_i, X) = average of 1/(1 + latency_to_neighbour_node) over all neighbours.
     */
    private double computeLocality(Node node, Operator operator, Placement placement) {
        List<String> neighbours = new ArrayList<>(operator.getUpstreamIds());
        neighbours.addAll(operator.getDownstreamIds());

        if (neighbours.isEmpty()) return 1.0;  // no neighbours → no penalty

        double sum = 0.0;
        for (String neighbourId : neighbours) {
            String neighbourNode = placement.nodeOf(neighbourId);
            if (neighbourNode == null) continue;
            double latency = getLatency(node.getId(), neighbourNode);
            sum += 1.0 / (1.0 + latency);
        }
        return sum / neighbours.size();
    }

    /** Looks up inter-node latency in ms; defaults to 20 ms if unknown. */
    private double getLatency(String fromId, String toId) {
        if (fromId.equals(toId)) return 0.0;
        return latencyTable
                .getOrDefault(fromId, Collections.emptyMap())
                .getOrDefault(toId, 20.0);
    }
}
