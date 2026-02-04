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
 * Generates a human-readable Markdown report from a DiffFile.
 * <p>
 * Output structure:
 * - Title and metadata (machine id, OS, timestamp)
 * - Narrative sections (opened/closed/changed)
 * - Summary table (opened -> closed -> changed, sorted by port)
 * <p>
 * Notes:
 * - This generator must not include sensitive fields such as full executable paths.
 * - Address scope is translated into user-facing text (localhost/all interfaces/etc.).
 * - Program names are normalized and table-escaped when needed.
 */
public final class DiffReportGenerator {

    /**
     * Builds a Markdown report for a given diff payload.
     *
     * @param diffFile Diff payload containing metadata and the computed diff.
     * @param metadata Optional extra metadata. Currently unused by design.
     * @return Markdown report as a String.
     */
    public static String generateMarkdown(DiffFile diffFile, ReportMetadata metadata) {

        SnapshotDiff diff = diffFile.diff();

        StringBuilder md = new StringBuilder();
        var meta = diffFile.metadata();

        // Header and core metadata
        md.append("# PortWatch — Informe de cambios\n\n");
        md.append("- **Equipo:** ").append(meta.machineId()).append("\n");
        md.append("- **Sistema operativo:** ").append(meta.os()).append("\n");
        md.append("- **Fecha del análisis:** ")
                .append(humanTimestamp(meta.timestamp()))
                .append("\n\n");

        // Narrative events grouped by type
        md.append("## Eventos detectados\n\n");

        appendOpened(md, safeList(diff.added()));
        appendClosed(md, safeList(diff.removed()));
        appendChanged(md, safeList(diff.changed()));

        // Summary table and footer note
        appendSummaryTable(md, diff);
        appendFooterNote(md);

        return md.toString();
    }

    /**
     * Appends the "opened ports" narrative section.
     */
    private static void appendOpened(StringBuilder md, List<ListeningSocket> added) {
        md.append("### 🟢 Puertos abiertos\n");
        if (added.isEmpty()) {
            md.append("- (Sin cambios)\n\n");
            return;
        }

        for (ListeningSocket s : added) {
            md.append("- ")
                    .append(openedPort(
                            s.LocalPort,
                            translateScope(s.LocalAddress),
                            normalizeProgram(s.ProcessName)
                    ))
                    .append("\n");
        }

        md.append("\n");
    }

    /**
     * Appends the "closed ports" narrative section.
     */
    private static void appendClosed(StringBuilder md, List<ListeningSocket> removed) {
        md.append("### 🔴 Puertos cerrados\n");
        if (removed.isEmpty()) {
            md.append("- (Sin cambios)\n\n");
            return;
        }

        for (ListeningSocket s : removed) {
            md.append("- ")
                    .append(closedPort(
                            s.LocalPort,
                            translateScope(s.LocalAddress),
                            normalizeProgram(s.ProcessName)
                    ))
                    .append("\n");
        }

        md.append("\n");
    }

    /**
     * Appends the "changed ports" narrative section.
     * <p>
     * A change means: same socket identity key (as defined in comparator) but process association changed.
     * If port is not available, the entry is still emitted with a generic message.
     * If program is unknown, a reduced message variant is emitted.
     */
    private static void appendChanged(StringBuilder md, List<SnapshotDiff.Changed> changed) {
        md.append("### 🔄 Cambios detectados\n");
        if (changed.isEmpty()) {
            md.append("- (Sin cambios)\n\n");
            return;
        }

        for (SnapshotDiff.Changed c : changed) {

            ListeningSocket after = c.after();

            Integer port = after != null ? after.LocalPort : null;
            String scope = after != null ? translateScope(after.LocalAddress) : "con alcance desconocido";
            String program = after != null ? normalizeProgram(after.ProcessName) : "programa desconocido";

            if (port == null) {
                md.append("- Se detectó un cambio de proceso asociado en un puerto no identificable.\n");
                continue;
            }

            if (isUnknownProgram(program)) {
                md.append("- ").append(changedPortNoProgram(port, scope)).append("\n");
            } else {
                md.append("- ").append(changedPort(port, scope, program)).append("\n");
            }
        }

        md.append("\n");
    }

    /**
     * Appends the summary table at the end of the report.
     * Row ordering:
     * - Opened (ascending port)
     * - Closed (ascending port)
     * - Changed (ascending port)
     * <p>
     * The program column is table-escaped to avoid breaking Markdown rendering.
     */
    private static void appendSummaryTable(StringBuilder md, SnapshotDiff diff) {

        md.append("---\n\n");
        md.append("## Resumen de eventos\n\n");
        md.append("| Tipo | Puerto | Alcance | Programa |\n");
        md.append("|------|--------|---------|----------|\n");

        boolean hasEvents =
                !safeList(diff.added()).isEmpty()
                        || !safeList(diff.removed()).isEmpty()
                        || !safeList(diff.changed()).isEmpty();

        if (!hasEvents) {
            md.append("| ℹ️ Sin cambios | – | – | – |\n\n");
            return;
        }

        // Opened
        List<ListeningSocket> added = new ArrayList<>(safeList(diff.added()));
        added.sort(Comparator.comparingInt(s -> s.LocalPort == null ? Integer.MAX_VALUE : s.LocalPort));
        for (ListeningSocket s : added) {
            md.append("| 🟢 Abierto | ")
                    .append(valuePort(s.LocalPort)).append(" | ")
                    .append(translateScope(s.LocalAddress)).append(" | ")
                    .append(escapeTable(normalizeProgram(s.ProcessName)))
                    .append(" |\n");
        }

        // Closed
        List<ListeningSocket> removed = new ArrayList<>(safeList(diff.removed()));
        removed.sort(Comparator.comparingInt(s -> s.LocalPort == null ? Integer.MAX_VALUE : s.LocalPort));
        for (ListeningSocket s : removed) {
            md.append("| 🔴 Cerrado | ")
                    .append(valuePort(s.LocalPort)).append(" | ")
                    .append(translateScope(s.LocalAddress)).append(" | ")
                    .append(escapeTable(normalizeProgram(s.ProcessName)))
                    .append(" |\n");
        }

        // Changed
        List<SnapshotDiff.Changed> changed = new ArrayList<>(safeList(diff.changed()));
        changed.sort(Comparator.comparingInt(c -> {
            Integer p = (c.after() != null) ? c.after().LocalPort : null;
            return p == null ? Integer.MAX_VALUE : p;
        }));

        for (SnapshotDiff.Changed c : changed) {
            ListeningSocket after = c.after();

            Integer port = after != null ? after.LocalPort : null;
            String scope = after != null ? translateScope(after.LocalAddress) : "con alcance desconocido";
            String program = after != null ? escapeTable(normalizeProgram(after.ProcessName)) : "programa desconocido";

            md.append("| 🔄 Cambio | ")
                    .append(valuePort(port)).append(" | ")
                    .append(scope).append(" | ")
                    .append(program).append(" |\n");
        }

        md.append("\n");
    }

    /**
     * Appends a final note clarifying report scope.
     */
    private static void appendFooterNote(StringBuilder md) {
        md.append("📌 *Este informe refleja los cambios detectados entre el análisis anterior y el análisis actual indicados en la cabecera.*\n");
    }

    // -------------------------------------------------------------------------
    // Templates (controlled phrasing)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
        if (processName == null || processName.isBlank()) {
            return "programa desconocido";
        }
        return processName.trim();
    }

    /**
     * Escapes Markdown table separators to avoid column breaks.
     */
    private static String escapeTable(String s) {
        if (s == null || s.isBlank()) {
            return "programa desconocido";
        }
        return s.replace("|", "\\|");
    }

    private static boolean isUnknownProgram(String program) {
        return "programa desconocido".equals(program);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String valuePort(Integer port) {
        return (port == null) ? "?" : port.toString();
    }

    /**
     * Converts a timestamp in "yyyyMMdd-HHmmss" into a human-readable format.
     * If parsing fails, returns the original string as a safe fallback.
     */
    private static String humanTimestamp(String ts) {
        try {
            var dt = LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        } catch (Exception e) {
            return ts;
        }
    }

    private DiffReportGenerator() {
    }
}
