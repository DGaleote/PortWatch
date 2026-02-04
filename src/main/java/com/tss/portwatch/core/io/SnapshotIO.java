package com.tss.portwatch.core.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.model.DiffFile;
import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.model.SnapshotFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Centralizes all disk persistence for PortWatch.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Resolve the base data directory (default, env override, or programmatic override)</li>
 *   <li>Expose standard subdirectories (snapshots / diffs)</li>
 *   <li>Locate the latest snapshot for a machine</li>
 *   <li>Read snapshot and diff JSON files</li>
 *   <li>Write JSON payloads to disk as pretty-printed JSON</li>
 * </ul>
 * <p>
 * This class is intentionally small and "dumb": it does not decide when to persist,
 * it only performs filesystem I/O once instructed by the application layer.
 */
public final class SnapshotIO {

    /**
     * Optional programmatic override for the base directory.
     * Marked volatile to ensure updates are visible across threads.
     */
    private static volatile Path overrideBaseDir = null;

    /**
     * Overrides the base directory where PortWatch stores its data.
     * Typically set from the CLI flag --output-dir.
     *
     * @param baseDir base directory to use for all persistence
     */
    public static void setBaseDataDir(Path baseDir) {
        overrideBaseDir = baseDir;
    }

    /**
     * Returns the base directory where all PortWatch data is stored.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Programmatic override via {@link #setBaseDataDir(Path)}</li>
     *   <li>Environment variable PORTWATCH_DATA_DIR</li>
     *   <li>Default relative directory "data"</li>
     * </ol>
     *
     * @return base directory path
     */
    public static Path baseDataDir() {
        if (overrideBaseDir != null) {
            return overrideBaseDir;
        }

        String env = System.getenv("PORTWATCH_DATA_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }

        return Path.of("data");
    }

    /**
     * Directory where snapshot JSON files are stored.
     *
     * @return snapshots directory
     */
    public static Path snapshotsDir() {
        return baseDataDir().resolve("snapshots");
    }

    /**
     * Directory where diff JSON files are stored.
     *
     * @return diffs directory
     */
    public static Path diffsDir() {
        return baseDataDir().resolve("diffs");
    }

    /**
     * Returns the most recent snapshot file inside the given directory.
     * <p>
     * Selection strategy:
     * - Only considers files named "snapshot-*.json"
     * - Uses lexicographical ordering, which works because timestamps
     * in filenames are formatted as yyyyMMdd-HHmmss
     *
     * @param dir machine snapshot directory to inspect
     * @return path to latest snapshot, or null if the directory does not exist or has no snapshots
     * @throws IOException if directory listing fails
     */
    public static Path latestSnapshot(Path dir) throws IOException {
        if (!Files.exists(dir)) return null;

        try (var s = Files.list(dir)) {
            return s
                    .filter(p -> p.getFileName().toString().startsWith("snapshot-")
                            && p.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElse(null);
        }
    }

    /**
     * Reads a snapshot JSON file and returns the contained socket list.
     * <p>
     * Snapshot files are expected to be wrapped as:
     * <pre>
     * {
     *   "metadata": { ... },
     *   "sockets": [ { ... }, { ... } ]
     * }
     * </pre>
     * <p>
     * The returned list is never null.
     *
     * @param file snapshot file to read
     * @param om   object mapper used for deserialization
     * @return sockets list, or an empty list if the file contains null sockets
     * @throws IOException if parsing fails
     */
    public static List<ListeningSocket> read(Path file, ObjectMapper om) throws IOException {
        try {
            SnapshotFile wrapper = om.readValue(file.toFile(), SnapshotFile.class);
            return (wrapper.sockets() == null) ? List.of() : wrapper.sockets();
        } catch (IOException e) {
            throw new IOException("Failed to read snapshot file: " + file, e);
        }
    }

    /**
     * Reads a diff JSON file into its wrapper type.
     *
     * @param file diff file to read
     * @param om   object mapper used for deserialization
     * @return parsed {@link DiffFile}
     * @throws IOException if parsing fails
     */
    public static DiffFile readDiff(Path file, ObjectMapper om) throws IOException {
        return om.readValue(file.toFile(), DiffFile.class);
    }

    /**
     * Writes the given payload as pretty-printed JSON in the target directory.
     * The directory is created if it does not exist.
     *
     * @param dir      target directory
     * @param filename output filename
     * @param om       object mapper used for serialization
     * @param data     payload to write
     * @return path to the written file
     * @throws IOException if writing fails
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
    private SnapshotIO() {
    }
}
