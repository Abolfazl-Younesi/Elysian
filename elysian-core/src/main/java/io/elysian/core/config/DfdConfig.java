package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DfdConfig {
    private boolean enabled             = true;
    private double  cpuHotPathThreshold = 0.65;
    private int     minStateSizeMb      = 100;
    private int     profilingWindowSec  = 30;

    public boolean isEnabled()              { return enabled; }
    public double  getCpuHotPathThreshold() { return cpuHotPathThreshold; }
    public int     getMinStateSizeMb()      { return minStateSizeMb; }
    public int     getProfilingWindowSec()  { return profilingWindowSec; }

    public void setEnabled(boolean v)              { this.enabled = v; }
    public void setCpuHotPathThreshold(double v)   { this.cpuHotPathThreshold = v; }
    public void setMinStateSizeMb(int v)           { this.minStateSizeMb = v; }
    public void setProfilingWindowSec(int v)       { this.profilingWindowSec = v; }
}
