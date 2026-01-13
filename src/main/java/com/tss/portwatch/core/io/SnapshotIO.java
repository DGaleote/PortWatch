package com.tss.portwatch.core.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.model.ListeningSocket;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

/**
 * Handles all persistence of snapshots and diffs on disk.
 *
 * This class is responsible for:
 *  - Resolving the base data directory
 *  - Locating the latest snapshot
 *  - Reading snapshot JSON files
 *  - Writing snapshots and diffs as pretty-printed JSON
 */
public final class SnapshotIO {

    /**
     * Base directory where all PortWatch data is stored.
     *
     * Can be overridden via the PORTWATCH_DATA_DIR environment variable.
     * If not set, defaults to "./data" relative to where the JAR is executed.
     */
    public static Path baseDataDir() {
        String env = System.getenv("PORTWATCH_DATA_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of("data");
    }

    /**
     * Directory where snapshot JSON files are stored.
     */
    public static Path snapshotsDir() {
        return baseDataDir().resolve("snapshots");
    }

    /**
     * Directory where diff JSON files are stored.
     */
    public static Path diffsDir() {
        return baseDataDir().resolve("diffs");
    }

    /**
     * Returns the most recent snapshot file (based on timestamped filename),
     * or null if no snapshots exist yet.
     */
    public static Path latestSnapshot(Path dir) throws IOException {
        if (!Files.exists(dir)) return null;

        try (var s = Files.list(dir)) {
            return s
                    .filter(p -> p.getFileName().toString().startsWith("snapshot-")
                            && p.getFileName().toString().endsWith(".json"))
                    // Lexicographical comparison works because filenames are timestamped as yyyyMMdd-HHmmss
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElse(null);
        }
    }

    /**
     * Reads a snapshot file and always returns a list.
     *
     * Snapshots are expected to be JSON arrays:
     *   [ { ... }, { ... } ]
     */
    public static List<ListeningSocket> read(Path file, ObjectMapper om) throws IOException {
        String json = Files.readString(file).trim();
        if (json.isEmpty()) return List.of();
        return om.readValue(json, new TypeReference<>() {});
    }

    /**
     * Writes any object as pretty-printed JSON into the given directory.
     *
     * The directory is created if it does not exist.
     */
    public static Path write(Path dir, String filename, ObjectMapper om, Object data) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(filename);
        om.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), data);
        return out;
    }

    /**
     * Utility class: no instances allowed.
     */
    private SnapshotIO() {}
}
