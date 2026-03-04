package io.elysian.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a streaming operator (or micro-operator after DFD splits it).
 * Immutable identity fields; mutable runtime fields for MAGTOP best-response.
 */
public class Operator {

    private final String  id;
    private final boolean stateful;

    // State size in MB (relevant for migration cost)
    private volatile double stateSizeMb;

    // DAG wiring
    private final List<String> upstreamIds   = new ArrayList<>();
    private final List<String> downstreamIds = new ArrayList<>();

    // Current placement — updated after each successful migration
    private volatile String currentNodeId;
    private volatile int    currentParallelism;

    // Parallelism options available per MAGTOP search
    private final List<Integer> parallelismOptions;

    public Operator(String id, boolean stateful, double stateSizeMb,
                    String currentNodeId, int currentParallelism,
                    List<Integer> parallelismOptions) {
        this.id                 = id;
        this.stateful           = stateful;
        this.stateSizeMb        = stateSizeMb;
        this.currentNodeId      = currentNodeId;
        this.currentParallelism = currentParallelism;
        this.parallelismOptions = new ArrayList<>(parallelismOptions);
    }

    // DAG wiring helpers
    public void addUpstream(String id)   { upstreamIds.add(id); }
    public void addDownstream(String id) { downstreamIds.add(id); }

    // Getters
    public String          getId()                 { return id; }
    public boolean         isStateful()            { return stateful; }
    public double          getStateSizeMb()        { return stateSizeMb; }
    public List<String>    getUpstreamIds()        { return List.copyOf(upstreamIds); }
    public List<String>    getDownstreamIds()      { return List.copyOf(downstreamIds); }
    public String          getCurrentNodeId()      { return currentNodeId; }
    public int             getCurrentParallelism() { return currentParallelism; }
    public List<Integer>   getParallelismOptions() { return List.copyOf(parallelismOptions); }

    // Setters for mutable runtime state
    public void setStateSizeMb(double v)        { this.stateSizeMb = v; }
    public void setCurrentNodeId(String v)      { this.currentNodeId = v; }
    public void setCurrentParallelism(int v)    { this.currentParallelism = v; }

    @Override
    public String toString() {
        return String.format("Operator{id='%s', stateful=%b, node='%s', p=%d}",
                id, stateful, currentNodeId, currentParallelism);
    }
}
