package com.alphapowertrading.portfolio;

import com.alphapowertrading.portfolio.config.PortfolioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.alphapowertrading.portfolio")
@EnableConfigurationProperties(PortfolioProperties.class)
public class PortfolioApplication {

  public static void main(String[] args) {
    SpringApplication.run(PortfolioApplication.class, args);
  }
}
