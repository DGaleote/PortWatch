package com.tss.portwatch.core.diff;

import com.tss.portwatch.core.model.ListeningSocket;

import java.util.List;

public record SnapshotDiff(
        List<ListeningSocket> added,
        List<ListeningSocket> removed,
        List<Changed> changed
) {
    public record Changed(ListeningSocket before, ListeningSocket after) {}
}
