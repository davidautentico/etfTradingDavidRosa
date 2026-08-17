package com.alphapowertrading.tickconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TickConverterApplication {

    public static void main(String[] args) {
        SpringApplication.run(TickConverterApplication.class, args);
    }
}
