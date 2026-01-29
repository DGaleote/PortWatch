package com.tss.portwatch.core.model;

import java.util.List;

public record SnapshotFile(
        PortWatchMetadata metadata,
        List<ListeningSocket> sockets
) {}
