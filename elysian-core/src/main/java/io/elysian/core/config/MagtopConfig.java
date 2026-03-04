package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MagtopConfig {
    private double alpha = 0.35;
    private double beta  = 0.30;
    private double gamma = 0.20;
    private double theta = 0.15;
    private double epsilon = 0.05;
    private int    maxIterations = 50;
    private int    parameterUpdateInterval = 5;
    private double latencySloMs = 50.0;
    private TopKConfig topK = new TopKConfig();

    public double getAlpha()   { return alpha; }
    public double getBeta()    { return beta; }
    public double getGamma()   { return gamma; }
    public double getTheta()   { return theta; }
    public double getEpsilon() { return epsilon; }
    public int    getMaxIterations()            { return maxIterations; }
    public int    getParameterUpdateInterval()  { return parameterUpdateInterval; }
    public double getLatencySloMs()             { return latencySloMs; }
    public TopKConfig getTopK()                 { return topK; }

    public void setAlpha(double v)   { this.alpha = v; }
    public void setBeta(double v)    { this.beta = v; }
    public void setGamma(double v)   { this.gamma = v; }
    public void setTheta(double v)   { this.theta = v; }
    public void setEpsilon(double v) { this.epsilon = v; }
    public void setMaxIterations(int v)           { this.maxIterations = v; }
    public void setParameterUpdateInterval(int v) { this.parameterUpdateInterval = v; }
    public void setLatencySloMs(double v)         { this.latencySloMs = v; }
    public void setTopK(TopKConfig v)             { this.topK = v; }
}
