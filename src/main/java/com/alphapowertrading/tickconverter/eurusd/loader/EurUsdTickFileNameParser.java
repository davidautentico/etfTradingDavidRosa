package com.alphapowertrading.tickconverter.eurusd.loader;

import com.alphapowertrading.tickconverter.eurusd.model.EurUsdTickFile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class EurUsdTickFileNameParser {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    public EurUsdTickFile parse(Path path) {
        String filename = removeExtension(path.getFileName().toString());
        String[] parts = filename.split("_");

        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "Invalid tick filename: " + path.getFileName()
                            + ". Expected SYMBOL_SIDE_YYYY-MM-DD_HH"
            );
        }

        String symbol = parts[0].toUpperCase(Locale.ROOT);
        EurUsdTickFile.Side side = parseSide(parts[1], path);
        LocalDate date = parseDate(parts[2], path);
        int hour = parseHour(parts[3], path);

        return new EurUsdTickFile(path, symbol, side, date, hour);
    }

    private EurUsdTickFile.Side parseSide(String value, Path path) {
        try {
            return EurUsdTickFile.Side.valueOf(
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid BID/ASK side in filename: " + path.getFileName(),
                    e
            );
        }
    }

    private LocalDate parseDate(String value, Path path) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date in filename: " + path.getFileName(),
                    e
            );
        }
    }

    private int parseHour(String value, Path path) {
        try {
            int hour = Integer.parseInt(value);

            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException(
                        "Hour must be between 00 and 23: " + path.getFileName()
                );
            }

            return hour;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid hour in filename: " + path.getFileName(),
                    e
            );
        }
    }

    private String removeExtension(String filename) {
        int index = filename.lastIndexOf('.');

        return index > 0 ? filename.substring(0, index) : filename;
    }
}
