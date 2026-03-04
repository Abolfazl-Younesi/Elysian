package io.elysian.refs;

import io.elysian.core.model.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Measures workload intensities (CPU, memory, I/O) for each node.
 * In production this reads from cgroups / JMX; in simulation it uses
 * the node's synthetic utilisation value already set by the Flink metrics collector.
 */
public class WorkloadCharacteriser {

    private static final Logger LOG = LoggerFactory.getLogger(WorkloadCharacteriser.class);

    public record Intensities(double cpuIntensity, double memIntensity, double ioIntensity) {}

    private final CopyOnWriteArrayList<Node> nodeRegistry = new CopyOnWriteArrayList<>();

    public void registerNode(Node n) { nodeRegistry.add(n); }

    public Collection<Node> getNodes() { return List.copyOf(nodeRegistry); }

    /**
     * Characterises the workload of a node and returns CPU, memory, and I/O intensities.
     * In a real deployment, these are read from cgroups performance counters.
     */
    public Intensities characterize(Node node) {
        // Simulation: derive from the cpu/mem utilisation fractions already on the node
        double cpuI = node.getCpuUsedFraction();
        double memI = node.getMemUsedFraction() * 0.8;   // memory bandwidth proxy
        double ioI  = node.getCpuUsedFraction() * 0.3;   // I/O correlated with CPU
        return new Intensities(cpuI, memI, ioI);
    }

    /**
     * Returns the current CPU utilisation fraction for a node [0, 1].
     * Production: read from /proc/stat or cgroups.cpu.usage.
     */
    public double measureCpuUtilization(Node node) {
        return node.getCpuUsedFraction();
    }
}
