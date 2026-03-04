package io.elysian.magtop;

import io.elysian.core.model.Node;
import io.elysian.core.model.Operator;
import io.elysian.core.model.Placement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Greedy initial placement (Algorithm 1 – Initial Placement Strategy).
 *
 * <p>Topological sort of operators (upstream before downstream),
 * then for each operator pick the node maximising:
 *   score = - comm_cost_to_parents + resource_headroom
 */
public class InitialPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(InitialPlacementStrategy.class);

    private final Map<String, Map<String, Double>> latencyTable;

    public InitialPlacementStrategy(Map<String, Map<String, Double>> latencyTable) {
        this.latencyTable = latencyTable;
    }

    /**
     * Computes an initial placement for the given operators on the available nodes.
     *
     * @param operators all operators in the pipeline (any order)
     * @param nodes     available cluster nodes
     * @return initial {@link Placement}
     */
    public Placement computeInitialPlacement(List<Operator> operators, List<Node> nodes) {
        if (nodes.isEmpty()) throw new IllegalArgumentException("No nodes available");

        Placement placement = new Placement();
        Map<String, Double> nodeLoadFraction = new HashMap<>();
        for (Node n : nodes) nodeLoadFraction.put(n.getId(), n.getCpuUsedFraction());

        // Build a simple map for quick lookup
        Map<String, Operator> opMap = new HashMap<>();
        for (Operator op : operators) opMap.put(op.getId(), op);

        List<Operator> sorted = topologicalSort(operators, opMap);

        for (Operator op : sorted) {
            Node bestNode = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Node node : nodes) {
                if (!basicFeasibility(node)) continue;
                double score = computePlacementScore(op, node, placement, nodeLoadFraction);
                if (score > bestScore) {
                    bestScore = score;
                    bestNode  = node;
                }
            }

            if (bestNode == null) {
                // Fallback: pick least loaded node
                bestNode = nodes.stream()
                        .min(Comparator.comparingDouble(n -> nodeLoadFraction.getOrDefault(n.getId(), 0.0)))
                        .orElseThrow();
                LOG.warn("No feasible node for op={}; falling back to least-loaded {}", op.getId(), bestNode.getId());
            }

            int parallelism = 1;
            placement.assign(op.getId(), bestNode.getId(), parallelism);
            nodeLoadFraction.merge(bestNode.getId(), 0.1, Double::sum);   // rough load estimate
            op.setCurrentNodeId(bestNode.getId());
            op.setCurrentParallelism(parallelism);
        }

        LOG.info("Initial placement computed for {} operators on {} nodes", sorted.size(), nodes.size());
        return placement;
    }

    // -----------------------------------------------------------------------

    private boolean basicFeasibility(Node node) {
        return node.cpuAvailFraction() > 0.05 && node.memAvailFraction() > 0.05;
    }

    private double computePlacementScore(Operator op, Node node,
                                          Placement current, Map<String, Double> loads) {
        // Minimise communication cost with parents
        double commCost = 0.0;
        for (String parentId : op.getUpstreamIds()) {
            String parentNode = current.nodeOf(parentId);
            if (parentNode != null) {
                commCost += getLatency(parentNode, node.getId());
            }
        }
        // Prefer nodes with available resources
        double headroom = 1.0 - loads.getOrDefault(node.getId(), 0.0);
        return headroom - 0.01 * commCost;  // ms scaled down
    }

    /**
     * Kahn's algorithm — upstream operators first.
     */
    private List<Operator> topologicalSort(List<Operator> operators, Map<String, Operator> opMap) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (Operator op : operators) inDegree.put(op.getId(), 0);
        for (Operator op : operators) {
            for (String downId : op.getDownstreamIds()) {
                inDegree.merge(downId, 1, Integer::sum);
            }
        }

        Queue<Operator> queue = new ArrayDeque<>();
        for (Operator op : operators) {
            if (inDegree.getOrDefault(op.getId(), 0) == 0) queue.add(op);
        }

        List<Operator> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            Operator cur = queue.poll();
            sorted.add(cur);
            for (String downId : cur.getDownstreamIds()) {
                int d = inDegree.merge(downId, -1, Integer::sum);
                if (d == 0 && opMap.containsKey(downId)) queue.add(opMap.get(downId));
            }
        }
        // Append any remaining (handles cycles gracefully)
        for (Operator op : operators) {
            if (!sorted.contains(op)) sorted.add(op);
        }
        return sorted;
    }

    private double getLatency(String from, String to) {
        if (from.equals(to)) return 0.0;
        return latencyTable.getOrDefault(from, Map.of()).getOrDefault(to, 20.0);
    }
}
