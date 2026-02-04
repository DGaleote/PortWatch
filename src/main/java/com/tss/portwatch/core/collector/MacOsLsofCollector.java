package com.tss.portwatch.core.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.exec.CommandRunner;
import com.tss.portwatch.core.model.ListeningSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * macOS implementation of {@link ListenerCollector}.
 * <p>
 * Uses the native {@code lsof} command to list TCP sockets in LISTEN state:
 * {@code lsof -nP -iTCP -sTCP:LISTEN}
 * <p>
 * The command output is parsed into {@link ListeningSocket} entries and then serialized
 * back to JSON so the rest of the application can treat all collectors uniformly.
 * <p>
 * Notes:
 * <ul>
 *   <li>{@code -n} disables DNS resolution (faster and deterministic)</li>
 *   <li>{@code -P} forces numeric ports (avoids "http", "ssh", etc.)</li>
 *   <li>{@code -iTCP -sTCP:LISTEN} filters only TCP LISTEN sockets</li>
 * </ul>
 */
public class MacOsLsofCollector implements ListenerCollector {

    /**
     * Local ObjectMapper used to serialize the collected sockets to JSON.
     * This keeps the collector self-contained.
     */
    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * Executes {@code lsof} and returns a JSON array with the detected listeners.
     *
     * @return JSON array where each element matches the ListeningSocket structure
     * @throws Exception if {@code lsof} fails or returns a non-zero exit code
     */
    @Override
    public String collectTcpListenersJson() throws Exception {
        /*
         * -n : no DNS
         * -P : numeric ports
         * -iTCP : TCP sockets
         * -sTCP:LISTEN : only LISTEN state
         */
        var result = CommandRunner.run(List.of("lsof", "-nP", "-iTCP", "-sTCP:LISTEN"));

        // Non-zero exit code indicates the command failed (missing binary, permissions, etc.).
        if (result.exitCode() != 0) {
            throw new RuntimeException("lsof failed: " + result.exitCode() + "\n" + result.stderr());
        }

        // Parse lsof stdout into domain objects and serialize as JSON array.
        List<ListeningSocket> rows = parseLsof(result.stdout());
        return OM.writeValueAsString(rows);
    }

    /**
     * Parses {@code lsof} output lines into {@link ListeningSocket} entries.
     * <p>
     * Expected lsof columns (typical):
     * {@code COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME}
     * <p>
     * Fields used:
     * <ul>
     *   <li>COMMAND (process name)</li>
     *   <li>PID</li>
     *   <li>NAME (host:port plus an optional "(LISTEN)" token)</li>
     * </ul>
     * <p>
     * The parser is tolerant to spacing differences by splitting on whitespace and
     * extracting the last tokens that represent the NAME field.
     */
    private List<ListeningSocket> parseLsof(String stdout) {
        List<ListeningSocket> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;

        String[] lines = stdout.split("\\R");
        boolean first = true;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Skip header line: "COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME"
            if (first) {
                first = false;
                if (line.toUpperCase().startsWith("COMMAND ")) continue;
            }

            // Example:
            //   ControlCe  123 user  ...  TCP 127.0.0.1:631 (LISTEN)
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;

            String command = parts[0];
            Integer pid = safeParseInt(parts[1]);

            /*
             * In macOS lsof output, "(LISTEN)" is typically a separate token.
             * If we took the last token blindly, we'd capture "(LISTEN)" instead of "host:port".
             *
             * Therefore:
             *  - if last token is "(LISTEN)", host:port is the token before it
             *  - otherwise, fallback to the last token (defensive)
             */
            String name;
            if ("(LISTEN)".equals(parts[parts.length - 1])) {
                name = parts[parts.length - 2];
            } else {
                name = parts[parts.length - 1];
            }

            HostPort hp = parseHostPortFromName(name);
            if (hp == null) continue;

            ListeningSocket row = new ListeningSocket();
            row.LocalAddress = hp.host;
            row.LocalPort = hp.port;
            row.ProcessName = command;
            row.ProcessId = pid;

            // Path is not collected on macOS in this implementation.
            row.Path = null;

            out.add(row);
        }

        return out;
    }

    /**
     * Extracts host and port from the last part of the lsof NAME column.
     * <p>
     * Typical inputs:
     * <ul>
     *   <li>{@code *:631}</li>
     *   <li>{@code 127.0.0.1:8080}</li>
     *   <li>{@code [::1]:631}</li>
     * </ul>
     * <p>
     * Normalizations:
     * <ul>
     *   <li>{@code *} becomes {@code 0.0.0.0} (all interfaces)</li>
     *   <li>IPv6 brackets are removed before parsing</li>
     * </ul>
     *
     * @param name extracted NAME token from lsof output
     * @return host/port pair, or null if parsing fails
     */
    private HostPort parseHostPortFromName(String name) {
        if (name == null || name.isBlank()) return null;

        String s = name.trim();

        // Defensive: remove trailing "(LISTEN)" if it arrives attached.
        if (s.endsWith("(LISTEN)")) {
            s = s.substring(0, s.length() - "(LISTEN)".length()).trim();
        }

        // Defensive: if "->" appears, keep only the local endpoint.
        int arrow = s.indexOf("->");
        if (arrow >= 0) s = s.substring(0, arrow).trim();

        // IPv6 is usually displayed as [::1]:631. Remove brackets.
        if (s.startsWith("[") && s.contains("]")) {
            int end = s.indexOf(']');
            s = s.substring(1, end) + s.substring(end + 1);
        }

        // Port is substring after the last ':'.
        int idx = s.lastIndexOf(':');
        if (idx <= 0 || idx >= s.length() - 1) return null;

        String host = s.substring(0, idx);
        if ("*".equals(host)) host = "0.0.0.0";

        String portStr = s.substring(idx + 1);
        Integer port = safeParseInt(portStr);
        if (port == null) return null;

        HostPort hp = new HostPort();
        hp.host = host;
        hp.port = port;
        return hp;
    }

    /**
     * Parses an integer safely. Returns null if parsing fails.
     * Used for PID and port parsing to keep the collector tolerant to unexpected formats.
     */
    private Integer safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Simple DTO holding a host and port pair.
     */
    private static class HostPort {
        String host;
        int port;
    }
}
