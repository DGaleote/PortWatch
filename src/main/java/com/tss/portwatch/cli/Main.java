package com.tss.portwatch.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.PortWatchApp;
import com.tss.portwatch.core.PortWatchApp.OutputMode;
import com.tss.portwatch.core.collector.LinuxSsCollector;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.collector.MacOsLsofCollector;
import com.tss.portwatch.core.collector.WindowsPowerShellCollector;
import com.tss.portwatch.core.os.OsDetector;

public class Main {

    public static void main(String[] args) throws Exception {
        // Parse flags (prints usage on error)
        CliOptions opt = parseArgs(args);
        if (opt == null) return;

        ListenerCollector collector = wireCollector();
        PortWatchApp app = new PortWatchApp(new ObjectMapper(), collector);

        dispatch(app, opt);
    }

    // ----------------- Parsing -----------------

    private static CliOptions parseArgs(String[] args) {
        int snapshotCount = 0;
        int diffCount = 0;
        int outputCount = 0;

        // null => implicit output (combined behavior)
        OutputMode outputMode = null;

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

            System.err.println("Unknown flag: " + arg);
            printUsage();
            return null;
        }

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

        return new CliOptions(snapshotCount == 1, diffCount == 1, outputMode);
    }

    // ----------------- Collector wiring -----------------

    private static ListenerCollector wireCollector() throws Exception {
        var os = OsDetector.detect();
        return switch (os) {
            case WINDOWS -> new WindowsPowerShellCollector();
            case LINUX -> new LinuxSsCollector();
            case MAC -> new MacOsLsofCollector();
            default -> throw new IllegalStateException("Unsupported OS for now: " + os);
        };
    }

    // ----------------- Dispatch -----------------

    private static void dispatch(PortWatchApp app, CliOptions opt) throws Exception {
        if (opt.snapshot && opt.diff) {
            System.err.println("Cannot use --snapshot and --diff together.");
            printUsage();
            return;
        }

        if (!opt.snapshot && !opt.diff) {
            app.runDefault(opt.outputMode); // null => implicit
            return;
        }

        if (opt.snapshot) {
            app.runSnapshotOnly(opt.outputMode); // null => implicit
            return;
        }

        app.runDiffOnly(opt.outputMode); // null => implicit
    }

    // ----------------- Help -----------------

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  portwatch [--snapshot | --diff] [--output=console|file]");
        System.err.println("Notes:");
        System.err.println("  If no flags are provided, PortWatch runs in default mode.");
        System.err.println("  If --output is omitted, output is combined.");
    }

    // ----------------- Options DTO -----------------

    private static final class CliOptions {
        final boolean snapshot;
        final boolean diff;
        final OutputMode outputMode; // null => implicit

        private CliOptions(boolean snapshot, boolean diff, OutputMode outputMode) {
            this.snapshot = snapshot;
            this.diff = diff;
            this.outputMode = outputMode;
        }
    }
}
