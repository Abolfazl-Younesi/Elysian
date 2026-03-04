package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterConfig {
    private List<String> regions = new ArrayList<>();

    public List<String> getRegions() { return regions; }
    public void setRegions(List<String> r) { this.regions = r; }
}
