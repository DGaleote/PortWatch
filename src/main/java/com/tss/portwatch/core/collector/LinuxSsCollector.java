package com.tss.portwatch.core.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.exec.CommandRunner;
import com.tss.portwatch.core.model.ListeningSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux implementation of ListenerCollector.
 *
 * Uses the native 'ss' tool to list TCP listening sockets:
 *   ss -lntpHn
 *
 * Where:
 *  -l : listening sockets
 *  -n : numeric addresses and ports
 *  -t : TCP only
 *  -p : process info (may be restricted without root)
 *  -H : no header
 */
public class LinuxSsCollector implements ListenerCollector {

    /**
     * Example ss output lines:
     *
     * LISTEN 0 4096 127.0.0.53%lo:53 0.0.0.0:* users:(("systemd-resolve",pid=725,fd=13))
     * LISTEN 0 4096 [::1]:631 [::]:* users:(("cupsd",pid=1234,fd=7))
     *
     * This regex extracts process name and pid from the "users:(())" block.
     */
    private static final Pattern USERS_PATTERN =
            Pattern.compile("users:\\(\\(\"(?<name>[^\"]+)\",pid=(?<pid>\\d+),fd=\\d+\\)\\)");

    /**
     * Local ObjectMapper used to serialize the collected sockets to JSON.
     */
    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String collectTcpListenersJson() throws Exception {
        // -l : listening
        // -n : numeric
        // -t : TCP
        // -p : include process info (may be restricted without permissions)
        // -H : no header
        var result = CommandRunner.run(List.of("ss", "-lntpHn"));

        // Non-zero exit code usually means a real execution problem
        // (missing binary, permissions, etc.)
        if (result.exitCode() != 0) {
            throw new RuntimeException(
                    "ss failed: " + result.exitCode() + "\n" + result.stderr()
            );
        }

        List<ListeningSocket> rows = parseSsOutput(result.stdout());
        return OM.writeValueAsString(rows);
    }

    /**
     * Parses the output of the 'ss' command into domain objects.
     */
    private List<ListeningSocket> parseSsOutput(String stdout) {

        List<ListeningSocket> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;

        String[] lines = stdout.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Typical structure:
            // STATE REC-Q SEND-Q LOCAL_ADDRESS:PORT PEER_ADDRESS:PORT ...
            String[] parts = line.split("\\s+");
            if (parts.length < 5) {
                // Unexpected or malformed line: ignore to avoid crashing
                continue;
            }

            // LOCAL_ADDRESS:PORT
            String local = parts[3];
            HostPort hp = parseHostPort(local);
            if (hp == null) continue;

            ListeningSocket row = new ListeningSocket();
            row.LocalAddress = hp.host;
            row.LocalPort = hp.port;

            // Extract process info if available (may be missing without permissions)
            Matcher m = USERS_PATTERN.matcher(line);
            if (m.find()) {
                row.ProcessName = m.group("name");
                row.ProcessId = safeParseInt(m.group("pid"));
            } else {
                row.ProcessName = null;
                row.ProcessId = null;
            }

            // Executable path is not reliably available without elevated privileges
            row.Path = null;

            out.add(row);
        }
        return out;
    }

    /**
     * Parses host and port from the LOCAL_ADDRESS field of ss output.
     *
     * Examples:
     *  127.0.0.53%lo:53
     *  127.0.0.1:631
     *  [::1]:631
     *  [::ffff:127.0.0.1]:63342
     *  *:22
     */
    private HostPort parseHostPort(String local) {
        if (local == null || local.isBlank()) return null;

        String s = local.trim();

        // Remove IPv6 brackets if present: [::1]:631 -> ::1:631
        if (s.startsWith("[") && s.contains("]")) {
            s = s.substring(1, s.indexOf(']')) + s.substring(s.indexOf(']') + 1);
        }

        // Port is after the LAST ':' (IPv6 contains multiple ':')
        int idx = s.lastIndexOf(':');
        if (idx <= 0 || idx >= s.length() - 1) return null;

        String host = s.substring(0, idx);
        if ("*".equals(host)) host = "0.0.0.0";

        String portStr = s.substring(idx + 1);

        // The host part may include a scope (e.g., %lo); we keep it for traceability.
        Integer port = safeParseInt(portStr);
        if (port == null) return null;

        HostPort hp = new HostPort();
        hp.host = host;
        hp.port = port;
        return hp;
    }

    /**
     * Safe integer parsing. Returns null on failure instead of throwing.
     */
    private Integer safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Simple DTO for a host + port pair.
     */
    private static class HostPort {
        String host;
        int port;
    }
}
