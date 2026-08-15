package com.coresrp.qa.report;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Thread-safe append-only JSON-lines writer, used for results/findings/data-footprint logs. */
public final class JsonlWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path file;
    private final Object lock = new Object();

    public JsonlWriter(Path file) {
        this.file = file;
    }

    public void append(Object record) {
        synchronized (lock) {
            try {
                String line = MAPPER.writeValueAsString(record) + System.lineSeparator();
                Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to append to " + file, e);
            }
        }
    }
}
