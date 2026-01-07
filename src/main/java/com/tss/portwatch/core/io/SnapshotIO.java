package com.tss.portwatch.core.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.model.ListeningSocket;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

public final class SnapshotIO {

    public static Path snapshotsDir() {
        return Path.of("data", "snapshots");
    }

    public static Path latestSnapshot(Path dir) throws IOException {
        if (!Files.exists(dir)) return null;

        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("snapshot-") && p.toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElse(null);
        }
    }

    public static List<ListeningSocket> read(Path file, ObjectMapper om) throws IOException {
        String json = Files.readString(file);
        if (json.trim().startsWith("[")) {
            return om.readValue(json, new TypeReference<>() {});
        }
        // caso raro: un solo objeto
        ListeningSocket one = om.readValue(json, ListeningSocket.class);
        return List.of(one);
    }

    public static Path write(Path dir, String filename, ObjectMapper om, Object data) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(filename);
        om.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), data);
        return out;
    }

    private SnapshotIO() {}
}
