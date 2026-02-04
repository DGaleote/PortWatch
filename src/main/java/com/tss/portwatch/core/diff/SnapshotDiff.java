package com.tss.portwatch.core.diff;

import com.tss.portwatch.core.model.ListeningSocket;

import java.util.List;

/**
 * Immutable representation of the difference between two snapshots.
 * <p>
 * A SnapshotDiff groups all detected changes into three categories:
 * <ul>
 *   <li><b>added</b>   – sockets that appear in the current snapshot but not in the previous one</li>
 *   <li><b>removed</b> – sockets that were present before but no longer exist</li>
 *   <li><b>changed</b> – sockets that still exist on the same address/port
 *       but whose owning process metadata has changed</li>
 * </ul>
 * <p>
 * This is a pure data structure:
 * it contains no comparison logic and performs no side effects.
 * All diff computation is done by {@link SnapshotComparator}.
 */
public record SnapshotDiff(
        List<ListeningSocket> added,
        List<ListeningSocket> removed,
        List<Changed> changed
) {

    /**
     * Represents a socket that exists in both snapshots
     * but whose associated metadata changed between them.
     * <p>
     * The identity of the socket (IP + port) is the same in both cases;
     * only ownership-related fields differ.
     * <p>
     * Typical example:
     * <ul>
     *   <li>same local address and port</li>
     *   <li>different process ID and/or process name</li>
     * </ul>
     *
     * @param before socket representation in the previous snapshot
     * @param after  socket representation in the current snapshot
     */
    public record Changed(ListeningSocket before, ListeningSocket after) {
    }
}
