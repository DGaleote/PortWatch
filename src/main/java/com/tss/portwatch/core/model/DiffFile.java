package com.tss.portwatch.core.model;

import com.tss.portwatch.core.diff.SnapshotDiff;

public record DiffFile(
        PortWatchMetadata metadata,
        SnapshotDiff diff
) {}
