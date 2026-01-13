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

/**
 * Core application orchestrator.
 *
 * Responsibilities:
 *  - Determine previous snapshot (if any)
 *  - Collect current listening TCP sockets via the injected ListenerCollector
 *  - Persist current snapshot to disk
 *  - If not first run: compute diff (previous vs current), persist it and print a console report
 *
 * NOTE: This class is OS-agnostic. Platform specifics are isolated inside ListenerCollector
 * implementations (Windows/Linux/macOS).
 */
public final class PortWatchApp {

    /** Jackson mapper injected from CLI layer (Main). Used for JSON read/write. */
    private final ObjectMapper om;

    /** OS-specific strategy that produces a JSON snapshot of TCP listeners. */
    private final ListenerCollector collector;

    public PortWatchApp(ObjectMapper om, ListenerCollector collector) {
        this.om = om;
        this.collector = collector;
    }

    /**
     * Executes a single run:
     *  - baseline mode if no previous snapshots exist
     *  - diff mode otherwise
     */
    public void runOnce() throws Exception {
        // Ensure snapshots directory exists and locate the latest snapshot file (if any).
        Path snapshotsDir = SnapshotIO.snapshotsDir();
        Path previousFile = SnapshotIO.latestSnapshot(snapshotsDir);
        boolean firstRun = (previousFile == null);

        if (firstRun) {
            // First execution on this machine (or snapshots were deleted): create baseline only.
            System.out.println("No previous snapshot found. Creating baseline...");
        }

        // Previous snapshot is empty when running for the first time.
        List<ListeningSocket> previous = firstRun ? List.of() : SnapshotIO.read(previousFile, om);

        // Collect current state using the OS-specific collector.
        // The collector returns JSON representing a List<ListeningSocket>.
        String json = collector.collectTcpListenersJson();
        List<ListeningSocket> current = parseSockets(json);

        // Persist current snapshot with a timestamp-based filename to keep a historical trail.
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path snapshotFile = SnapshotIO.write(snapshotsDir, "snapshot-" + ts + ".json", om, current);

        if (firstRun) {
            // Baseline mode: no diff is generated because there is no previous reference.
            System.out.println("Baseline created: " + snapshotFile.toAbsolutePath());
            System.out.println("Items: " + current.size());
            System.out.println("Baseline mode: no diff generated");
            return;
        }

        // Compute diff between the previous snapshot and the current snapshot.
        SnapshotDiff diff = SnapshotComparator.compare(previous, current);

        // Persist diff as JSON (separate folder from snapshots).
        Path diffFile = SnapshotIO.write(
                SnapshotIO.diffsDir(),
                "diff-" + ts + ".json",
                om,
                diff
        );

        // Console summary (human-friendly quick view).
        System.out.println("Previous: " + previousFile.toAbsolutePath());
        System.out.println("Snapshot saved: " + snapshotFile.toAbsolutePath());
        System.out.println("Items: " + current.size());
        System.out.println("Diff saved: " + diffFile.toAbsolutePath());
        System.out.println("Added: " + diff.added().size());
        System.out.println("Removed: " + diff.removed().size());
        System.out.println("Changed: " + diff.changed().size());

        // Print a limited amount of diff entries to keep console output readable.
        DiffReporter.printConsole(diff, 10);
    }

    /**
     * Parses JSON returned by a collector into a list of ListeningSocket.
     *
     * Collectors are expected to return a JSON array (possibly empty) with the same
     * structure used by SnapshotIO.
     */
    private List<ListeningSocket> parseSockets(String json) throws Exception {
        // Be tolerant with empty output (e.g., collector returns nothing due to permissions or no listeners).
        String t = json.trim();
        if (t.isEmpty()) return List.of();

        // Anonymous TypeReference keeps the generic type information for Jackson.
        return om.readValue(t, new TypeReference<>() {});
    }
}
