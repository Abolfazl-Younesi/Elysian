package io.elysian.refs;

import io.elysian.core.config.RefsConfig;

/**
 * UFV — Update Frequency and Voltage function from ReFS (Algorithm Alg:ReFS, lines 21–31).
 *
 * <p>f_new = f_t × (1 + γ × log(u / u_target) × (2 - L))
 * Hysteresis: suppress Δf if |f_new - f_t| < δ × f_max
 * Voltage:    V = V_min + (V_max - V_min) × ((f - f_min)/(f_max - f_min))^0.5
 */
public class FrequencyVoltageUpdater {

    /**
     * @param ft       current frequency (GHz)
     * @param vt       current voltage (V)
     * @param u        current CPU utilisation [0,1]
     * @param l        load factor L_i
     * @param cfg      ReFS config (γ, δ, f_min, f_max, V_min, V_max, u_target)
     * @return double[] {f_{t+1}, V_{t+1}}
     */
    public double[] update(double ft, double vt, double u, double l, RefsConfig cfg) {
        double uTarget = cfg.getTargetUtilization();
        double gamma   = cfg.getGamma();
        double delta   = cfg.getDelta();
        double fMin    = cfg.getFMinGhz();
        double fMax    = cfg.getFMaxGhz();
        double vMin    = cfg.getVMinVolts();
        double vMax    = cfg.getVMaxVolts();

        // Guard against log(0)
        double safeU = Math.max(1e-6, u);
        double fNew  = ft * (1.0 + gamma * Math.log(safeU / uTarget) * (2.0 - l));

        // Hysteresis gate
        double deltaFMin = delta * fMax;
        double fNext;
        if (Math.abs(fNew - ft) < deltaFMin) {
            fNext = ft;   // suppress small fluctuations
        } else {
            fNext = Math.max(fMin, Math.min(fMax, fNew));
        }

        // Square-root voltage mapping
        double fRange = fMax - fMin;
        double vNext = (fRange == 0) ? vMin
                : vMin + (vMax - vMin) * Math.sqrt((fNext - fMin) / fRange);
        vNext = Math.max(vMin, Math.min(vMax, vNext));

        return new double[]{fNext, vNext};
    }
}
