package com.library.util;

import java.util.ArrayList;
import java.util.List;

public final class CsvExporter {
    private CsvExporter() {
    }

    public static String toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append(line(headers));
        for (List<String> row : rows) {
            csv.append('\n').append(line(row));
        }
        return csv.toString();
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    private static String line(List<String> cells) {
        List<String> escaped = new ArrayList<>(cells.size());
        for (String cell : cells) {
            escaped.add(escape(cell));
        }
        return String.join(",", escaped);
    }
}
