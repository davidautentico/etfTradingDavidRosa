package com.alphapowertrading.tickchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TickCheckerApplication {

  public static void main(String[] args) {
    SpringApplication.run(TickCheckerApplication.class, args);
  }
}
