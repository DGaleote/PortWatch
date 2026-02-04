package com.tss.portwatch.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.PortWatchApp;
import com.tss.portwatch.core.PortWatchApp.OutputMode;
import com.tss.portwatch.core.collector.LinuxSsCollector;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.collector.MacOsLsofCollector;
import com.tss.portwatch.core.collector.WindowsPowerShellCollector;
import com.tss.portwatch.core.io.SnapshotIO;
import com.tss.portwatch.core.os.OsDetector;

import java.nio.file.Path;

/**
 * CLI entry point for PortWatch.
 * <p>
 * Responsibilities:
 * - Parse command-line flags and validate supported combinations.
 * - Configure base output directory (optional).
 * - Wire the OS-specific collector.
 * - Delegate execution to {@link PortWatchApp}.
 * <p>
 * This class intentionally keeps orchestration and validation in the CLI layer,
 * while keeping core logic inside {@link PortWatchApp}.
 */
public final class Main {

    private Main() {
        // Utility class: no instances.
    }

    public static void main(String[] args) throws Exception {
        CliOptions opt = parseArgs(args);
        if (opt == null) return;

        // Optional override of the base data directory (defaults to ./data).
        if (opt.outputDir != null) {
            SnapshotIO.setBaseDataDir(Path.of(opt.outputDir));
        }

        ListenerCollector collector = wireCollector();
        PortWatchApp app = new PortWatchApp(new ObjectMapper(), collector);

        dispatch(app, opt);
    }

    // ----------------- Parsing -----------------

    /**
     * Parses CLI flags.
     * <p>
     * Returns null if parsing fails (usage printed to stderr).
     * <p>
     * Supported flags:
     * - --snapshot
     * - --diff
     * - --output=console|file
     * - --output-dir=<path>
     * - --report=md|html
     * <p>
     * Validation rules:
     * - No duplicated flags.
     * - --report requires --diff.
     * - --report requires persistence (cannot be used with --output=console).
     */
    private static CliOptions parseArgs(String[] args) {
        int outputDirCount = 0;
        String outputDir = null;

        int snapshotCount = 0;
        int diffCount = 0;
        int outputCount = 0;
        int reportCount = 0;

        // null => implicit output mode (combined behavior)
        OutputMode outputMode = null;

        // null => no report requested
        String reportFormat = null;

        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;

            if ("--snapshot".equals(arg)) {
                snapshotCount++;
                continue;
            }

            if ("--diff".equals(arg)) {
                diffCount++;
                continue;
            }

            if (arg.startsWith("--output=")) {
                outputCount++;
                String value = arg.substring("--output=".length()).trim().toLowerCase();

                outputMode = switch (value) {
                    case "console" -> OutputMode.CONSOLE;
                    case "file" -> OutputMode.FILE;
                    default -> {
                        System.err.println("Invalid --output value: " + value + " (allowed: console|file)");
                        printUsage();
                        yield null;
                    }
                };

                if (outputMode == null) return null;
                continue;
            }

            if (arg.startsWith("--output-dir=")) {
                outputDirCount++;
                String value = arg.substring("--output-dir=".length()).trim();

                if (value.isBlank()) {
                    System.err.println("Invalid --output-dir value (empty).");
                    printUsage();
                    return null;
                }

                outputDir = value;
                continue;
            }

            if (arg.startsWith("--report=")) {
                reportCount++;
                String value = arg.substring("--report=".length()).trim().toLowerCase();

                reportFormat = switch (value) {
                    case "md" -> "md";
                    case "html" -> "html";
                    default -> {
                        System.err.println("Invalid --report value: " + value + " (allowed: md|html)");
                        printUsage();
                        yield null;
                    }
                };

                if (reportFormat == null) return null;
                continue;
            }

            System.err.println("Unknown flag: " + arg);
            printUsage();
            return null;
        }

        // Duplicate flag detection
        if (snapshotCount > 1) {
            System.err.println("Duplicate flag: --snapshot");
            printUsage();
            return null;
        }
        if (diffCount > 1) {
            System.err.println("Duplicate flag: --diff");
            printUsage();
            return null;
        }
        if (outputCount > 1) {
            System.err.println("Duplicate flag: --output");
            printUsage();
            return null;
        }
        if (outputDirCount > 1) {
            System.err.println("Duplicate flag: --output-dir");
            printUsage();
            return null;
        }
        if (reportCount > 1) {
            System.err.println("Duplicate flag: --report");
            printUsage();
            return null;
        }

        // Report requires a diff to interpret.
        if (reportFormat != null && diffCount != 1) {
            System.err.println("--report requires --diff.");
            printUsage();
            return null;
        }

        // Report requires persisted data (diff payload available). Console mode is non-persistent.
        if (reportFormat != null && outputMode == OutputMode.CONSOLE) {
            System.err.println("--report requires file output (use --output=file or omit --output).");
            printUsage();
            return null;
        }

        return new CliOptions(snapshotCount == 1, diffCount == 1, outputMode, outputDir, reportFormat);
    }

    // ----------------- Collector wiring -----------------

    /**
     * Wires an OS-specific {@link ListenerCollector}.
     * <p>
     * OS detection is centralized via {@link OsDetector}.
     * Each collector is responsible for obtaining TCP listening sockets on its target OS.
     *
     * @return collector implementation for the current OS
     */
    private static ListenerCollector wireCollector() {
        var os = OsDetector.detect();
        return switch (os) {
            case WINDOWS -> new WindowsPowerShellCollector();
            case LINUX -> new LinuxSsCollector();
            case MAC -> new MacOsLsofCollector();
            default -> throw new IllegalStateException("Unsupported OS for now: " + os);
        };
    }

    // ----------------- Dispatch -----------------

    /**
     * Applies high-level execution selection and delegates to {@link PortWatchApp}.
     * <p>
     * Selection rules:
     * - --snapshot and --diff are mutually exclusive.
     * - No mode flags => default mode.
     * - --snapshot => snapshot-only mode.
     * - --diff => diff-only mode, optionally with report generation.
     */
    private static void dispatch(PortWatchApp app, CliOptions opt) throws Exception {
        if (opt.snapshot && opt.diff) {
            System.err.println("Cannot use --snapshot and --diff together.");
            printUsage();
            return;
        }

        if (!opt.snapshot && !opt.diff) {
            app.runDefault(opt.outputMode); // null => implicit combined output
            return;
        }

        if (opt.snapshot) {
            app.runSnapshotOnly(opt.outputMode); // null => implicit combined output
            return;
        }

        // Diff-only mode. A report format is only meaningful here.
        app.runDiffOnly(opt.outputMode, opt.reportFormat);
    }

    // ----------------- Help -----------------

    /**
     * Prints CLI usage information to stderr.
     */
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  portwatch [--snapshot | --diff] [--output=console|file] [--output-dir=<path>] [--report=md|html]");
        System.err.println("Notes:");
        System.err.println("  If no flags are provided, PortWatch runs in default mode.");
        System.err.println("  If --output is omitted, output is combined.");
        System.err.println("  --report requires --diff.");
    }

    // ----------------- Options DTO -----------------

    /**
     * Parsed CLI options.
     * <p>
     * outputMode:
     * - null means "implicit combined mode" (file + console), consistent with existing CLI behavior.
     * <p>
     * reportFormat:
     * - null means "no report requested".
     */
    private static final class CliOptions {
        final String outputDir;

        final boolean snapshot;
        final boolean diff;

        final OutputMode outputMode; // null => implicit combined behavior
        final String reportFormat;   // null => no report

        private CliOptions(boolean snapshot, boolean diff, OutputMode outputMode, String outputDir, String reportFormat) {
            this.snapshot = snapshot;
            this.diff = diff;
            this.outputMode = outputMode;
            this.outputDir = outputDir;
            this.reportFormat = reportFormat;
        }
    }
}
