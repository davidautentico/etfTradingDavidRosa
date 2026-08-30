package com.alphapowertrading.correlation;

import com.alphapowertrading.correlation.config.CorrelationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CorrelationProperties.class)
public class CorrelationApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(CorrelationApplication.class);
    app.setAdditionalProfiles("correlation");
    app.run(args);
  }
}
