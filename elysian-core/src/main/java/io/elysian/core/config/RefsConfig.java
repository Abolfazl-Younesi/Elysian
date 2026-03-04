package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RefsConfig {
    private boolean enabled           = true;
    private double  targetUtilization = 0.70;
    private double  gamma             = 0.10;
    private double  beta              = 0.50;
    private double  delta             = 0.02;
    private long    monitoringIntervalMs = 1000L;
    private double  fMinGhz  = 0.80;
    private double  fMaxGhz  = 3.50;
    private double  vMinVolts = 0.90;
    private double  vMaxVolts = 1.20;

    public boolean isEnabled()              { return enabled; }
    public double  getTargetUtilization()   { return targetUtilization; }
    public double  getGamma()               { return gamma; }
    public double  getBeta()                { return beta; }
    public double  getDelta()               { return delta; }
    public long    getMonitoringIntervalMs(){ return monitoringIntervalMs; }
    public double  getFMinGhz()             { return fMinGhz; }
    public double  getFMaxGhz()             { return fMaxGhz; }
    public double  getVMinVolts()           { return vMinVolts; }
    public double  getVMaxVolts()           { return vMaxVolts; }

    public void setEnabled(boolean v)              { this.enabled = v; }
    public void setTargetUtilization(double v)     { this.targetUtilization = v; }
    public void setGamma(double v)                 { this.gamma = v; }
    public void setBeta(double v)                  { this.beta = v; }
    public void setDelta(double v)                 { this.delta = v; }
    public void setMonitoringIntervalMs(long v)    { this.monitoringIntervalMs = v; }
    public void setFMinGhz(double v)               { this.fMinGhz = v; }
    public void setFMaxGhz(double v)               { this.fMaxGhz = v; }
    public void setVMinVolts(double v)             { this.vMinVolts = v; }
    public void setVMaxVolts(double v)             { this.vMaxVolts = v; }
}
