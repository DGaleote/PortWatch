package com.tss.portwatch.core.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.exec.CommandRunner;
import com.tss.portwatch.core.model.ListeningSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * macOS implementation of ListenerCollector.
 *
 * Uses the native 'lsof' command to list TCP sockets in LISTEN state:
 *   lsof -nP -iTCP -sTCP:LISTEN
 *
 * Output is parsed into a List<ListeningSocket> and serialized back to JSON.
 *
 * Notes:
 *  - '-n' disables DNS resolution (faster and more deterministic)
 *  - '-P' shows numeric ports (avoids "http", "ssh", etc.)
 *  - We only target TCP LISTEN sockets (the ones relevant for "open ports")
 */
public class MacOsLsofCollector implements ListenerCollector {

    /**
     * Local ObjectMapper used to serialize the collected sockets to JSON.
     * (PortWatchApp also has a mapper; here we keep this collector self-contained.)
     */
    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String collectTcpListenersJson() throws Exception {
        // -n : no DNS
        // -P : numeric ports
        // -iTCP : TCP sockets
        // -sTCP:LISTEN : only LISTEN state
        var result = CommandRunner.run(List.of("lsof", "-nP", "-iTCP", "-sTCP:LISTEN"));

        // Non-zero exit code means lsof failed (missing binary, permissions, etc.)
        if (result.exitCode() != 0) {
            throw new RuntimeException("lsof failed: " + result.exitCode() + "\n" + result.stderr());
        }

        // Parse lsof stdout into domain objects and serialize as JSON array.
        List<ListeningSocket> rows = parseLsof(result.stdout());
        return OM.writeValueAsString(rows);
    }

    /**
     * Parses 'lsof' output lines into ListeningSocket entries.
     *
     * Expected lsof columns (typical):
     *   COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME
     *
     * We only need:
     *  - COMMAND (process name)
     *  - PID
     *  - NAME (host:port ... plus "(LISTEN)")
     */
    private List<ListeningSocket> parseLsof(String stdout) {
        List<ListeningSocket> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;

        String[] lines = stdout.split("\\R");
        boolean first = true;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Skip the header line:
            // "COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME"
            if (first) {
                first = false;
                if (line.toUpperCase().startsWith("COMMAND ")) continue;
            }

            // lsof aligns columns with spaces. The NAME field is at the end and, in LISTEN mode,
            // typically ends with a separate "(LISTEN)" token.
            //
            // Example:
            //   ControlCe  123 user  ...  TCP 127.0.0.1:631 (LISTEN)
            //
            // Splitting by whitespace is OK as long as we handle "(LISTEN)" properly.
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;

            String command = parts[0];
            Integer pid = safeParseInt(parts[1]);

            // IMPORTANT:
            // In macOS 'lsof' output, "(LISTEN)" is usually a separate token.
            // If we take the last token blindly, we'd capture "(LISTEN)" instead of "host:port"
            // and end up parsing NOTHING (empty snapshot).
            //
            // Therefore:
            //  - if the last token is "(LISTEN)", the host:port is the token right before it
            //  - otherwise, we fallback to the last token (defensive)
            String name;
            if ("(LISTEN)".equals(parts[parts.length - 1])) {
                name = parts[parts.length - 2];
            } else {
                name = parts[parts.length - 1];
            }

            // Convert the NAME field into a normalized host + numeric port.
            HostPort hp = parseHostPortFromName(name);
            if (hp == null) continue;

            // Build the ListeningSocket domain record.
            ListeningSocket row = new ListeningSocket();
            row.LocalAddress = hp.host;
            row.LocalPort = hp.port;
            row.ProcessName = command;
            row.ProcessId = pid;

            // Path on macOS could be obtained via ps/proc queries, but it's not required
            // for the current practice goal (port/process correlation).
            row.Path = null;

            out.add(row);
        }

        return out;
    }

    /**
     * Extracts host and port from the last part of the lsof NAME column.
     *
     * Typical inputs:
     *   "*:631"
     *   "127.0.0.1:8080"
     *   "[::1]:631"
     *
     * This method normalizes:
     *  - "*" -> "0.0.0.0"
     *  - "[::1]:631" -> "::1:631" (brackets removed before parsing)
     */
    private HostPort parseHostPortFromName(String name) {
        if (name == null || name.isBlank()) return null;

        String s = name.trim();

        // Remove trailing "(LISTEN)" if it somehow arrives attached to the string.
        // (Normally we already stripped it by choosing the token before "(LISTEN)".)
        if (s.endsWith("(LISTEN)")) {
            s = s.substring(0, s.length() - "(LISTEN)".length()).trim();
        }

        // Defensive: if a "->" appears, keep only the left side (local endpoint).
        // In theory, LISTEN entries should not contain "->", but it can appear
        // when commands/filters change in the future.
        int arrow = s.indexOf("->");
        if (arrow >= 0) s = s.substring(0, arrow).trim();

        // IPv6 is usually displayed in brackets: [::1]:631
        // Remove brackets so we can treat it uniformly.
        if (s.startsWith("[") && s.contains("]")) {
            s = s.substring(1, s.indexOf(']')) + s.substring(s.indexOf(']') + 1);
        }

        // Port is the substring after the last ':'
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
