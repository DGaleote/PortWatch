package com.tss.portwatch.report;

import com.tss.portwatch.core.diff.SnapshotDiff;
import com.tss.portwatch.core.model.DiffFile;
import com.tss.portwatch.core.model.ListeningSocket;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a human-readable HTML report from a {@link DiffFile}.
 * <p>
 * Output structure:
 * - Title and metadata (machine id, OS, timestamp)
 * - Narrative sections (opened/closed/changed)
 * - Summary table (opened -> closed -> changed, sorted by port)
 * <p>
 * Notes:
 * - The HTML is self-contained (inline CSS) to simplify portability.
 * - User-controlled values are escaped to avoid malformed HTML.
 * - Scope translation is consistent with the Markdown report generator.
 */
public final class DiffHtmlReportGenerator {

    /**
     * Builds a complete HTML document for a given diff payload.
     *
     * @param diffFile Diff payload containing metadata and the computed diff.
     * @return A full HTML document as a String.
     */
    public static String generateHtml(DiffFile diffFile) {
        SnapshotDiff diff = diffFile.diff();

        String machineId = safe(diffFile.metadata().machineId());
        String os = safe(diffFile.metadata().os());
        String ts = safe(diffFile.metadata().timestamp());
        String humanDate = formatTsHuman(ts);

        StringBuilder html = new StringBuilder(16_384);

        // Document header + CSS
        html.append("""
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>PortWatch - Informe de cambios</title>
                  <style>
                    :root {
                      --bg: #0f1115;
                      --panel: #151922;
                      --text: #e7eaf0;
                      --muted: #aab2c0;
                      --line: #2a3140;
                      --good: #30d158;
                      --bad: #ff453a;
                      --chg: #64d2ff;
                      --mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
                      --sans: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif;
                    }
                    body { margin:0; font-family: var(--sans); background: var(--bg); color: var(--text); }
                    .wrap { max-width: 980px; margin: 0 auto; padding: 32px 20px; }
                    h1 { margin: 0 0 12px; font-size: 34px; letter-spacing: .2px; }
                    h2 { margin: 26px 0 10px; font-size: 22px; }
                    h3 { margin: 18px 0 8px; font-size: 18px; }
                    .meta { margin: 0; padding-left: 18px; color: var(--muted); }
                    .meta b { color: var(--text); }
                    .card { background: var(--panel); border: 1px solid var(--line); border-radius: 14px; padding: 16px 16px; margin-top: 14px; }
                    .sep { border: none; border-top: 1px solid var(--line); margin: 18px 0; }
                    ul { margin: 8px 0 0; padding-left: 22px; }
                    li { margin: 6px 0; color: var(--text); }
                    .tag { display:inline-flex; align-items:center; gap:10px; font-weight:700; }
                    .dot { width: 12px; height: 12px; border-radius: 50%; display:inline-block; }
                    .dot.good { background: var(--good); }
                    .dot.bad { background: var(--bad); }
                    .dot.chg { background: var(--chg); }
                    table { width: 100%; border-collapse: collapse; margin-top: 12px; }
                    th, td { border: 1px solid var(--line); padding: 10px 10px; font-size: 14px; }
                    th { background: #121622; text-align: left; }
                    td.mono { font-family: var(--mono); }
                    .note { margin-top: 14px; color: var(--muted); font-style: italic; }
                    code { font-family: var(--mono); }
                  </style>
                </head>
                <body>
                <div class="wrap">
                """);

        // Title + metadata
        html.append("<h1>PortWatch — Informe de cambios</h1>\n");
        html.append("<ul class=\"meta\">");
        html.append("<li><b>Equipo:</b> ").append(escapeHtml(machineId)).append("</li>");
        html.append("<li><b>Sistema operativo:</b> ").append(escapeHtml(os)).append("</li>");
        html.append("<li><b>Fecha del análisis:</b> ").append(escapeHtml(humanDate)).append("</li>");
        html.append("</ul>\n");

        // Narrative section
        html.append("<div class=\"card\">");
        html.append("<h2>Eventos detectados</h2>");

        appendOpened(html, safeList(diff.added()));
        appendClosed(html, safeList(diff.removed()));
        appendChanged(html, safeList(diff.changed()));

        html.append("</div>\n");

        // Summary card
        html.append("<div class=\"card\">");
        html.append("<h2>Resumen de eventos</h2>");
        appendSummaryTable(html, diff);
        html.append("<p class=\"note\">Este informe refleja los cambios detectados entre el análisis anterior y el análisis actual indicados en la cabecera.</p>");
        html.append("</div>\n");

        html.append("</div></body></html>");
        return html.toString();
    }

    // ---------------------------
    // Sections (narrative lists)
    // ---------------------------

    /**
     * Appends the "opened ports" narrative section.
     */
    private static void appendOpened(StringBuilder html, List<ListeningSocket> added) {
        html.append("<h3><span class=\"tag\"><span class=\"dot good\"></span>Puertos abiertos</span></h3>");
        if (added.isEmpty()) {
            html.append("<ul><li>(Sin cambios)</li></ul>");
            return;
        }
        html.append("<ul>");
        for (ListeningSocket s : added) {
            html.append("<li>")
                    .append(escapeHtml(openedPort(
                            s.LocalPort,
                            translateScope(s.LocalAddress),
                            normalizeProgram(s.ProcessName)
                    )))
                    .append("</li>");
        }
        html.append("</ul>");
    }

    /**
     * Appends the "closed ports" narrative section.
     */
    private static void appendClosed(StringBuilder html, List<ListeningSocket> removed) {
        html.append("<h3><span class=\"tag\"><span class=\"dot bad\"></span>Puertos cerrados</span></h3>");
        if (removed.isEmpty()) {
            html.append("<ul><li>(Sin cambios)</li></ul>");
            return;
        }
        html.append("<ul>");
        for (ListeningSocket s : removed) {
            html.append("<li>")
                    .append(escapeHtml(closedPort(
                            s.LocalPort,
                            translateScope(s.LocalAddress),
                            normalizeProgram(s.ProcessName)
                    )))
                    .append("</li>");
        }
        html.append("</ul>");
    }

    /**
     * Appends the "changed ports" narrative section.
     * <p>
     * If port is missing, a generic message is emitted.
     * If program is unknown, a reduced message variant is emitted.
     */
    private static void appendChanged(StringBuilder html, List<SnapshotDiff.Changed> changed) {
        html.append("<h3><span class=\"tag\"><span class=\"dot chg\"></span>Cambios detectados</span></h3>");
        if (changed.isEmpty()) {
            html.append("<ul><li>(Sin cambios)</li></ul>");
            return;
        }
        html.append("<ul>");
        for (SnapshotDiff.Changed c : changed) {
            ListeningSocket after = c.after();

            Integer port = after != null ? after.LocalPort : null;
            String scope = after != null ? translateScope(after.LocalAddress) : "con alcance desconocido";
            String program = after != null ? normalizeProgram(after.ProcessName) : "programa desconocido";

            if (port == null) {
                html.append("<li>Se detectó un cambio de proceso asociado en un puerto no identificable.</li>");
                continue;
            }

            String line = isUnknownProgram(program)
                    ? changedPortNoProgram(port, scope)
                    : changedPort(port, scope, program);

            html.append("<li>").append(escapeHtml(line)).append("</li>");
        }
        html.append("</ul>");
    }

    // ---------------------------
    // Summary table
    // ---------------------------

    /**
     * Appends the summary table.
     * Row ordering:
     * - Opened (ascending port)
     * - Closed (ascending port)
     * - Changed (ascending port)
     */
    private static void appendSummaryTable(StringBuilder html, SnapshotDiff diff) {
        boolean hasEvents =
                !safeList(diff.added()).isEmpty()
                        || !safeList(diff.removed()).isEmpty()
                        || !safeList(diff.changed()).isEmpty();

        html.append("<table>");
        html.append("<thead><tr>")
                .append("<th>Tipo</th><th>Puerto</th><th>Alcance</th><th>Programa</th>")
                .append("</tr></thead><tbody>");

        if (!hasEvents) {
            html.append("<tr>")
                    .append("<td>Sin cambios</td><td class=\"mono\">–</td><td>–</td><td>–</td>")
                    .append("</tr>");
            html.append("</tbody></table>");
            return;
        }

        List<ListeningSocket> added = new ArrayList<>(safeList(diff.added()));
        added.sort(Comparator.comparingInt(s -> s.LocalPort == null ? Integer.MAX_VALUE : s.LocalPort));
        for (ListeningSocket s : added) {
            row(html, "Abierto", s.LocalPort, translateScope(s.LocalAddress), normalizeProgram(s.ProcessName));
        }

        List<ListeningSocket> removed = new ArrayList<>(safeList(diff.removed()));
        removed.sort(Comparator.comparingInt(s -> s.LocalPort == null ? Integer.MAX_VALUE : s.LocalPort));
        for (ListeningSocket s : removed) {
            row(html, "Cerrado", s.LocalPort, translateScope(s.LocalAddress), normalizeProgram(s.ProcessName));
        }

        List<SnapshotDiff.Changed> changed = new ArrayList<>(safeList(diff.changed()));
        changed.sort(Comparator.comparingInt(c -> {
            Integer p = (c.after() != null) ? c.after().LocalPort : null;
            return p == null ? Integer.MAX_VALUE : p;
        }));
        for (SnapshotDiff.Changed c : changed) {
            ListeningSocket after = c.after();
            Integer port = after != null ? after.LocalPort : null;
            String scope = after != null ? translateScope(after.LocalAddress) : "con alcance desconocido";
            String program = after != null ? normalizeProgram(after.ProcessName) : "programa desconocido";
            row(html, "Cambio", port, scope, program);
        }

        html.append("</tbody></table>");
    }

    /**
     * Emits a single table row with escaped content.
     */
    private static void row(StringBuilder html, String type, Integer port, String scope, String program) {
        html.append("<tr>")
                .append("<td>").append(escapeHtml(type)).append("</td>")
                .append("<td class=\"mono\">").append(escapeHtml(valuePort(port))).append("</td>")
                .append("<td>").append(escapeHtml(scope)).append("</td>")
                .append("<td class=\"mono\">").append(escapeHtml(program)).append("</td>")
                .append("</tr>");
    }

    // ---------------------------
    // Templates (same phrasing as Markdown)
    // ---------------------------

    private static String openedPort(Integer port, String scope, String program) {
        return String.format(
                "Se abrió el puerto %s, accesible %s, por el programa %s.",
                valuePort(port), scope, program
        );
    }

    private static String closedPort(Integer port, String scope, String program) {
        return String.format(
                "Se cerró el puerto %s, que estaba accesible %s, asociado al programa %s.",
                valuePort(port), scope, program
        );
    }

    private static String changedPort(Integer port, String scope, String program) {
        return String.format(
                "El puerto %s cambió de proceso asociado, manteniéndose accesible %s (programa: %s).",
                valuePort(port), scope, program
        );
    }

    private static String changedPortNoProgram(Integer port, String scope) {
        return String.format(
                "El puerto %s cambió de proceso asociado, manteniéndose accesible %s.",
                valuePort(port), scope
        );
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private static String valuePort(Integer port) {
        return (port == null) ? "?" : port.toString();
    }

    /**
     * Translates local bind address into a user-facing scope description.
     */
    private static String translateScope(String localAddress) {
        if (localAddress == null || localAddress.isBlank()) return "con alcance desconocido";
        return switch (localAddress.trim()) {
            case "127.0.0.1" -> "solo desde este equipo";
            case "0.0.0.0" -> "en todas las interfaces";
            default -> "en la interfaz " + localAddress.trim();
        };
    }

    /**
     * Normalizes program name. Empty or null yields the canonical "unknown" label.
     */
    private static String normalizeProgram(String processName) {
        if (processName == null || processName.isBlank()) return "programa desconocido";
        return processName.trim();
    }

    private static boolean isUnknownProgram(String program) {
        return "programa desconocido".equals(program);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * Null-safe string helper.
     */
    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    /**
     * Escapes user-controlled strings to avoid malformed HTML.
     */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Converts "yyyyMMdd-HHmmss" into "dd/MM/yyyy HH:mm:ss".
     * If parsing fails, returns the input as a safe fallback.
     */
    private static String formatTsHuman(String ts) {
        if (ts == null || ts.isBlank()) return "";
        try {
            LocalDateTime dt = LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        } catch (Exception e) {
            return ts;
        }
    }

    private DiffHtmlReportGenerator() {
    }
}
