package com.tss.portwatch.report;

import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.diff.SnapshotDiff;

/**
 * Renders a SnapshotDiff to the console in a human-readable format.
 *
 * This class is responsible only for presentation.
 * It does not perform any diff logic and does not modify data.
 */
public final class DiffReporter {

    private DiffReporter() {
        // Utility class: no instances allowed.
    }

    /**
     * Prints the diff to the console, grouping results by category
     * (added, removed, changed) and limiting the number of entries
     * shown per section to keep output readable.
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

        // Inform the user if not all entries were printed.
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
     * Prints sockets that stayed on the same address:port
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
     * Formats a ListeningSocket into a compact one-line representation.
     */
    private static String formatSocket(ListeningSocket s) {
        String addrPort = safe(s.LocalAddress) + ":" + s.LocalPort;
        String proc = safe(s.ProcessName) + " (PID " + s.ProcessId + ")";
        String path = (s.Path == null || s.Path.isBlank()) ? "" : " (" + s.Path + ")";
        return addrPort + " -> " + proc + path;
    }

    /**
     * Replaces null or blank strings with "?" to keep console output readable.
     */
    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "?" : v;
    }
}
