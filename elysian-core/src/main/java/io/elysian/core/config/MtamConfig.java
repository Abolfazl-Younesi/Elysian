package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MtamConfig {
    private double hotThresholdAccessPerSec  = 100.0;
    private double warmThresholdAccessPerSec =  10.0;
    private double migrationThreshold = 0.05;
    private LstmConfig lstm = new LstmConfig();

    public double getHotThresholdAccessPerSec()  { return hotThresholdAccessPerSec; }
    public double getWarmThresholdAccessPerSec()  { return warmThresholdAccessPerSec; }
    public double getMigrationThreshold()         { return migrationThreshold; }
    public LstmConfig getLstm()                   { return lstm; }

    public void setHotThresholdAccessPerSec(double v)  { this.hotThresholdAccessPerSec = v; }
    public void setWarmThresholdAccessPerSec(double v) { this.warmThresholdAccessPerSec = v; }
    public void setMigrationThreshold(double v)        { this.migrationThreshold = v; }
    public void setLstm(LstmConfig v)                  { this.lstm = v; }
}
