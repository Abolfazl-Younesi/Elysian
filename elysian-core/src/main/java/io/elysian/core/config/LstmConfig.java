package io.elysian.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LstmConfig {
    private int    hiddenUnits          = 32;
    private int    layers               = 2;
    private int    windowSize           = 100;
    private double learningRate         = 0.001;
    private int    epochs               = 100;
    private int    batchSize            = 64;
    private double confidenceThreshold  = 0.5;
    private double ewmaAlpha            = 0.7;

    public int    getHiddenUnits()         { return hiddenUnits; }
    public int    getLayers()              { return layers; }
    public int    getWindowSize()          { return windowSize; }
    public double getLearningRate()        { return learningRate; }
    public int    getEpochs()             { return epochs; }
    public int    getBatchSize()           { return batchSize; }
    public double getConfidenceThreshold() { return confidenceThreshold; }
    public double getEwmaAlpha()           { return ewmaAlpha; }

    public void setHiddenUnits(int v)          { this.hiddenUnits = v; }
    public void setLayers(int v)               { this.layers = v; }
    public void setWindowSize(int v)           { this.windowSize = v; }
    public void setLearningRate(double v)      { this.learningRate = v; }
    public void setEpochs(int v)              { this.epochs = v; }
    public void setBatchSize(int v)            { this.batchSize = v; }
    public void setConfidenceThreshold(double v){ this.confidenceThreshold = v; }
    public void setEwmaAlpha(double v)         { this.ewmaAlpha = v; }
}
