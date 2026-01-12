package com.tss.portwatch.core.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.model.ListeningSocket;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

public final class SnapshotIO {

    /**
     * Directorio base donde se guardan snapshots y diffs.
     * Puede configurarse con la variable de entorno PORTWATCH_DATA_DIR.
     * Si no existe, se usa "./data" (directorio desde donde se lanza el JAR).
     */
    public static Path baseDataDir() {
        String env = System.getenv("PORTWATCH_DATA_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of("data");
    }

    public static Path snapshotsDir() {
        return baseDataDir().resolve("snapshots");
    }

    public static Path diffsDir() {
        return baseDataDir().resolve("diffs");
    }

    /**
     * Devuelve el snapshot más reciente (por nombre timestamped) o null si no hay ninguno.
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
     * Lee un snapshot y devuelve SIEMPRE una lista.
     * En modo "limpio" asumimos snapshots siempre como JSON array: [...]
     */
    public static List<ListeningSocket> read(Path file, ObjectMapper om) throws IOException {
        String json = Files.readString(file).trim();
        if (json.isEmpty()) return List.of();
        return om.readValue(json, new TypeReference<>() {});
    }

    /**
     * Escribe cualquier objeto como JSON pretty en el directorio indicado.
     */
    public static Path write(Path dir, String filename, ObjectMapper om, Object data) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(filename);
        om.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), data);
        return out;
    }

    private SnapshotIO() {}
}
