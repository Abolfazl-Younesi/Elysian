package io.elysian.refs;

import io.elysian.core.config.RefsConfig;
import io.elysian.core.model.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * ReFS closed-loop controller — Algorithm (ReFS) from the supplementary.
 *
 * <p>Every monitoring interval ΔT:
 * <ol>
 *   <li>CharacterizeWorkload → (I_cpu, I_mem, I_io) per node</li>
 *   <li>MeasureCPUUtilization → u_i per node</li>
 *   <li>ComputeLoadFactor → L_i = (u_i / Σ u_j) × m</li>
 *   <li>UFV → new f_{t+1}, V_{t+1}</li>
 *   <li>Apply and sleep ΔT</li>
 * </ol>
 */
public class RefsController implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(RefsController.class);

    private final RefsConfig              cfg;
    private final WorkloadCharacteriser   wc;
    private final LoadFactorCalculator    lfc;
    private final FrequencyVoltageUpdater fvu;

    private volatile boolean running = true;

    public RefsController(RefsConfig cfg,
                          WorkloadCharacteriser wc,
                          LoadFactorCalculator lfc,
                          FrequencyVoltageUpdater fvu) {
        this.cfg = cfg;
        this.wc  = wc;
        this.lfc = lfc;
        this.fvu = fvu;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        LOG.info("[ReFS] Controller started; target utilisation={}", cfg.getTargetUtilization());
        while (running) {
            try {
                Collection<Node> nodes = wc.getNodes();
                double[] utils = new double[nodes.size()];

                // Phase 1: characterise + measure
                int idx = 0;
                for (Node node : nodes) {
                    WorkloadCharacteriser.Intensities intensities = wc.characterize(node);
                    double psi = cfg.getBeta() * (intensities.memIntensity() / Math.max(1e-9, intensities.cpuIntensity()))
                               + (1 - cfg.getBeta()) * (intensities.ioIntensity() / Math.max(1e-9, intensities.cpuIntensity()));
                    double u = wc.measureCpuUtilization(node);
                    node.setCpuUsedFraction(u);
                    utils[idx++] = u;
                }

                // Phase 2: load factor + frequency/voltage update
                int i2 = 0;
                for (Node node : nodes) {
                    double l = lfc.compute(i2++, utils);
                    double[] fv = fvu.update(node.getCurrentFreqGhz(), node.getCurrentVoltage(),
                            node.getCpuUsedFraction(), l, cfg);
                    node.setCurrentFreqGhz(fv[0]);
                    node.setCurrentVoltage(fv[1]);
                    LOG.debug("[ReFS] node={} f={:.2f}GHz V={:.3f}",
                            node.getId(), fv[0], fv[1]);
                }

                Thread.sleep(cfg.getMonitoringIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("[ReFS] Cycle error", e);
            }
        }
        LOG.info("[ReFS] Controller stopped");
    }
}
