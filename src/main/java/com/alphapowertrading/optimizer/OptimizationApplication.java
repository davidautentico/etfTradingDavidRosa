package com.alphapowertrading.optimizer;

import com.alphapowertrading.optimizer.config.OptimizationProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@SpringBootApplication(scanBasePackages = "com.alphapowertrading.optimizer")
@EnableConfigurationProperties(OptimizationProperties.class)
public class OptimizationApplication implements CommandLineRunner {

  private final OptimizationRunner runner;

  public OptimizationApplication(OptimizationRunner runner) {
    this.runner = runner;
  }

  public static void main(String[] args) {
    SpringApplication.run(OptimizationApplication.class, args);
  }

  @Bean
  CommandLineRunner debug(Environment environment) {
    return args -> {
      System.out.println("================================");
      System.out.println("optimizer.symbol = " + environment.getProperty("optimizer.symbol"));
      System.out.println(
          "optimizer.data-directory = " + environment.getProperty("optimizer.data-directory"));
      System.out.println(
          "optimizer.output-file = " + environment.getProperty("optimizer.output-file"));
      System.out.println("================================");
    };
  }

  @Override
  public void run(String... args) {
    runner.run();
  }
}
