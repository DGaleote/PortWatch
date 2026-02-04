package com.tss.portwatch.core.model;

/**
 * Metadata associated with a PortWatch execution.
 * <p>
 * This record captures the contextual information that identifies
 * where and when a snapshot or diff was generated.
 * <p>
 * It is embedded in:
 * <ul>
 *   <li>snapshot files</li>
 *   <li>diff files</li>
 *   <li>human-readable reports (Markdown / HTML)</li>
 * </ul>
 * <p>
 * The timestamp is stored in a machine-friendly format
 * (yyyyMMdd-HHmmss) and can be later converted to a human-readable
 * representation when generating reports.
 * <p>
 * This record is immutable by design to ensure traceability
 * and reproducibility of results.
 */
public record PortWatchMetadata(

        // Identifier of the machine where the analysis was executed.
        String machineId,

        // Operating system name as reported by the JVM (normalized upstream).
        String os,

        // Execution timestamp in compact format: yyyyMMdd-HHmmss.
        String timestamp

) {
}
