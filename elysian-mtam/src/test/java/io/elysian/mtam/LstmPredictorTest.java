package io.elysian.mtam;

import io.elysian.core.config.LstmConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

class LstmPredictorTest {

    private LstmConfig defaultCfg() {
        LstmConfig c = new LstmConfig();
        c.setHiddenUnits(32);
        c.setLayers(2);
        c.setWindowSize(100);
        c.setConfidenceThreshold(0.5);
        c.setEwmaAlpha(0.7);
        return c;
    }

    @Test
    void ewmaFallbackWhenWindowTooSmall() {
        LstmPredictor pred = new LstmPredictor(defaultCfg());
        Deque<Double> tiny = new ArrayDeque<>();
        tiny.add(100.0); tiny.add(110.0);  // only 2 events

        LstmPredictor.Prediction p = pred.predict(tiny, 50.0, 40.0);
        // Confidence must be 0 (fallback case)
        assertEquals(0.0, p.confidence(), "Tiny window must trigger fallback");
        // EWMA: 0.7*50 + 0.3*40 = 35+12 = 47
        assertEquals(47.0, p.predictedRate(), 0.01, "EWMA fallback value");
    }

    @Test
    void predictedRateNonNegative() {
        LstmPredictor pred = new LstmPredictor(defaultCfg());
        Deque<Double> window = new ArrayDeque<>();
        for (int i = 0; i < 20; i++) window.add((double) (i * 10));

        LstmPredictor.Prediction p = pred.predict(window, 100.0, 80.0);
        assertTrue(p.predictedRate() >= 0.0, "Predicted rate must be non-negative");
    }

    @Test
    void extractFeaturesCorrectMean() {
        Deque<Double> window = new ArrayDeque<>();
        for (int i = 0; i < 10; i++) window.add(100.0);  // constant 100ms intervals

        double[] features = LstmPredictor.extractFeatures(window);
        assertEquals(100.0, features[0], 0.01, "Mean interval should be 100");
        assertEquals(0.0,   features[1], 0.01, "Variance should be 0 for constant series");
        assertEquals(0.0,   features[2], 0.01, "Trend should be 0 for constant series");
    }

    @Test
    void stateTierAssignerHotClassification() {
        LstmPredictor pred = new LstmPredictor(defaultCfg());
        io.elysian.core.config.MtamConfig mtamCfg = new io.elysian.core.config.MtamConfig();
        StateTierAssigner assigner = new StateTierAssigner(mtamCfg, pred);

        // Feed many accesses at high rate → should classify as HOT
        for (int i = 0; i < 50; i++) {
            assigner.assignTier("key1", 5.0 /*ms*/, 200.0 /*accesses/s*/);
        }
        io.elysian.core.model.StateTier tier = assigner.currentTier("key1");
        assertEquals(io.elysian.core.model.StateTier.HOT, tier,
                "High-rate key should be classified HOT");
    }
}
