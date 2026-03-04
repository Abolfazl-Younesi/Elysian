package io.elysian.mtam;

import io.elysian.core.config.MtamConfig;
import io.elysian.core.model.StateTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the {@code TierAssignment} function from Algorithm (tier-assignment).
 *
 * <p>For each state key, maintains a sliding access-history window and calls
 * {@link LstmPredictor} to decide proactively which tier (HOT/WARM/COLD) the key
 * should occupy. Migration is triggered only when benefit > transition cost.
 */
public class StateTierAssigner {

    private static final Logger LOG = LoggerFactory.getLogger(StateTierAssigner.class);

    private final MtamConfig     cfg;
    private final LstmPredictor  lstm;

    // Per-key sliding window of access intervals (ms)
    private final Map<String, Deque<Double>> accessWindows = new ConcurrentHashMap<>();
    // Per-key long-running historical average (accesses/s)
    private final Map<String, Double> historicalAvg        = new ConcurrentHashMap<>();
    // Current tier per key
    private final Map<String, StateTier> tierMap           = new ConcurrentHashMap<>();

    public StateTierAssigner(MtamConfig cfg, LstmPredictor lstm) {
        this.cfg  = cfg;
        this.lstm = lstm;
    }

    /**
     * Records an access event for a state key and returns the assigned tier.
     *
     * @param stateKey   identifier of the state object
     * @param intervalMs time since last access for this key (ms)
     * @param accessRatePerSec current measured access rate
     * @return recommended {@link StateTier}
     */
    public StateTier assignTier(String stateKey, double intervalMs, double accessRatePerSec) {
        // Update sliding window
        Deque<Double> window = accessWindows.computeIfAbsent(
                stateKey, k -> new ArrayDeque<>(cfg.getLstm().getWindowSize()));
        if (window.size() >= cfg.getLstm().getWindowSize()) window.pollFirst();
        window.addLast(intervalMs);

        // Update historical average (EWMA)
        double histAvg = historicalAvg.merge(stateKey, accessRatePerSec,
                (old, cur) -> 0.9 * old + 0.1 * cur);

        // LSTM prediction
        LstmPredictor.Prediction pred = lstm.predict(window, accessRatePerSec, histAvg);
        double predictedRate = pred.predictedRate();

        StateTier targetTier = classify(Math.max(accessRatePerSec, predictedRate));
        StateTier currentTier = tierMap.getOrDefault(stateKey, StateTier.COLD);

        if (targetTier != currentTier) {
            double benefit = estimateBenefit(currentTier, targetTier, accessRatePerSec);
            double cost    = estimateTransitionCost(currentTier, targetTier);
            if (benefit - cost > cfg.getMigrationThreshold()) {
                LOG.info("[MTAM] key={} tier {} → {} (benefit={:.3f} cost={:.3f})",
                        stateKey, currentTier, targetTier, benefit, cost);
                tierMap.put(stateKey, targetTier);
            } else {
                targetTier = currentTier;  // defer
            }
        }

        return targetTier;
    }

    public StateTier currentTier(String stateKey) {
        return tierMap.getOrDefault(stateKey, StateTier.COLD);
    }

    // -----------------------------------------------------------------------

    private StateTier classify(double rate) {
        if (rate >= cfg.getHotThresholdAccessPerSec())  return StateTier.HOT;
        if (rate >= cfg.getWarmThresholdAccessPerSec()) return StateTier.WARM;
        return StateTier.COLD;
    }

    /** Simplified benefit: difference in access-latency saved by the better tier. */
    private double estimateBenefit(StateTier from, StateTier to, double rate) {
        double fromLatMs = tierLatencyMs(from);
        double toLatMs   = tierLatencyMs(to);
        return rate * (fromLatMs - toLatMs) / 1000.0;  // normalised
    }

    private double tierLatencyMs(StateTier tier) {
        return switch (tier) {
            case HOT  -> 0.5;
            case WARM -> 5.0;
            case COLD -> 50.0;
        };
    }

    /** Simplified transition cost (encoding + transfer size proxy). */
    private double estimateTransitionCost(StateTier from, StateTier to) {
        // HOT↔WARM cheaper than HOT↔COLD
        if (from == StateTier.HOT  && to == StateTier.COLD) return 0.30;
        if (from == StateTier.COLD && to == StateTier.HOT)  return 0.25;
        return 0.05;
    }
}
