package com.alphapowertrading.simulator.core.loader;

import com.alphapowertrading.simulator.core.market.MarketData;

import java.io.IOException;
import java.nio.file.Path;

public interface MarketDataCsvLoader {
    boolean supports(String header);
    MarketData load(Path file) throws IOException;
}
