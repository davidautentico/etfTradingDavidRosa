package com.alphapowertrading.synthetic;

import com.alphapowertrading.simulator.core.loader.TradingViewCsvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(TradingViewCsvLoader.class)
public class SyntheticSeriesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyntheticSeriesApplication.class, args);
    }
}