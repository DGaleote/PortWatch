package com.tss.portwatch.report;

import java.time.LocalDateTime;

/**
 * Metadata associated with a generated report.
 * This record encapsulates contextual information that is not part of the
 * diff itself, but helps to interpret the report in time and scope.
 * Currently, this metadata is optional and may not be used by all report
 * generators. It is defined to allow future extensions without changing
 * existing report signatures.
 *
 * @param machineId         Identifier of the machine where the analysis ran.
 * @param previousAnalysis  Timestamp of the previous analysis used as baseline.
 * @param currentAnalysis   Timestamp of the current analysis.
 */
public record ReportMetadata(
        String machineId,
        LocalDateTime previousAnalysis,
        LocalDateTime currentAnalysis
) {}
