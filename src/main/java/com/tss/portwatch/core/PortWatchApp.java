package com.tss.portwatch.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.diff.SnapshotComparator;
import com.tss.portwatch.core.diff.SnapshotDiff;
import com.tss.portwatch.core.io.SnapshotIO;
import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.report.DiffReporter;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PortWatchApp {

    private final ObjectMapper om;
    private final ListenerCollector collector;

    public PortWatchApp(ObjectMapper om, ListenerCollector collector) {
        this.om = om;
        this.collector = collector;
    }

    public void runOnce() throws Exception {
        Path snapshotsDir = SnapshotIO.snapshotsDir();
        Path previousFile = SnapshotIO.latestSnapshot(snapshotsDir);
        boolean firstRun = (previousFile == null);

        if (firstRun) {
            System.out.println("No previous snapshot found. Creating baseline...");
        }

        List<ListeningSocket> previous = firstRun ? List.of() : SnapshotIO.read(previousFile, om);

        // Scan actual
        String json = collector.collectTcpListenersJson();
        List<ListeningSocket> current = parseSockets(json);

        // Guardar snapshot
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path snapshotFile = SnapshotIO.write(snapshotsDir, "snapshot-" + ts + ".json", om, current);

        if (firstRun) {
            System.out.println("Baseline created: " + snapshotFile.toAbsolutePath());
            System.out.println("Items: " + current.size());
            System.out.println("Baseline mode: no diff generated");
            return;
        }

        // Diff + guardar
        SnapshotDiff diff = SnapshotComparator.compare(previous, current);
        Path diffFile = SnapshotIO.write(
                SnapshotIO.diffsDir(),
                "diff-" + ts + ".json",
                om,
                diff
        );


        // Consola
        System.out.println("Previous: " + previousFile.toAbsolutePath());
        System.out.println("Snapshot saved: " + snapshotFile.toAbsolutePath());
        System.out.println("Items: " + current.size());
        System.out.println("Diff saved: " + diffFile.toAbsolutePath());
        System.out.println("Added: " + diff.added().size());
        System.out.println("Removed: " + diff.removed().size());
        System.out.println("Changed: " + diff.changed().size());
        DiffReporter.printConsole(diff, 10);
    }

    private List<ListeningSocket> parseSockets(String json) throws Exception {


        String t = json.trim();
        if (t.isEmpty()) return List.of();
        return om.readValue(t, new TypeReference<>() {
        });
    }
}
