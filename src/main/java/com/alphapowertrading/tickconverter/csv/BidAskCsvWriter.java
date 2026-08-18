package com.alphapowertrading.tickconverter.csv;

import com.alphapowertrading.tickconverter.core.model.Ohlc;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class BidAskCsvWriter {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("UTC"));

    public BufferedWriter open(Path file) throws IOException {
        Path parent = file.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        BufferedWriter writer =
                Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8);

        writer.write("timestamp,open,high,low,close");
        writer.newLine();

        return writer;
    }

    public void write(
            BufferedWriter writer,
            Ohlc candle)
            throws IOException {

        writer.write(
                FORMATTER.format(candle.timestamp()));

        writer.write(',');
        writer.write(Integer.toString(candle.open()));

        writer.write(',');
        writer.write(Integer.toString(candle.high()));

        writer.write(',');
        writer.write(Integer.toString(candle.low()));

        writer.write(',');
        writer.write(Integer.toString(candle.close()));

        writer.newLine();
    }
}