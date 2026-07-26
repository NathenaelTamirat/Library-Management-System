package com.library.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExporterTest {
    @Test
    void escapesCommasQuotesAndJoinsRows() {
        String csv = CsvExporter.toCsv(
                List.of("a", "b"),
                List.of(
                        List.of("1", "two,parts"),
                        List.of("say \"hi\"", "ok")));
        assertEquals("a,b\n1,\"two,parts\"\n\"say \"\"hi\"\"\",ok", csv);
    }
}
