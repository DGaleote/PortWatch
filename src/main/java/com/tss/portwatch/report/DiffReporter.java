package com.tss.portwatch.report;

import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.diff.SnapshotDiff;

public final class DiffReporter {

    private DiffReporter() {
        // utility class
    }

//    public static void printConsole(SnapshotDiff diff, int limitPerSection) {
//        printAdded(diff, limitPerSection);
//        printRemoved(diff, limitPerSection);
//        printChanged(diff, limitPerSection);
//    }

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


    private static void printAdded(SnapshotDiff diff, int limit) {
        int shown = 0;

        for (ListeningSocket s : diff.added()) {
            System.out.println("[+] NEW     " + formatSocket(s));
            shown++;
            if (shown >= limit) break;
        }

        int remaining = diff.added().size() - shown;
        if (remaining > 0) {
            System.out.println("    ... (" + remaining + " more added)");
        }
    }

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

//    private static void printChanged(SnapshotDiff diff, int limit) {
//        int shown = 0;
//
//        for (SnapshotDiff.Changed c : diff.changed()) {
//            ListeningSocket before = c.before();
//            ListeningSocket after = c.after();
//
//            System.out.println("[*] CHANGED " + after.LocalAddress + ":" + after.LocalPort
//                    + " -> " + safe(before.ProcessName) + " (PID " + before.ProcessId + ")"
//                    + " => " + safe(after.ProcessName) + " (PID " + after.ProcessId + ")");
//
//            shown++;
//            if (shown >= limit) break;
//        }
//
//        int remaining = diff.changed().size() - shown;
//        if (remaining > 0) {
//            System.out.println("    ... (" + remaining + " more changed)");
//        }
//    }

    private static void printChanged(SnapshotDiff diff, int limit) {
        int shown = 0;

        for (SnapshotDiff.Changed c : diff.changed()) {
            ListeningSocket before = c.before();
            ListeningSocket after = c.after();

            System.out.println("[*] CHANGED " + safe(after.LocalAddress) + ":" + after.LocalPort
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


    private static String formatSocket(ListeningSocket s) {
        String addrPort = safe(s.LocalAddress) + ":" + s.LocalPort;
        String proc = safe(s.ProcessName) + " (PID " + s.ProcessId + ")";
        String path = (s.Path == null || s.Path.isBlank()) ? "" : " (" + s.Path + ")";
        return addrPort + " -> " + proc + path;
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "?" : v;
    }
}
