package com.coresrp.qa.report.model;

/**
 * A suspected real vulnerability (not just a failed assertion), e.g. unsanitized input
 * reflected back, cross-org data leakage, unhandled server exception surfaced to the user.
 * Appended to reports/&lt;run&gt;/ui/findings.jsonl and rendered in a distinct report section.
 */
public record Finding(
        String severity,   // HIGH | MEDIUM | LOW | INFO
        String category,
        String testClass,
        String testName,
        String summary,
        String evidence,
        String screenshotPath,
        String timestamp
) {
}
