package com.alphapowertrading.walkforward.config;

import com.alphapowertrading.simulator.core.loader.CsvLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WalkForwardProperties.class)
public class WalkForwardConfig {

  /*
   * CsvLoader belongs to the simulator module. The walk-forward application
   * scans the simulator package, so this configuration intentionally does
   * not duplicate or replace the simulator bean.
   */
  private final CsvLoader csvLoader;

  public WalkForwardConfig(CsvLoader csvLoader) {
    this.csvLoader = csvLoader;
  }
}
