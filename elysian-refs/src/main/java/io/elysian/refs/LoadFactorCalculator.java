package io.elysian.refs;

/**
 * Computes the load factor L_i = (u_i / Σ u_j) × m
 * with safe fallback to 1.0 when all nodes are idle.
 */
public class LoadFactorCalculator {

    /**
     * @param idx   index of the target node in the utils array
     * @param utils observed CPU utilisation fractions for all m nodes
     * @return load factor L_i ∈ (0, 2m] — values > 1 indicate above-average load
     */
    public double compute(int idx, double[] utils) {
        int m = utils.length;
        double sum = 0.0;
        for (double u : utils) sum += u;

        if (sum <= 0.0) return 1.0;   // safe fallback when all idle

        return (utils[idx] / sum) * m;
    }
}
