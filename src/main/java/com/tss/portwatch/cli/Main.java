package com.tss.portwatch.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.collector.WindowsPowerShellCollector;
import com.tss.portwatch.core.diff.SnapshotComparator;
import com.tss.portwatch.core.diff.SnapshotDiff;
import com.tss.portwatch.core.io.SnapshotIO;
import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.os.OsDetector;
import com.tss.portwatch.report.DiffReporter;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        var os = OsDetector.detect();

        ListenerCollector collector = switch (os) {
            case WINDOWS -> new WindowsPowerShellCollector();
            default -> throw new IllegalStateException("Unsupported OS for now: " + os);
        };

        ObjectMapper om = new ObjectMapper();

        Path dir = SnapshotIO.snapshotsDir();
        Path previousFile = SnapshotIO.latestSnapshot(dir);
        boolean isFirstRun = (previousFile == null);

        if (isFirstRun) {
            System.out.println("No previous snapshot found. Creating baseline...");
        }

        List<ListeningSocket> previous = isFirstRun ? List.of() : SnapshotIO.read(previousFile, om);

        // Nuevo escaneo
        String json = collector.collectTcpListenersJson();
        List<ListeningSocket> current;
        if (json.trim().startsWith("[")) {
            current = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } else {
            current = List.of(om.readValue(json, ListeningSocket.class));
        }

        // Guardar snapshot nuevo
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path snapshotFile = SnapshotIO.write(dir, "snapshot-" + ts + ".json", om, current);

        if (isFirstRun) {
            System.out.println("Baseline created: " + snapshotFile.toAbsolutePath());
        }

        // Calcular diff y guardarlo
        SnapshotDiff diff = null;
        Path diffFile = null;

        if (!isFirstRun) {
            diff = SnapshotComparator.compare(previous, current);
            diffFile = SnapshotIO.write(Path.of("data", "diffs"), "diff-" + ts + ".json", om, diff);
        }


        // Resumen por consola
        if (isFirstRun) {
            System.out.println("Items: " + current.size());
            System.out.println("Baseline mode: no diff generated");
        } else {
            System.out.println("Previous: " + previousFile.toAbsolutePath());
            System.out.println("Snapshot saved: " + snapshotFile.toAbsolutePath());
            System.out.println("Items: " + current.size());
            System.out.println("Diff saved: " + diffFile.toAbsolutePath());
            System.out.println("Added: " + diff.added().size());
            System.out.println("Removed: " + diff.removed().size());
            System.out.println("Changed: " + diff.changed().size());
            DiffReporter.printConsole(diff, 10);
        }


    }

}
