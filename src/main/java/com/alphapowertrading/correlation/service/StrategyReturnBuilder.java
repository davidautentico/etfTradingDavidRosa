package com.alphapowertrading.correlation.service;

import com.alphapowertrading.correlation.model.StrategyReturns;
import com.alphapowertrading.correlation.model.TradeRecord;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StrategyReturnBuilder {

  public StrategyReturns build(String strategyName, List<TradeRecord> trades) {
    Map<LocalDate, Double> dailyReturns = new LinkedHashMap<>();

    trades.stream()
        .sorted(Comparator.comparing(TradeRecord::exitDate))
        .forEach(
            trade -> {
              LocalDate date = trade.exitDate().toLocalDate();
              double existingReturn = dailyReturns.getOrDefault(date, 0.0);
              double combinedReturn =
                  (1.0 + existingReturn) * (1.0 + trade.profitPercentage()) - 1.0;
              dailyReturns.put(date, combinedReturn);
            });

    return new StrategyReturns(strategyName, Map.copyOf(dailyReturns), trades.size());
  }
}
