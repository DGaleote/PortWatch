package com.tss.portwatch.core.diff;

import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.model.SocketKey;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Computes the difference between two snapshots of listening sockets.
 * <p>
 * The diff algorithm is based on {@link SocketKey} (protocol + address + port),
 * which identifies a listening endpoint independently of the owning process.
 * <p>
 * Using this key, the comparator detects:
 * <ul>
 *   <li><b>added</b>   – endpoints that appear in the current snapshot but not in the previous one</li>
 *   <li><b>removed</b> – endpoints that were present before but are no longer present</li>
 *   <li><b>changed</b> – endpoints that still exist on the same address/port, but whose process metadata changed</li>
 * </ul>
 * <p>
 * This class performs only comparison logic. It does not access the filesystem and does not render output.
 */
public final class SnapshotComparator {

    /**
     * Compares two snapshots and produces a {@link SnapshotDiff}.
     * <p>
     * Both snapshots are first indexed by {@link SocketKey} to allow O(1) lookups when computing:
     * added/removed endpoints and ownership changes.
     *
     * @param before snapshot from the previous run (may be empty, but not null)
     * @param after  snapshot from the current run (may be empty, but not null)
     * @return a diff describing added, removed and changed sockets
     */
    public static SnapshotDiff compare(List<ListeningSocket> before, List<ListeningSocket> after) {
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

        // Changed: same key, different ownership/process metadata
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
     * Builds a map indexed by {@link SocketKey} for fast lookup.
     * <p>
     * If duplicates exist for the same key (rare but possible depending on source data),
     * the last entry wins.
     *
     * @param list snapshot sockets
     * @return map keyed by endpoint identity (protocol + address + port)
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
     * Computes the {@link SocketKey} for a {@link ListeningSocket}.
     * <p>
     * Currently, PortWatch handles TCP listeners, so the protocol is fixed to TCP.
     *
     * @param s socket model entry
     * @return key representing the endpoint identity
     */
    private static SocketKey keyOf(ListeningSocket s) {
        return SocketKey.tcp(s.LocalAddress, s.LocalPort);
    }

    /**
     * Determines whether a socket changed between two snapshots.
     * <p>
     * The endpoint identity (address + port) is already known to be the same,
     * so only process-related fields are compared.
     *
     * @param before socket representation in previous snapshot
     * @param after  socket representation in current snapshot
     * @return true if ownership/process metadata differs
     */
    private static boolean isChanged(ListeningSocket before, ListeningSocket after) {
        if (!Objects.equals(before.ProcessId, after.ProcessId)) return true;
        if (!Objects.equals(before.ProcessName, after.ProcessName)) return true;
        return !Objects.equals(before.Path, after.Path);
    }

    /**
     * Sort helper to produce stable, human-readable output order.
     * <p>
     * Ordering is based on the textual representation of the {@link SocketKey}.
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
