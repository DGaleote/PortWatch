package com.tss.portwatch.core.diff;

import com.tss.portwatch.core.model.ListeningSocket;

import java.util.List;

/**
 * Immutable representation of the difference between two snapshots.
 *
 * It contains:
 *  - sockets that appeared (added)
 *  - sockets that disappeared (removed)
 *  - sockets that still exist but changed (typically the owning process)
 */
public record SnapshotDiff(
        List<ListeningSocket> added,
        List<ListeningSocket> removed,
        List<Changed> changed
) {

    /**
     * Represents a socket that exists in both snapshots
     * but whose associated metadata changed.
     *
     * Typical example:
     *  - same IP:PORT
     *  - different ProcessId or ProcessName
     */
    public record Changed(ListeningSocket before, ListeningSocket after) {}
}
