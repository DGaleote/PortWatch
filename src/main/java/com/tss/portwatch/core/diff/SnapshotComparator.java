package com.tss.portwatch.core.diff;

import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.model.SocketKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the difference between two snapshots of listening sockets.
 *
 * The algorithm is based on a SocketKey (protocol + address + port), which
 * identifies a listening endpoint independently of the owning process.
 *
 * Using this key, we can detect:
 *  - new sockets (added)
 *  - removed sockets (removed)
 *  - sockets that still exist but changed ownership or metadata (changed)
 */
public final class SnapshotComparator {

    /**
     * Compares two snapshots and produces a SnapshotDiff.
     *
     * @param before snapshot from the previous run
     * @param after  snapshot from the current run
     * @return a diff describing added, removed and changed sockets
     */
    public static SnapshotDiff compare(List<ListeningSocket> before, List<ListeningSocket> after) {
        // Index both snapshots by SocketKey (address + port + protocol)
        Map<SocketKey, ListeningSocket> b = index(before);
        Map<SocketKey, ListeningSocket> a = index(after);

        // Added: keys that exist in "after" but not in "before"
        List<ListeningSocket> added = a.keySet().stream()
                .filter(k -> !b.containsKey(k))
                .map(a::get)
                .sorted(SnapshotComparator::byKey)
                .toList();

        // Removed: keys that existed in "before" but are no longer present in "after"
        List<ListeningSocket> removed = b.keySet().stream()
                .filter(k -> !a.containsKey(k))
                .map(b::get)
                .sorted(SnapshotComparator::byKey)
                .toList();

        // Changed: same socket key, but different process metadata
        List<SnapshotDiff.Changed> changed = a.keySet().stream()
                .filter(b::containsKey)
                .map(k -> new AbstractMap.SimpleEntry<>(b.get(k), a.get(k)))
                .filter(e -> isChanged(e.getKey(), e.getValue()))
                .map(e -> new SnapshotDiff.Changed(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(c -> keyOf(c.after()).toString()))
                .toList();

        return new SnapshotDiff(added, removed, changed);
    }

    /**
     * Builds a map indexed by SocketKey for fast lookup.
     *
     * If duplicates exist for the same key (rare but possible),
     * the last one wins.
     */
    private static Map<SocketKey, ListeningSocket> index(List<ListeningSocket> list) {
        return list.stream().collect(Collectors.toMap(
                SnapshotComparator::keyOf,
                s -> s,
                (x, y) -> y,
                LinkedHashMap::new
        ));
    }

    /**
     * Computes the SocketKey for a ListeningSocket.
     * Currently only TCP sockets are supported.
     */
    private static SocketKey keyOf(ListeningSocket s) {
        return SocketKey.tcp(s.LocalAddress, s.LocalPort);
    }

    /**
     * Determines whether a socket changed between two snapshots.
     *
     * The socket identity (address + port) is already known to be the same,
     * so we only compare process-related metadata.
     */
    private static boolean isChanged(ListeningSocket before, ListeningSocket after) {
        if (!Objects.equals(before.ProcessId, after.ProcessId)) return true;
        if (!Objects.equals(before.ProcessName, after.ProcessName)) return true;
        return !Objects.equals(before.Path, after.Path);
    }

    /**
     * Sort helper to produce stable, human-readable output order.
     */
    private static int byKey(ListeningSocket x, ListeningSocket y) {
        return keyOf(x).toString().compareTo(keyOf(y).toString());
    }

    /**
     * Utility class: no instances allowed.
     */
    private SnapshotComparator() {
    }
}
