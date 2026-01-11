package com.tss.portwatch.core.diff;

import com.tss.portwatch.core.model.ListeningSocket;
import com.tss.portwatch.core.model.SocketKey;

import java.util.*;
import java.util.stream.Collectors;

public final class SnapshotComparator {

    public static SnapshotDiff compare(List<ListeningSocket> before, List<ListeningSocket> after) {
        Map<SocketKey, ListeningSocket> b = index(before);
        Map<SocketKey, ListeningSocket> a = index(after);

        // Added: keys in after but not in before
        List<ListeningSocket> added = a.keySet().stream()
                .filter(k -> !b.containsKey(k))
                .map(a::get)
                .sorted(SnapshotComparator::byKey)
                .toList();

        // Removed: keys in before but not in after
        List<ListeningSocket> removed = b.keySet().stream()
                .filter(k -> !a.containsKey(k))
                .map(b::get)
                .sorted(SnapshotComparator::byKey)
                .toList();

        // Changed: same key but different processId/name/path
        List<SnapshotDiff.Changed> changed = a.keySet().stream()
                .filter(b::containsKey)
                .map(k -> new AbstractMap.SimpleEntry<>(b.get(k), a.get(k)))
                .filter(e -> isChanged(e.getKey(), e.getValue()))
                .map(e -> new SnapshotDiff.Changed(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(c -> keyOf(c.after()).toString()))
                .toList();

        return new SnapshotDiff(added, removed, changed);
    }

    private static Map<SocketKey, ListeningSocket> index(List<ListeningSocket> list) {
        // si hubiese duplicados por address/port (raro), nos quedamos con el último
        return list.stream().collect(Collectors.toMap(
                SnapshotComparator::keyOf,
                s -> s,
                (x, y) -> y,
                LinkedHashMap::new
        ));
    }

    private static SocketKey keyOf(ListeningSocket s) {
        return SocketKey.tcp(s.LocalAddress, s.LocalPort);
    }

    private static boolean isChanged(ListeningSocket before, ListeningSocket after) {
        if (!Objects.equals(before.ProcessId, after.ProcessId)) return true;
        if (!Objects.equals(before.ProcessName, after.ProcessName)) return true;
        return !Objects.equals(before.Path, after.Path);
    }


    private static int byKey(ListeningSocket x, ListeningSocket y) {
        return keyOf(x).toString().compareTo(keyOf(y).toString());
    }

    private SnapshotComparator() {
    }
}
