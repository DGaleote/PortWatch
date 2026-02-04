package com.tss.portwatch.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.diff.SnapshotComparator;
import com.tss.portwatch.core.diff.SnapshotDiff;
import com.tss.portwatch.core.io.SnapshotIO;
import com.tss.portwatch.core.model.DiffFile;
import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.model.PortWatchMetadata;
import com.tss.portwatch.core.model.SnapshotFile;
import com.tss.portwatch.report.DiffHtmlReportGenerator;
import com.tss.portwatch.report.DiffReportGenerator;
import com.tss.portwatch.report.DiffReporter;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Core application class for PortWatch.
 * <p>
 * Responsibilities:
 * - Orchestrate execution modes (default, snapshot-only, diff-only)
 * - Coordinate data collection, comparison and persistence
 * - Control output rendering (console / file / implicit)
 * - Trigger report generation (Markdown / HTML) when requested
 * <p>
 * This class contains no OS-specific logic and no CLI parsing logic.
 */
public final class PortWatchApp {

    /**
     * Output behavior selector.
     * CONSOLE: no disk persistence (except baseline if missing)
     * FILE: disk persistence only
     */
    public enum OutputMode {CONSOLE, FILE}

    private final ObjectMapper om;
    private final ListenerCollector collector;
    private final String machineId;

    /**
     * Creates a PortWatch application instance.
     *
     * @param om        Jackson ObjectMapper used for JSON serialization
     * @param collector OS-specific listener collector
     */
    public PortWatchApp(ObjectMapper om, ListenerCollector collector) {
        this.om = om;
        this.collector = collector;
        this.machineId = resolveMachineId();
    }

    // -------------------------------------------------------------------------
    // Public entry points (invoked from CLI dispatcher)
    // -------------------------------------------------------------------------

    /**
     * Default execution mode.
     * - If no baseline exists, creates one and stops
     * - Otherwise computes a diff
     */
    public void runDefault(OutputMode mode) throws Exception {
        RunResult r = executeDefaultPipeline(mode);
        render(mode, r);
    }

    /**
     * Snapshot-only execution mode.
     */
    public void runSnapshotOnly(OutputMode mode) throws Exception {
        RunResult r = executeSnapshotOnlyPipeline(mode);
        render(mode, r);
    }

    /**
     * Diff-only execution mode.
     * Optionally generates a report if requested.
     */
    public void runDiffOnly(OutputMode mode, String reportFormat) throws Exception {

        RunResult r = executeDiffOnlyPipeline(mode);
        render(mode, r);

        // Report generation is only allowed if a persisted diff exists
        if (reportFormat != null) {
            if (r.diffPayload == null) {
                throw new IllegalStateException(
                        "No diff available for report generation (baseline created or console-only run)."
                );
            }

            if ("md".equalsIgnoreCase(reportFormat)) {
                generateMarkdownReport(r.diffPayload);
            } else if ("html".equalsIgnoreCase(reportFormat)) {
                generateHtmlReport(r.diffPayload);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Report generation
    // -------------------------------------------------------------------------

    /**
     * Generates an HTML report from a diff payload.
     */
    private void generateHtmlReport(DiffFile diffFile) throws Exception {
        String machineId = diffFile.metadata().machineId();
        String timestamp = diffFile.metadata().timestamp();

        String html = DiffHtmlReportGenerator.generateHtml(diffFile);

        Path dir = SnapshotIO.baseDataDir()
                .resolve("reports")
                .resolve(machineId);

        Files.createDirectories(dir);

        Path out = dir.resolve("report-" + machineId + "-" + timestamp + ".html");
        Files.writeString(out, html);

        System.out.println("Report generated: " + out);
    }

    /**
     * Generates a Markdown report from a diff payload.
     */
    private void generateMarkdownReport(DiffFile diffFile) throws Exception {
        String machineId = diffFile.metadata().machineId();
        String timestamp = diffFile.metadata().timestamp();

        String md = DiffReportGenerator.generateMarkdown(diffFile, null);

        Path dir = SnapshotIO.baseDataDir()
                .resolve("reports")
                .resolve(machineId);

        Files.createDirectories(dir);

        Path out = dir.resolve("report-" + machineId + "-" + timestamp + ".md");
        Files.writeString(out, md);

        System.out.println("Report generated: " + out.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Pipelines (pure work, no rendering)
    // -------------------------------------------------------------------------

    /**
     * Determines whether results should be persisted to disk.
     * CONSOLE mode suppresses persistence except for baseline creation.
     */
    private boolean shouldPersist(OutputMode mode) {
        return mode != OutputMode.CONSOLE;
    }

    /**
     * Default pipeline:
     * - Creates baseline if missing
     * - Otherwise computes diff
     */
    private RunResult executeDefaultPipeline(OutputMode mode) throws Exception {
        Path snapshotsDir = snapshotsDirForMachine();
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
        DiffFile diffPayload = buildDiffPayload(ts, diff);
        Path diffFile = writeDiffFile(ts, diffPayload);

        return RunResult.withDiff(previousFile, snapshotFile, diffFile, diff, diffPayload);
    }

    /**
     * Snapshot-only pipeline.
     */
    private RunResult executeSnapshotOnlyPipeline(OutputMode mode) throws Exception {
        List<ListeningSocket> current = collectCurrent();

        String json = null;
        if (mode == null || mode == OutputMode.CONSOLE) {
            json = om.writerWithDefaultPrettyPrinter().writeValueAsString(current);
        }

        if (mode == OutputMode.CONSOLE) {
            return RunResult.snapshotConsoleOnly(json);
        }

        Path snapshotsDir = snapshotsDirForMachine();
        Path previousFile = SnapshotIO.latestSnapshot(snapshotsDir);

        String ts = nowTs();
        Path snapshotFile = writeSnapshot(snapshotsDir, ts, current);

        if (previousFile == null) {
            return RunResult.baselineOnly(snapshotFile, false, json);
        }

        return RunResult.snapshotOnly(snapshotFile, json);
    }

    /**
     * Diff-only pipeline.
     */
    private RunResult executeDiffOnlyPipeline(OutputMode mode) throws Exception {
        Path snapshotsDir = snapshotsDirForMachine();
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
        DiffFile diffPayload = buildDiffPayload(ts, diff);
        Path diffFile = writeDiffFile(ts, diffPayload);

        return RunResult.withDiff(previousFile, snapshotFile, diffFile, diff, diffPayload);
    }

    // -------------------------------------------------------------------------
    // Rendering (output only)
    // -------------------------------------------------------------------------

    private void render(OutputMode mode, RunResult r) {
        if (r.baselineCreated) {
            renderFile(mode, r);
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

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    private Path createBaseline(Path snapshotsDir) throws Exception {
        String ts = nowTs();
        List<ListeningSocket> current = collectCurrent();
        return writeSnapshot(snapshotsDir, ts, current);
    }

    private Path writeSnapshot(Path snapshotsDir, String ts, List<ListeningSocket> current) throws Exception {
        PortWatchMetadata meta = new PortWatchMetadata(machineId, System.getProperty("os.name"), ts);
        SnapshotFile payload = new SnapshotFile(meta, current);

        return SnapshotIO.write(
                snapshotsDir,
                "snapshot-" + machineId + "-" + ts + ".json",
                om,
                payload
        );
    }

    private DiffFile buildDiffPayload(String ts, SnapshotDiff diff) {
        PortWatchMetadata meta = new PortWatchMetadata(machineId, System.getProperty("os.name"), ts);
        return new DiffFile(meta, diff);
    }

    private Path writeDiffFile(String ts, DiffFile payload) throws Exception {
        return SnapshotIO.write(
                diffsDirForMachine(),
                "diff-" + machineId + "-" + ts + ".json",
                om,
                payload
        );
    }

    // -------------------------------------------------------------------------
    // Collection and parsing
    // -------------------------------------------------------------------------

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

    private String resolveMachineId() {
        String host = null;

        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }

        if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            host = System.getenv("COMPUTERNAME");
            if (host == null || host.isBlank()) host = System.getenv("HOSTNAME");
        }

        if (host == null || host.isBlank()) host = "UNKNOWN";
        return host.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Path snapshotsDirForMachine() {
        return SnapshotIO.snapshotsDir().resolve(machineId);
    }

    private Path diffsDirForMachine() {
        return SnapshotIO.diffsDir().resolve(machineId);
    }

    // -------------------------------------------------------------------------
    // Result carrier
    // -------------------------------------------------------------------------

    /**
     * Immutable carrier for pipeline execution results.
     * Encapsulates all data required for rendering and report generation.
     */
    private static final class RunResult {
        final boolean baselineCreated;
        final boolean suggestDiff;

        final DiffFile diffPayload;
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
                String snapshotJson,
                DiffFile diffPayload
        ) {
            this.baselineCreated = baselineCreated;
            this.suggestDiff = suggestDiff;
            this.baselineFile = baselineFile;
            this.previousFile = previousFile;
            this.snapshotFile = snapshotFile;
            this.diffFile = diffFile;
            this.diff = diff;
            this.snapshotJson = snapshotJson;
            this.diffPayload = diffPayload;
        }

        static RunResult baselineOnly(Path baselineFile, boolean suggestDiff) {
            return new RunResult(true, suggestDiff, baselineFile, null, null, null, null, null, null);
        }

        static RunResult baselineOnly(Path baselineFile, boolean suggestDiff, String snapshotJson) {
            return new RunResult(true, suggestDiff, baselineFile, null, null, null, null, snapshotJson, null);
        }

        static RunResult snapshotOnly(Path snapshotFile, String snapshotJson) {
            return new RunResult(false, false, null, null, snapshotFile, null, null, snapshotJson, null);
        }

        static RunResult withDiff(Path previousFile, Path snapshotFile, Path diffFile,
                                  SnapshotDiff diff, DiffFile diffPayload) {
            return new RunResult(false, false, null, previousFile, snapshotFile, diffFile, diff, null, diffPayload);
        }

        static RunResult diffOnlyInMemory(Path previousFile, SnapshotDiff diff) {
            return new RunResult(false, false, null, previousFile, null, null, diff, null, null);
        }

        static RunResult snapshotConsoleOnly(String snapshotJson) {
            return new RunResult(false, false, null, null, null, null, null, snapshotJson, null);
        }
    }
}
