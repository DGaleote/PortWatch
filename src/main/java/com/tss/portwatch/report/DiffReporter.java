package com.tss.portwatch.report;

import com.tss.portwatch.core.diff.SnapshotDiff;
import com.tss.portwatch.core.model.ListeningSocket;

/**
 * Console renderer for {@link SnapshotDiff}.
 * <p>
 * This class is responsible exclusively for presenting diff information
 * in a human-readable format on standard output.
 * <p>
 * It does not:
 * - perform any diff computation,
 * - modify domain data,
 * - persist any information.
 * <p>
 * Its sole responsibility is formatting and output.
 */
public final class DiffReporter {

    /**
     * Utility class constructor.
     * Prevents instantiation.
     */
    private DiffReporter() {
        // Utility class: no instances allowed.
    }

    /**
     * Prints a diff summary to the console.
     * <p>
     * Results are grouped by category (added, removed, changed) and
     * each section is limited to a maximum number of entries to avoid
     * excessive console output.
     *
     * @param diff            the computed snapshot diff
     * @param limitPerSection maximum number of entries printed per section
     */
    public static void printConsole(SnapshotDiff diff, int limitPerSection) {

        if (!diff.added().isEmpty()) {
            System.out.println("\n=== ADDED ===");
            printAdded(diff, limitPerSection);
        }

        if (!diff.removed().isEmpty()) {
            System.out.println("\n=== REMOVED ===");
            printRemoved(diff, limitPerSection);
        }

        if (!diff.changed().isEmpty()) {
            System.out.println("\n=== CHANGED ===");
            printChanged(diff, limitPerSection);
        }
    }

    /**
     * Prints sockets that appeared since the previous snapshot.
     */
    private static void printAdded(SnapshotDiff diff, int limit) {
        int shown = 0;

        for (ListeningSocket s : diff.added()) {
            System.out.println("[+] NEW     " + formatSocket(s));
            shown++;
            if (shown >= limit) break;
        }

        // Inform if not all entries were printed.
        int remaining = diff.added().size() - shown;
        if (remaining > 0) {
            System.out.println("    ... (" + remaining + " more added)");
        }
    }

    /**
     * Prints sockets that disappeared since the previous snapshot.
     */
    private static void printRemoved(SnapshotDiff diff, int limit) {
        int shown = 0;

        for (ListeningSocket s : diff.removed()) {
            System.out.println("[-] CLOSED  " + formatSocket(s));
            shown++;
            if (shown >= limit) break;
        }

        int remaining = diff.removed().size() - shown;
        if (remaining > 0) {
            System.out.println("    ... (" + remaining + " more removed)");
        }
    }

    /**
     * Prints sockets that remained bound to the same address and port
     * but changed their owning process.
     */
    private static void printChanged(SnapshotDiff diff, int limit) {
        int shown = 0;

        for (SnapshotDiff.Changed c : diff.changed()) {
            ListeningSocket before = c.before();
            ListeningSocket after = c.after();

            System.out.println("[*] CHANGED "
                    + safe(after.LocalAddress) + ":" + after.LocalPort
                    + " " + safe(before.ProcessName) + " (PID " + before.ProcessId + ")"
                    + " => " + safe(after.ProcessName) + " (PID " + after.ProcessId + ")");

            shown++;
            if (shown >= limit) break;
        }

        int remaining = diff.changed().size() - shown;
        if (remaining > 0) {
            System.out.println("    ... (" + remaining + " more changed)");
        }
    }

    /**
     * Formats a {@link ListeningSocket} into a compact single-line string
     * suitable for console output.
     */
    private static String formatSocket(ListeningSocket s) {
        String addrPort = safe(s.LocalAddress) + ":" + s.LocalPort;
        String proc = safe(s.ProcessName) + " (PID " + s.ProcessId + ")";
        String path = (s.Path == null || s.Path.isBlank()) ? "" : " (" + s.Path + ")";
        return addrPort + " -> " + proc + path;
    }

    /**
     * Replaces null or blank values with a placeholder to keep
     * console output readable and consistent.
     */
    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "?" : v;
    }
}
