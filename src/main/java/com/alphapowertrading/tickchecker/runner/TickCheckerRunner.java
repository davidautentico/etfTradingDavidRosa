package com.alphapowertrading.tickchecker.runner;

import com.alphapowertrading.tickchecker.checker.BidAsk1mChecker;
import com.alphapowertrading.tickchecker.config.CheckerProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TickCheckerRunner implements CommandLineRunner {

  private final CheckerProperties properties;
  private final BidAsk1mChecker checker;

  public TickCheckerRunner(
      CheckerProperties properties,
      BidAsk1mChecker checker) {
    this.properties = properties;
    this.checker = checker;
  }

  @Override
  public void run(String... args) throws Exception {
    checker.check(properties);
  }
}
