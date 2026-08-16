package com.alphapowertrading.walkforward;

import com.alphapowertrading.walkforward.service.WalkForwardMonthlyRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.alphapowertrading")
public class WalkForwardApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalkForwardApplication.class, args);
    }

    @Bean
    CommandLineRunner walkForwardCommandLineRunner(
            WalkForwardMonthlyRunner runner
    ) {
        return args -> runner.run();
    }
}