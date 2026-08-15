package com.coresrp.qa.report;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JsonlReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonlReader() {
    }

    public static <T> List<T> read(Path file, Class<T> type) {
        List<T> results = new ArrayList<>();
        if (!Files.exists(file)) {
            return results;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                results.add(MAPPER.readValue(line, type));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
        return results;
    }
}
