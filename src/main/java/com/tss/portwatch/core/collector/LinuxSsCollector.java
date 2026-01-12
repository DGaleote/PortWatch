package com.tss.portwatch.core.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.exec.CommandRunner;
import com.tss.portwatch.core.model.ListeningSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinuxSsCollector implements ListenerCollector {

    // Ejemplo ss:
    // LISTEN 0 4096 127.0.0.53%lo:53 0.0.0.0:* users:(("systemd-resolve",pid=725,fd=13))
    // LISTEN 0 4096 [::1]:631 [::]:* users:(("cupsd",pid=1234,fd=7))
    private static final Pattern USERS_PATTERN =
            Pattern.compile("users:\\(\\(\"(?<name>[^\"]+)\",pid=(?<pid>\\d+),fd=\\d+\\)\\)");

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String collectTcpListenersJson() throws Exception {
        // -l listening, -n numeric, -t tcp, -p process (capado sin permisos), -H sin header
        var result = CommandRunner.run(List.of("ss", "-lntpHn"));

        // Si ss devuelve warning/exit != 0, preferimos tratar stderr como diagnóstico
        if (result.exitCode() != 0) {
            throw new RuntimeException(
                    "ss failed: " + result.exitCode() + "\n" + result.stderr()
            );
        }

        List<ListeningSocket> rows = parseSsOutput(result.stdout());

        return OM.writeValueAsString(rows);
    }

    private List<ListeningSocket> parseSsOutput(String stdout) {

        List<ListeningSocket> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;

        String[] lines = stdout.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Partimos por espacios pero "users:((" no nos molesta porque lo parseamos aparte.
            // Estructura típica:
            // STATE REC-Q SEND-Q LOCAL_ADDRESS:PORT PEER_ADDRESS:PORT ...
            String[] parts = line.split("\\s+");
            if (parts.length < 5) {
                // línea rara -> la ignoramos (no queremos petar por un caso marginal)
                continue;
            }

            String local = parts[3]; // local address:port (o [::1]:631)
            HostPort hp = parseHostPort(local);
            if (hp == null) continue;

            ListeningSocket row = new ListeningSocket();
            row.LocalAddress = hp.host;
            row.LocalPort = hp.port;

            // Proceso (si lo hay y si tenemos permisos)
            Matcher m = USERS_PATTERN.matcher(line);
            if (m.find()) {
                row.ProcessName = m.group("name");
                row.ProcessId = safeParseInt(m.group("pid"));
            } else {
                row.ProcessName = null;
                row.ProcessId = null;
            }

            // Ruta ejecutable: sin sudo normalmente no puedes resolverla de forma fiable -> null
            row.Path = null;

            out.add(row);
        }
        return out;
    }

    private HostPort parseHostPort(String local) {
        // local puede ser:
        // 127.0.0.53%lo:53
        // 127.0.0.1:631
        // [::1]:631
        // [::ffff:127.0.0.1]:63342
        // *:22 (a veces)
        if (local == null || local.isBlank()) return null;

        String s = local.trim();

        // Quita corchetes si viene IPv6 entre []
        // Ej: [::1]:631  -> ::1:631
        if (s.startsWith("[") && s.contains("]")) {
            s = s.substring(1, s.indexOf(']')) + s.substring(s.indexOf(']') + 1);
        }

        // Ahora buscamos el ÚLTIMO ':' para separar puerto (IPv6 tiene muchos ':')
        int idx = s.lastIndexOf(':');
        if (idx <= 0 || idx >= s.length() - 1) return null;

        String host = s.substring(0, idx);
        if ("*".equals(host)) host = "0.0.0.0";

        String portStr = s.substring(idx + 1);

        // host puede venir con %lo (scope). Lo dejamos tal cual para trazabilidad.
        Integer port = safeParseInt(portStr);
        if (port == null) return null;

        HostPort hp = new HostPort();
        hp.host = host;
        hp.port = port;
        return hp;
    }

    private Integer safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static class HostPort {
        String host;
        int port;
    }


}