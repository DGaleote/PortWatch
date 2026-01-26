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

    public enum OutputMode {CONSOLE, FILE}

    private final ObjectMapper om;
    private final ListenerCollector collector;

    public PortWatchApp(ObjectMapper om, ListenerCollector collector) {
        this.om = om;
        this.collector = collector;
    }

    // ----------------- Public entry points -----------------

    public void runDefault(OutputMode mode) throws Exception {
        RunResult r = executeDefaultPipeline(mode);
        render(mode, r);
    }

    public void runSnapshotOnly(OutputMode mode) throws Exception {
        RunResult r = executeSnapshotOnlyPipeline(mode);
        render(mode, r);
    }

    public void runDiffOnly(OutputMode mode) throws Exception {
        RunResult r = executeDiffOnlyPipeline(mode);
        render(mode, r);
    }

    // ----------------- Pipelines (work, no output) -----------------

    private boolean shouldPersist(OutputMode mode) {
        // console = sigiloso (no escribe a disco). Si no hay baseline, se crea baseline igualmente.
        return mode != OutputMode.CONSOLE;
    }

    /**
     * Default:
     * - If no baseline: create baseline snapshot and stop (no diff)
     * - Else:
     * - CONSOLE: compute diff in memory (no disk writes)
     * - FILE or implicit: write snapshot + diff
     */
    private RunResult executeDefaultPipeline(OutputMode mode) throws Exception {
        Path snapshotsDir = SnapshotIO.snapshotsDir();
        Path previousFile = SnapshotIO.latestSnapshot(snapshotsDir);

        if (previousFile == null) {
            Path baselineFile = createBaseline(snapshotsDir);
            return RunResult.baselineOnly(baselineFile, true);
        }

        List<ListeningSocket> current = collectCurrent();
        List<ListeningSocket> previous = SnapshotIO.read(previousFile, om);
        SnapshotDiff diff = SnapshotComparator.compare(previous, current);

        if (!shouldPersist(mode)) {
            return RunResult.diffOnlyInMemory(previousFile, diff);
        }

        String ts = nowTs();
        Path snapshotFile = writeSnapshot(snapshotsDir, ts, current);
        Path diffFile = writeDiff(ts, diff);
        return RunResult.withDiff(previousFile, snapshotFile, diffFile, diff);
    }

    /**
     * Snapshot-only:
     * - CONSOLE: print snapshot JSON to stdout (no disk writes)
     * - FILE or implicit: write snapshot; implicit also prints JSON
     */
    private RunResult executeSnapshotOnlyPipeline(OutputMode mode) throws Exception {
        List<ListeningSocket> current = collectCurrent();

        // JSON is needed for CONSOLE and implicit mode
        String json = null;
        if (mode == null || mode == OutputMode.CONSOLE) {
            json = om.writerWithDefaultPrettyPrinter().writeValueAsString(current);
        }

        if (mode == OutputMode.CONSOLE) {
            return RunResult.snapshotConsoleOnly(json);
        }

        Path snapshotsDir = SnapshotIO.snapshotsDir();
        Path previousFile = SnapshotIO.latestSnapshot(snapshotsDir);

        String ts = nowTs();
        Path snapshotFile = writeSnapshot(snapshotsDir, ts, current);

        if (previousFile == null) {
            // baseline snapshot (snapshot-only: no "run again" guidance)
            return RunResult.baselineOnly(snapshotFile, false, json);
        }

        return RunResult.snapshotOnly(snapshotFile, json);
    }

    /**
     * Diff-only:
     * - If no baseline: create baseline snapshot and stop
     * - Else:
     * - CONSOLE: compute diff in memory (no disk writes)
     * - FILE or implicit: write snapshot + diff
     */
    private RunResult executeDiffOnlyPipeline(OutputMode mode) throws Exception {
        Path snapshotsDir = SnapshotIO.snapshotsDir();
        Path previousFile = SnapshotIO.latestSnapshot(snapshotsDir);

        if (previousFile == null) {
            Path baselineFile = createBaseline(snapshotsDir);
            return RunResult.baselineOnly(baselineFile, true);
        }

        List<ListeningSocket> current = collectCurrent();
        List<ListeningSocket> previous = SnapshotIO.read(previousFile, om);
        SnapshotDiff diff = SnapshotComparator.compare(previous, current);

        if (!shouldPersist(mode)) {
            return RunResult.diffOnlyInMemory(previousFile, diff);
        }

        String ts = nowTs();
        Path snapshotFile = writeSnapshot(snapshotsDir, ts, current);
        Path diffFile = writeDiff(ts, diff);
        return RunResult.withDiff(previousFile, snapshotFile, diffFile, diff);
    }

    // ----------------- Render (output) -----------------

    private void render(OutputMode mode, RunResult r) {

        // Baseline: always minimal output (paths + guidance if applies)
        if (r.baselineCreated) {
            renderFile(mode, r);

            // snapshot-only implicit mode prints JSON even if baseline just created
            if (mode == null && r.snapshotJson != null) {
                System.out.println(r.snapshotJson);
            }
            return;
        }

        if (mode == OutputMode.FILE) {
            renderFile(mode, r);
            return;
        }

        if (mode == OutputMode.CONSOLE) {
            renderConsole(r);
            return;
        }

        // implicit (no --output): combined
        renderFile(mode, r);
        renderConsole(r);
    }

    private void renderFile(OutputMode mode, RunResult r) {
        if (r.baselineCreated) {
            if (r.suggestDiff) System.out.println("No previous snapshot found.");
            System.out.println("Baseline snapshot created: " + r.baselineFile.toAbsolutePath());
            if (r.suggestDiff) System.out.println("Run again in a few minutes to generate a diff.");
            return;
        }

        // In FILE mode we print "Previous:". In implicit mode, console will also print it.
        boolean implicitWillAlsoPrintConsole = (mode == null);
        if (r.previousFile != null && !implicitWillAlsoPrintConsole) {
            System.out.println("Previous: " + r.previousFile.toAbsolutePath());
        }

        if (r.snapshotFile != null) {
            System.out.println("Snapshot saved: " + r.snapshotFile.toAbsolutePath());
        }
        if (r.diffFile != null) {
            System.out.println("Diff saved: " + r.diffFile.toAbsolutePath());
        }
    }

    private void renderConsole(RunResult r) {
        // snapshot-only console/implicit: print JSON
        if (r.snapshotJson != null) {
            System.out.println(r.snapshotJson);
            return;
        }

        if (r.diff == null) return;

        System.out.println("Previous: " + r.previousFile.toAbsolutePath());
        System.out.println("Added: " + r.diff.added().size());
        System.out.println("Removed: " + r.diff.removed().size());
        System.out.println("Changed: " + r.diff.changed().size());
        DiffReporter.printConsole(r.diff, 10);
    }

    // ----------------- Baseline + persistence helpers -----------------

    private Path createBaseline(Path snapshotsDir) throws Exception {
        String ts = nowTs();
        List<ListeningSocket> current = collectCurrent();
        return writeSnapshot(snapshotsDir, ts, current);
    }

    private Path writeSnapshot(Path snapshotsDir, String ts, List<ListeningSocket> current) throws Exception {
        return SnapshotIO.write(snapshotsDir, "snapshot-" + ts + ".json", om, current);
    }

    private Path writeDiff(String ts, SnapshotDiff diff) throws Exception {
        return SnapshotIO.write(SnapshotIO.diffsDir(), "diff-" + ts + ".json", om, diff);
    }

    // ----------------- Collection + parsing -----------------

    private List<ListeningSocket> collectCurrent() throws Exception {
        String json = collector.collectTcpListenersJson();
        return parseSockets(json);
    }

    private List<ListeningSocket> parseSockets(String json) throws Exception {
        String t = json.trim();
        if (t.isEmpty()) return List.of();
        return om.readValue(t, new TypeReference<>() {
        });
    }

    private String nowTs() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    // ----------------- Result carrier -----------------

    private static final class RunResult {
        final boolean baselineCreated;
        final boolean suggestDiff;

        final Path baselineFile;
        final Path previousFile;
        final Path snapshotFile;
        final Path diffFile;
        final SnapshotDiff diff;

        final String snapshotJson;

        private RunResult(
                boolean baselineCreated,
                boolean suggestDiff,
                Path baselineFile,
                Path previousFile,
                Path snapshotFile,
                Path diffFile,
                SnapshotDiff diff,
                String snapshotJson
        ) {
            this.baselineCreated = baselineCreated;
            this.suggestDiff = suggestDiff;
            this.baselineFile = baselineFile;
            this.previousFile = previousFile;
            this.snapshotFile = snapshotFile;
            this.diffFile = diffFile;
            this.diff = diff;
            this.snapshotJson = snapshotJson;
        }

        static RunResult baselineOnly(Path baselineFile, boolean suggestDiff) {
            return new RunResult(true, suggestDiff, baselineFile, null, null, null, null, null);
        }

        static RunResult baselineOnly(Path baselineFile, boolean suggestDiff, String snapshotJson) {
            return new RunResult(true, suggestDiff, baselineFile, null, null, null, null, snapshotJson);
        }

        static RunResult snapshotOnly(Path snapshotFile, String snapshotJson) {
            return new RunResult(false, false, null, null, snapshotFile, null, null, snapshotJson);
        }

        static RunResult withDiff(Path previousFile, Path snapshotFile, Path diffFile, SnapshotDiff diff) {
            return new RunResult(false, false, null, previousFile, snapshotFile, diffFile, diff, null);
        }

        static RunResult diffOnlyInMemory(Path previousFile, SnapshotDiff diff) {
            return new RunResult(false, false, null, previousFile, null, null, diff, null);
        }

        static RunResult snapshotConsoleOnly(String snapshotJson) {
            return new RunResult(false, false, null, null, null, null, null, snapshotJson);
        }
    }
}
