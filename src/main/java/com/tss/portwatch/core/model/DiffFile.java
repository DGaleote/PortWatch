package com.tss.portwatch.core.model;

import com.tss.portwatch.core.diff.SnapshotDiff;

/**
 * Immutable container representing a persisted diff result.
 * <p>
 * A DiffFile combines:
 * <ul>
 *   <li>{@link PortWatchMetadata} describing when, where and on which machine
 *       the diff was generated</li>
 *   <li>{@link SnapshotDiff} containing the actual structural differences
 *       between two snapshots</li>
 * </ul>
 * <p>
 * This record represents exactly what is written to disk as a diff JSON file
 * and what is later consumed by report generators (Markdown / HTML).
 * <p>
 * The separation between DiffFile and SnapshotDiff is intentional:
 * SnapshotDiff models the pure domain difference, while DiffFile adds
 * execution context and persistence metadata.
 */
public record DiffFile(
        PortWatchMetadata metadata,
        SnapshotDiff diff
) {
}
