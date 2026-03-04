package io.elysian.refs;

import io.elysian.core.config.RefsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class FrequencyVoltageUpdaterTest {

    private final FrequencyVoltageUpdater fvu = new FrequencyVoltageUpdater();

    private RefsConfig defaultCfg() {
        RefsConfig cfg = new RefsConfig();
        cfg.setTargetUtilization(0.70);
        cfg.setGamma(0.10);
        cfg.setDelta(0.02);
        cfg.setFMinGhz(0.80);
        cfg.setFMaxGhz(3.50);
        cfg.setVMinVolts(0.90);
        cfg.setVMaxVolts(1.20);
        return cfg;
    }

    @Test
    void frequencyClampedToMin() {
        // Very low utilisation → frequency should drop but not below f_min
        double[] result = fvu.update(1.0, 1.0, 0.01, 1.0, defaultCfg());
        assertTrue(result[0] >= 0.80, "f must be >= f_min");
    }

    @Test
    void frequencyClampedToMax() {
        // Very high utilisation → frequency should rise but not above f_max
        double[] result = fvu.update(3.0, 1.1, 0.99, 1.0, defaultCfg());
        assertTrue(result[0] <= 3.50, "f must be <= f_max");
    }

    @Test
    void hysteresisSupressesSmallChanges() {
        // Utilisation exactly at target → no change
        double ft = 2.0;
        RefsConfig cfg = defaultCfg();
        cfg.setGamma(0.001);  // tiny gain → Δf very small
        double[] result = fvu.update(ft, 1.0, cfg.getTargetUtilization(), 1.0, cfg);
        assertEquals(ft, result[0], 1e-6, "Hysteresis should suppress near-zero Δf");
    }

    @Test
    void voltageWithinBounds() {
        RefsConfig cfg = defaultCfg();
        for (double u : new double[]{0.1, 0.5, 0.7, 0.9}) {
            double[] r = fvu.update(2.0, 1.05, u, 1.0, cfg);
            assertTrue(r[1] >= cfg.getVMinVolts(), "V >= V_min");
            assertTrue(r[1] <= cfg.getVMaxVolts(), "V <= V_max");
        }
    }

    @Test
    void loadFactorCalculatorFallback() {
        LoadFactorCalculator lfc = new LoadFactorCalculator();
        // All idle → Li = 1
        double[] utils = {0.0, 0.0, 0.0};
        assertEquals(1.0, lfc.compute(0, utils), 1e-9, "Fallback to 1 when all idle");
    }
}
