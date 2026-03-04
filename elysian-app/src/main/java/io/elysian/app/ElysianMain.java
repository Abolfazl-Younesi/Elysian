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
}

