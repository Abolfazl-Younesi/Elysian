package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoordinationConfig {
    private int    maxRetries          = 5;
    private long   initialBackoffMs    = 100L;
    private double backoffMultiplier   = 2.0;
    private int    lockTimeoutSec      = 5;
    private String zookeeperConnect    = "localhost:2181";
    private String lockBasePath        = "/elysian/locks";

    public int    getMaxRetries()        { return maxRetries; }
    public long   getInitialBackoffMs()  { return initialBackoffMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public int    getLockTimeoutSec()    { return lockTimeoutSec; }
    public String getZookeeperConnect()  { return zookeeperConnect; }
    public String getLockBasePath()      { return lockBasePath; }

    public void setMaxRetries(int v)         { this.maxRetries = v; }
    public void setInitialBackoffMs(long v)  { this.initialBackoffMs = v; }
    public void setBackoffMultiplier(double v){ this.backoffMultiplier = v; }
    public void setLockTimeoutSec(int v)     { this.lockTimeoutSec = v; }
    public void setZookeeperConnect(String v){ this.zookeeperConnect = v; }
    public void setLockBasePath(String v)    { this.lockBasePath = v; }
}
