package com.tss.portwatch.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.PortWatchApp;
import com.tss.portwatch.core.collector.LinuxSsCollector;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.collector.MacOsLsofCollector;
import com.tss.portwatch.core.collector.WindowsPowerShellCollector;
import com.tss.portwatch.core.os.OsDetector;

/**
 * Application entry point.
 *
 * This class is intentionally thin: its only responsibility is to
 * detect the current operating system and wire the correct
 * OS-specific ListenerCollector implementation into the core
 * PortWatchApp.
 *
 * All business logic (snapshot, diff, reporting) lives outside
 * this class.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // Detect the current operating system (WINDOWS, LINUX, MAC, etc.)
        // This is done through our own abstraction instead of relying
        // directly on JVM properties to keep the core decoupled.
        var os = OsDetector.detect();

        // Select the appropriate collector strategy for the detected OS.
        // Each implementation knows how to list listening TCP sockets
        // using the native tools of that platform.
        ListenerCollector collector = switch (os) {
            case WINDOWS -> new WindowsPowerShellCollector();
            case LINUX -> new LinuxSsCollector();
            case MAC -> new MacOsLsofCollector();

            // Fail fast on unsupported systems instead of producing
            // partial or misleading results.
            default -> throw new IllegalStateException("Unsupported OS for now: " + os);
        };

        // Create the core application, injecting:
        //  - a JSON mapper (used to serialize snapshots)
        //  - the OS-specific collector
        // Then run a single snapshot/diff cycle.
        new PortWatchApp(new ObjectMapper(), collector).runOnce();
    }
}
