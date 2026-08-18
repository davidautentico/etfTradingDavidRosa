package com.alphapowertrading.statistical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StatisticalAnalysisApplication {

  public static void main(String[] args) {
    SpringApplication.run(StatisticalAnalysisApplication.class, args);
  }
}
