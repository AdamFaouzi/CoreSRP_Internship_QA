package com.coresrp.qa.report.model;

/** Summary stats for one JMeter DSL scenario run, written by the load test itself after `.run()`. */
public record LoadScenarioResult(
        String scenario,       // ramping | spike | soak | upload-stress | ...
        long samplesCount,
        long errorsCount,
        double errorRatePct,
        long p50Ms,
        long p90Ms,
        long p99Ms,
        long minMs,
        long maxMs,
        long meanMs,
        double throughputPerSec,
        long durationSeconds,
        String dashboardRelPath, // relative to run dir, links to the JMeter DSL htmlReporter output
        String timestamp
) {
}
