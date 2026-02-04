package com.tss.portwatch.core.model;

import java.util.List;

/**
 * Immutable representation of a snapshot persisted to disk.
 * <p>
 * A {@code SnapshotFile} models the exact structure written as JSON
 * when PortWatch captures the current listening sockets state.
 * <p>
 * It contains:
 * <ul>
 *   <li>metadata describing when, where and on which machine the snapshot was taken</li>
 *   <li>the list of listening sockets observed at that moment</li>
 * </ul>
 * <p>
 * This class is used both for:
 * <ul>
 *   <li>persisting snapshots to disk</li>
 *   <li>loading previous snapshots to compute diffs</li>
 * </ul>
 * <p>
 * The class is intentionally immutable to ensure snapshot integrity.
 */
public record SnapshotFile(

        // Metadata associated with this snapshot (machine, OS, timestamp).
        PortWatchMetadata metadata,

        // List of listening sockets captured during the snapshot.
        List<ListeningSocket> sockets

) {
}
