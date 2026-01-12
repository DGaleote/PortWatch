package com.tss.portwatch.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.PortWatchApp;
import com.tss.portwatch.core.collector.LinuxSsCollector;
import com.tss.portwatch.core.collector.ListenerCollector;
import com.tss.portwatch.core.collector.MacOsLsofCollector;
import com.tss.portwatch.core.collector.WindowsPowerShellCollector;
import com.tss.portwatch.core.os.OsDetector;

public class Main {

    public static void main(String[] args) throws Exception {
        var os = OsDetector.detect();

        ListenerCollector collector = switch (os) {
            case WINDOWS -> new WindowsPowerShellCollector();
            case LINUX -> new LinuxSsCollector();
            case MAC -> new MacOsLsofCollector();
            default -> throw new IllegalStateException("Unsupported OS for now: " + os);
        };

        new PortWatchApp(new ObjectMapper(), collector).runOnce();
    }
}
