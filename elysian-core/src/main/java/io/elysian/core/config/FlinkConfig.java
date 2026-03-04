package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FlinkConfig {
    private String jobManagerUrl            = "http://localhost:8081";
    private long   metricsPollingIntervalMs = 2000L;
    private long   rescalingApiTimeoutMs    = 10000L;

    public String getJobManagerUrl()              { return jobManagerUrl; }
    public long   getMetricsPollingIntervalMs()   { return metricsPollingIntervalMs; }
    public long   getRescalingApiTimeoutMs()       { return rescalingApiTimeoutMs; }

    public void setJobManagerUrl(String v)             { this.jobManagerUrl = v; }
    public void setMetricsPollingIntervalMs(long v)    { this.metricsPollingIntervalMs = v; }
    public void setRescalingApiTimeoutMs(long v)       { this.rescalingApiTimeoutMs = v; }
}
