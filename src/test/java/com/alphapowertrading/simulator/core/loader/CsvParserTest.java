package com.alphapowertrading.simulator.core.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParserTest {

    @Test
    void shouldConvertPricesToIntegersAndIgnoreVolume() throws Exception {
        Path file = Files.createTempFile("3qqq", ".csv");

        Files.writeString(file,
                "\"Fecha\",\"Último\",\"Apertura\",\"Máximo\",\"Mínimo\",\"Vol.\",\"% var.\"\n" +
                "\"10.08.2026\",\"381,70\",\"379,34\",\"381,70\",\"379,22\",\"1,06K\",\"1,47%\"\n" +
                "\"07.08.2026\",\"376,18\",\"366,68\",\"376,84\",\"366,60\",\"9,75K\",\"2,27%\"\n");

        var data = new CsvParser().load(file);

        assertEquals(2, data.size());

        // Oldest first.
        assertEquals("07.08.2026",
                data.get(0).date().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        assertEquals(36668L, data.get(0).open());
        assertEquals(37684L, data.get(0).high());
        assertEquals(36660L, data.get(0).low());
        assertEquals(37618L, data.get(0).close());
    }
}
