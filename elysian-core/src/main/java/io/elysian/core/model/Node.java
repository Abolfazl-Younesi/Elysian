package io.elysian.core.model;

import java.util.concurrent.atomic.AtomicDouble;

/**
 * Represents a physical compute node (edge or cloud) in the cluster.
 * All resource usage fields are thread-safe for concurrent ReFS updates.
 */
public class Node {

    private final String   id;
    private final String   region;
    private final NodeType nodeType;

    // Capacity (immutable)
    private final int    cpuCores;
    private final double cpuGhz;
    private final double memGib;
    private final double bwGbps;     // network bandwidth

    // Current usage (mutable, read by ReFS and MAGTOP concurrently)
    private final AtomicDouble cpuUsedFraction = new AtomicDouble(0.0);
    private final AtomicDouble memUsedFraction = new AtomicDouble(0.0);

    // ReFS state
    private volatile double currentFreqGhz;
    private volatile double currentVoltage;

    // Historical performance score (updated by CoordinatedMigrationExecutor)
    private volatile double histPerformanceScore = 1.0;

    public Node(String id, String region, NodeType nodeType,
                int cpuCores, double cpuGhz, double memGib, double bwGbps) {
        this.id       = id;
        this.region   = region;
        this.nodeType = nodeType;
        this.cpuCores = cpuCores;
        this.cpuGhz   = cpuGhz;
        this.memGib   = memGib;
        this.bwGbps   = bwGbps;
        this.currentFreqGhz = cpuGhz;
        this.currentVoltage = 1.0;
    }

    // Derived convenience methods used by TopKSelector
    public double cpuAvailFraction() { return 1.0 - cpuUsedFraction.get(); }
    public double memAvailFraction() { return 1.0 - memUsedFraction.get(); }

    // Getters
    public String   getId()                   { return id; }
    public String   getRegion()               { return region; }
    public NodeType getNodeType()             { return nodeType; }
    public int      getCpuCores()             { return cpuCores; }
    public double   getCpuGhz()              { return cpuGhz; }
    public double   getMemGib()              { return memGib; }
    public double   getBwGbps()              { return bwGbps; }
    public double   getCpuUsedFraction()     { return cpuUsedFraction.get(); }
    public double   getMemUsedFraction()     { return memUsedFraction.get(); }
    public double   getCurrentFreqGhz()      { return currentFreqGhz; }
    public double   getCurrentVoltage()      { return currentVoltage; }
    public double   getHistPerformanceScore(){ return histPerformanceScore; }

    // Setters for mutable fields
    public void setCpuUsedFraction(double v)     { cpuUsedFraction.set(Math.min(1.0, Math.max(0.0, v))); }
    public void setMemUsedFraction(double v)     { memUsedFraction.set(Math.min(1.0, Math.max(0.0, v))); }
    public void setCurrentFreqGhz(double v)      { this.currentFreqGhz = v; }
    public void setCurrentVoltage(double v)      { this.currentVoltage = v; }
    public void setHistPerformanceScore(double v){ this.histPerformanceScore = v; }

    @Override
    public String toString() {
        return String.format("Node{id='%s', region='%s', type=%s, cpu=%.0f%%, mem=%.0f%%}",
                id, region, nodeType,
                cpuUsedFraction.get() * 100, memUsedFraction.get() * 100);
    }
}
