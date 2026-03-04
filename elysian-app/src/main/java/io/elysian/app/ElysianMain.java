package io.elysian.app;

import io.elysian.core.config.ConfigLoader;
import io.elysian.core.config.ElysianConfig;
import io.elysian.core.model.*;
import io.elysian.coordination.*;
import io.elysian.dfd.DfdManager;
import io.elysian.magtop.*;
import io.elysian.mtam.*;
import io.elysian.refs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ELySian Application Entry Point.
 *
 * <p>Wires all modules together:
 * <ol>
 *   <li>Load config from {@code elysian.yaml}</li>
 *   <li>Build the cluster model (nodes, topology)</li>
 *   <li>Start ReFS controller in a background thread</li>
 *   <li>Run the MAGTOP placement loop</li>
 *   <li>Periodically re-run MAGTOP as workload changes</li>
 * </ol>
 *
 * <p>Override config path: {@code -Delysian.config=/path/to/elysian.yaml}
 */
public class ElysianMain {

    private static final Logger LOG = LoggerFactory.getLogger(ElysianMain.class);

    public static void main(String[] args) throws Exception {
        LOG.info("=============================================================");
        LOG.info("  ELySian — Joint Operator Placement & Auto-Scaling System");
        LOG.info("=============================================================");

        // ── 1. Config ──────────────────────────────────────────────────────
        ElysianConfig cfg = ConfigLoader.get();
        LOG.info("Config loaded (topK={}, maxIter={}, refs={})",
                cfg.getMagtop().getTopK().getK(),
                cfg.getMagtop().getMaxIterations(),
                cfg.getRefs().isEnabled());

        // ── 2. Cluster model ───────────────────────────────────────────────
        List<Node> nodes = buildExampleCluster();

        // ── 3. Pipeline operators ──────────────────────────────────────────
        List<Operator> operators = buildExamplePipeline(nodes.get(0).getId());

        // ── 4. Shared tables ───────────────────────────────────────────────
        Map<String, Map<String, Double>> latencyTable = buildLatencyTable(nodes);
        Map<String, Map<String, Double>> bwTable      = buildBwTable(nodes);
        Map<String, Map<String, Double>> flowTable    = buildFlowTable(operators);
        Map<String, Double>              e2eLatency   = new HashMap<>();
        nodes.forEach(n -> e2eLatency.put(n.getId(), 30.0));

        // ── 5. Wire ReFS ───────────────────────────────────────────────────
        WorkloadCharacteriser   wc  = new WorkloadCharacteriser();
        nodes.forEach(wc::registerNode);
        LoadFactorCalculator    lfc = new LoadFactorCalculator();
        FrequencyVoltageUpdater fvu = new FrequencyVoltageUpdater();
        RefsController refs = new RefsController(cfg.getRefs(), wc, lfc, fvu);

        Thread refsThread = Thread.ofVirtual().name("refs-controller").start(refs);

        // ── 6. Wire Coordination ───────────────────────────────────────────
        DistributedLockManager   lockMgr     = new DistributedLockManager();
        MigrationCheckpointStore checkpoints = new MigrationCheckpointStore();
        CoordinatedMigrationExecutor coordinator =
                new CoordinatedMigrationExecutor(cfg.getCoordination(), lockMgr, checkpoints);

        // ── 7. Wire MAGTOP ─────────────────────────────────────────────────
        TopKSelector topK = new TopKSelector(cfg.getMagtop().getTopK(), latencyTable);
        UtilityCalculator util = new UtilityCalculator(
                cfg.getMagtop(), e2eLatency, bwTable, flowTable, latencyTable);
        InitialPlacementStrategy init = new InitialPlacementStrategy(latencyTable);

        // MigrationDelegate bridges MAGTOP → Coordination
        MagtopPlanner.MigrationDelegate delegate =
                (op, targetNode, targetP, placement) -> {
                    CoordinatedMigrationExecutor.Result r =
                            coordinator.execute(op, targetNode, targetP, placement);
                    return r == CoordinatedMigrationExecutor.Result.SUCCESS;
                };

        MagtopPlanner magtop = new MagtopPlanner(
                cfg.getMagtop(), topK, util, init, delegate);

        // ── 8. Wire MTAM ───────────────────────────────────────────────────
        LstmPredictor    lstm   = new LstmPredictor(cfg.getMtam().getLstm());
        StateTierAssigner mtam  = new StateTierAssigner(cfg.getMtam(), lstm);

        // ── 9. Wire DFD ────────────────────────────────────────────────────
        DfdManager dfd = new DfdManager(cfg.getDfd());

        // ── 10. Periodic MAGTOP loop ────────────────────────────────────────
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        Placement[] latestPlacement = {null};

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // DFD pass — decompose hot operators
                List<Operator> all = new ArrayList<>(operators);
                List<Operator> expanded = new ArrayList<>();
                for (Operator op : all) {
                    dfd.updateProfile(op.getId(),
                            op.getCpuUsedFractionSimulated(), op.getStateSizeMb());
                    expanded.addAll(dfd.evaluateDecomposition(op));
                }

                // MAGTOP optimisation
                Placement p = magtop.optimise(expanded, nodes);
                latestPlacement[0] = p;
                LOG.info("[APP] New placement: {}", p);
            } catch (Exception e) {
                LOG.error("[APP] MAGTOP error", e);
            }
        }, 0, 30, TimeUnit.SECONDS);

        // ── 11. Shutdown hook ───────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("[APP] Shutting down ELySian…");
            refs.stop();
            scheduler.shutdownNow();
        }));

        LOG.info("[APP] ELySian running. MAGTOP fires every 30 s. Ctrl-C to stop.");
        scheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    // -----------------------------------------------------------------------
    // Example cluster / pipeline builders (replace with Flink metrics reader)
    // -----------------------------------------------------------------------

    static List<Node> buildExampleCluster() {
        return List.of(
            new Node("n-lyon-01",    "Lyon",    NodeType.FAR_EDGE,   1,  2.4,   2,   1),
            new Node("n-sophia-01",  "Sophia",  NodeType.EDGE_FOG,   6,  2.93,  96,  1),
            new Node("n-nantes-01",  "Nantes",  NodeType.CLOUD,      10, 1.80,  128, 10),
            new Node("n-toulouse-01","Toulouse", NodeType.CLOUD,     16, 2.40,  256, 10)
        );
    }

    static List<Operator> buildExamplePipeline(String defaultNode) {
        List<Integer> pOptions = List.of(1, 2, 4);
        Operator source  = new Operator("source",  false, 0,    defaultNode, 1, pOptions);
        Operator filter  = new Operator("filter",  false, 0,    defaultNode, 1, pOptions);
        Operator join    = new Operator("join",    true,  500,  defaultNode, 1, pOptions);
        Operator agg     = new Operator("agg",     true,  200,  defaultNode, 1, pOptions);
        Operator sink    = new Operator("sink",    false, 0,    defaultNode, 1, pOptions);

        source.addDownstream("filter");
        filter.addUpstream("source"); filter.addDownstream("join");
        join.addUpstream("filter");   join.addDownstream("agg");
        agg.addUpstream("join");      agg.addDownstream("sink");
        sink.addUpstream("agg");

        return List.of(source, filter, join, agg, sink);
    }

    static Map<String, Map<String, Double>> buildLatencyTable(List<Node> nodes) {
        // Approximate inter-region latencies in ms (from paper Table 1)
        double[][] latMs = {
            //  lyon  sophia nantes toulouse
            {0,    16,    25,    20},  // lyon
            {16,    0,    31,    26},  // sophia
            {25,   31,     0,    15},  // nantes
            {20,   26,    15,     0}   // toulouse
        };
        Map<String, Map<String, Double>> table = new HashMap<>();
        List<String> ids = nodes.stream().map(Node::getId).toList();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Double> row = new HashMap<>();
            for (int j = 0; j < ids.size(); j++) row.put(ids.get(j), latMs[i][j]);
            table.put(ids.get(i), row);
        }
        return table;
    }

    static Map<String, Map<String, Double>> buildBwTable(List<Node> nodes) {
        Map<String, Map<String, Double>> bw = new HashMap<>();
        for (Node a : nodes) {
            Map<String, Double> row = new HashMap<>();
            for (Node b : nodes) row.put(b.getId(), a.getBwGbps());
            bw.put(a.getId(), row);
        }
        return bw;
    }

    static Map<String, Map<String, Double>> buildFlowTable(List<Operator> ops) {
        Map<String, Map<String, Double>> flow = new HashMap<>();
        for (Operator op : ops) {
            Map<String, Double> row = new HashMap<>();
            for (String down : op.getDownstreamIds()) row.put(down, 100.0); // 100 Mbps
            flow.put(op.getId(), row);
        }
        return flow;
    }
}
