package com.coresrp.qa.report.model;

/** One Playwright test result, appended to reports/&lt;run&gt;/ui/results.jsonl. */
public record TestOutcome(
        String category,       // boundary | fileupload | multitenant | session | standardflow
        String testClass,
        String testName,
        String status,         // PASSED | FAILED | ABORTED
        long durationMs,
        String expected,
        String actual,
        String message,
        String screenshotPath, // relative to run dir, null if none
        String timestamp
) {
}
