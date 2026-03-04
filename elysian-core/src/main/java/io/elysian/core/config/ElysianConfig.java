package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Root POJO mapping the top-level sections of {@code elysian.yaml}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElysianConfig {

    private ClusterConfig cluster = new ClusterConfig();
    private MagtopConfig  magtop  = new MagtopConfig();
    private DfdConfig     dfd     = new DfdConfig();
    private MtamConfig    mtam    = new MtamConfig();
    private RefsConfig    refs    = new RefsConfig();
    private CoordinationConfig coordination = new CoordinationConfig();
    private FlinkConfig   flink   = new FlinkConfig();

    public ClusterConfig     getCluster()       { return cluster; }
    public MagtopConfig      getMagtop()        { return magtop; }
    public DfdConfig         getDfd()           { return dfd; }
    public MtamConfig        getMtam()          { return mtam; }
    public RefsConfig        getRefs()          { return refs; }
    public CoordinationConfig getCoordination() { return coordination; }
    public FlinkConfig       getFlink()         { return flink; }

    public void setCluster(ClusterConfig c)           { this.cluster = c; }
    public void setMagtop(MagtopConfig m)             { this.magtop = m; }
    public void setDfd(DfdConfig d)                   { this.dfd = d; }
    public void setMtam(MtamConfig m)                 { this.mtam = m; }
    public void setRefs(RefsConfig r)                 { this.refs = r; }
    public void setCoordination(CoordinationConfig c) { this.coordination = c; }
    public void setFlink(FlinkConfig f)               { this.flink = f; }
}
