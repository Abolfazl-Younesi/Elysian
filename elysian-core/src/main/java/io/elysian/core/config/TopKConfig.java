package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TopKConfig {
    private int    k              = 3;
    private double weightCpu      = 0.40;
    private double weightMemory   = 0.30;
    private double weightLocality = 0.20;
    private double weightHistPerf = 0.10;

    public int    getK()              { return k; }
    public double getWeightCpu()      { return weightCpu; }
    public double getWeightMemory()   { return weightMemory; }
    public double getWeightLocality() { return weightLocality; }
    public double getWeightHistPerf() { return weightHistPerf; }

    public void setK(int k)                    { this.k = k; }
    public void setWeightCpu(double v)         { this.weightCpu = v; }
    public void setWeightMemory(double v)      { this.weightMemory = v; }
    public void setWeightLocality(double v)    { this.weightLocality = v; }
    public void setWeightHistPerf(double v)    { this.weightHistPerf = v; }
}
