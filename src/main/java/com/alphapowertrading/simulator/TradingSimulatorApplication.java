package com.alphapowertrading.simulator;

import com.alphapowertrading.simulator.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class TradingSimulatorApplication {

  public static void main(String[] args) {
    SpringApplication.run(TradingSimulatorApplication.class, args);
  }
}
