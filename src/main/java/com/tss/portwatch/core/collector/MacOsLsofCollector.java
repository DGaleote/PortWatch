package com.tss.portwatch.core.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.portwatch.core.exec.CommandRunner;
import com.tss.portwatch.core.model.ListeningSocket;

import java.util.ArrayList;
import java.util.List;

public class MacOsLsofCollector implements ListenerCollector {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String collectTcpListenersJson() throws Exception {
        // -n: no DNS, -P: no service names (puertos numéricos)
        // -iTCP: sockets TCP, -sTCP:LISTEN: solo LISTEN
        var result = CommandRunner.run(List.of("lsof", "-nP", "-iTCP", "-sTCP:LISTEN"));

        if (result.exitCode() != 0) {
            throw new RuntimeException("lsof failed: " + result.exitCode() + "\n" + result.stderr());
        }

        List<ListeningSocket> rows = parseLsof(result.stdout());
        return OM.writeValueAsString(rows);
    }

    private List<ListeningSocket> parseLsof(String stdout) {
        List<ListeningSocket> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;

        String[] lines = stdout.split("\\R");
        boolean first = true;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Saltar cabecera típica: "COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME"
            if (first) {
                first = false;
                if (line.toUpperCase().startsWith("COMMAND ")) continue;
            }

            // lsof alinea por espacios; el "NAME" es el campo final y puede contener " (LISTEN)"
            // Nos quedamos con: COMMAND (0), PID (1), NAME (último).
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;

            String command = parts[0];
            Integer pid = safeParseInt(parts[1]);

//            String name = parts[parts.length - 1];


            // NAME son los últimos tokens. Normalmente acaba con "(LISTEN)" separado.
// Reconstruimos NAME tomando el token previo a "(LISTEN)" (y soportamos casos raros).
            String name;
            if ("(LISTEN)".equals(parts[parts.length - 1])) {
                name = parts[parts.length - 2];
            } else {
                name = parts[parts.length - 1];
            }



            // NAME puede venir como:
            // TCP *:631 (LISTEN)
            // TCP 127.0.0.1:8080 (LISTEN)
            // TCP [::1]:631 (LISTEN)
            // a veces "localhost:..."; con -n evitamos DNS, pero por si acaso.
            HostPort hp = parseHostPortFromName(name);
            if (hp == null) continue;

            ListeningSocket row = new ListeningSocket();
            row.LocalAddress = hp.host;
            row.LocalPort = hp.port;
            row.ProcessName = command;
            row.ProcessId = pid;
            row.Path = null; // en mac obtener path fiable es otro paso; para TSS, OK

            out.add(row);
        }

        return out;
    }

    private HostPort parseHostPortFromName(String name) {
        if (name == null || name.isBlank()) return null;

        String s = name.trim();

        // Quitar "(LISTEN)" si aparece pegado como último token
        if (s.endsWith("(LISTEN)")) {
            s = s.substring(0, s.length() - "(LISTEN)".length()).trim();
        }

        // lsof suele acabar en host:port, pero puede venir con flechas "->"
        // Ej: 127.0.0.1:8080->127.0.0.1:12345 (en conexiones)
        // Para LISTEN debería ser solo host:port, pero por seguridad cortamos por "->"
        int arrow = s.indexOf("->");
        if (arrow >= 0) s = s.substring(0, arrow).trim();

        // IPv6 entre []: [::1]:631
        if (s.startsWith("[") && s.contains("]")) {
            s = s.substring(1, s.indexOf(']')) + s.substring(s.indexOf(']') + 1);
        }

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
