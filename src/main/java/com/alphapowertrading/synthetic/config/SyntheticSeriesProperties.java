package com.alphapowertrading.synthetic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "synthetic")
public class SyntheticSeriesProperties {

    private String baseSymbol;

    private double leverage;

    private String dataDirectory;

    public String getBaseSymbol() {
        return baseSymbol;
    }

    public void setBaseSymbol(String baseSymbol) {
        this.baseSymbol = baseSymbol;
    }

    public double getLeverage() {
        return leverage;
    }

    public void setLeverage(double leverage) {
        this.leverage = leverage;
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }
}