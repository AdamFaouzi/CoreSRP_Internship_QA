package com.coresrp.qa.report;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.report.model.DataFootprintEntry;
import com.coresrp.qa.report.model.Finding;
import com.coresrp.qa.report.model.LoadScenarioResult;
import com.coresrp.qa.report.model.TestOutcome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Merges Playwright results/findings/data-footprint (JSONL, written incrementally by the UI
 * suite) and JMeter DSL scenario summaries (JSON, written by the load suite) into a single
 * report.html for one run. Safe to call even if one suite didn't run: that section just renders
 * as "no data for this run".
 */
public class ReportAggregator {

    public Path generate() {
        List<TestOutcome> outcomes = JsonlReader.read(RunContext.uiResultsFile(), TestOutcome.class);
        List<Finding> findings = JsonlReader.read(RunContext.findingsFile(), Finding.class);
        List<DataFootprintEntry> footprint = JsonlReader.read(RunContext.dataFootprintFile(), DataFootprintEntry.class);
        List<LoadScenarioResult> loadResults = readLoadResults();

        String html = render(outcomes, findings, footprint, loadResults);
        Path out = RunContext.reportHtmlFile();
        try {
            Files.writeString(out, html);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + out, e);
        }
        return out;
    }

    private List<LoadScenarioResult> readLoadResults() {
        Path loadDir = RunContext.loadDir();
        List<LoadScenarioResult> results = new ArrayList<>();
        if (!Files.isDirectory(loadDir)) return results;
        try (var files = Files.list(loadDir)) {
            for (Path f : files.filter(p -> p.toString().endsWith("-result.jsonl")).toList()) {
                results.addAll(JsonlReader.read(f, LoadScenarioResult.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + loadDir, e);
        }
        return results;
    }

    private String render(List<TestOutcome> outcomes, List<Finding> findings,
                           List<DataFootprintEntry> footprint, List<LoadScenarioResult> loadResults) {
        long passed = outcomes.stream().filter(o -> "PASSED".equals(o.status())).count();
        long failed = outcomes.stream().filter(o -> "FAILED".equals(o.status())).count();
        long other = outcomes.size() - passed - failed;

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'>")
          .append("<title>Core Invoice Adversarial QA Report - ").append(esc(RunContext.runId())).append("</title>")
          .append("<style>").append(CSS).append("</style></head><body>");

        sb.append("<h1>Core Invoice Adversarial QA Report</h1>");
        sb.append("<p class='meta'>Run ").append(esc(RunContext.runId()))
          .append(" &middot; Target: ").append(esc(QaConfig.baseUrl()))
          .append(" &middot; Environment: <strong>").append(esc(QaConfig.envLabel())).append("</strong></p>");

        // --- Executive summary ---
        sb.append("<section class='summary'><h2>Executive Summary</h2><div class='cards'>");
        sb.append(card("Total UI tests", String.valueOf(outcomes.size())));
        sb.append(card("Passed", String.valueOf(passed), "ok"));
        sb.append(card("Failed", String.valueOf(failed), failed > 0 ? "bad" : "ok"));
        sb.append(card("Other (aborted/disabled)", String.valueOf(other)));
        sb.append(card("Findings", String.valueOf(findings.size()), findings.isEmpty() ? "ok" : "warn"));
        sb.append(card("Load scenarios run", String.valueOf(loadResults.size())));
        sb.append(card("Data footprint entries", String.valueOf(footprint.size())));
        sb.append("</div>");
        if ("live-free-trial".equals(QaConfig.envLabel()) || QaConfig.envLabel().toLowerCase().contains("live")) {
            sb.append("<p class='notice'>&#9888; This run targeted the <strong>live free-trial environment</strong>, not staging. See Data Footprint below for everything created.</p>");
        }
        sb.append("</section>");

        // --- Findings (security-relevant, distinct from ordinary failures) ---
        sb.append("<section><h2>Findings</h2>");
        if (findings.isEmpty()) {
            sb.append("<p class='empty'>No findings flagged in this run.</p>");
        } else {
            sb.append("<table><tr><th>Severity</th><th>Category</th><th>Summary</th><th>Evidence</th><th>Test</th><th>Screenshot</th></tr>");
            for (Finding f : findings.stream()
                    .sorted(Comparator.comparing((Finding f) -> severityRank(f.severity())))
                    .toList()) {
                sb.append("<tr class='sev-").append(esc(f.severity().toLowerCase())).append("'>")
                  .append("<td>").append(esc(f.severity())).append("</td>")
                  .append("<td>").append(esc(f.category())).append("</td>")
                  .append("<td>").append(esc(f.summary())).append("</td>")
                  .append("<td><pre>").append(esc(f.evidence())).append("</pre></td>")
                  .append("<td>").append(esc(f.testName())).append("</td>")
                  .append("<td>").append(screenshotLink(f.screenshotPath())).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }
        sb.append("</section>");

        // --- Playwright section, grouped by category ---
        sb.append("<section><h2>Playwright &mdash; UI / Adversarial Functional Tests</h2>");
        if (outcomes.isEmpty()) {
            sb.append("<p class='empty'>No UI test results for this run.</p>");
        } else {
            Map<String, List<TestOutcome>> byCategory = outcomes.stream()
                    .collect(Collectors.groupingBy(TestOutcome::category, LinkedHashMap::new, Collectors.toList()));
            for (var entry : byCategory.entrySet()) {
                long catPassed = entry.getValue().stream().filter(o -> "PASSED".equals(o.status())).count();
                sb.append("<h3>").append(esc(entry.getKey())).append(" &mdash; ")
                  .append(catPassed).append("/").append(entry.getValue().size()).append(" passed</h3>");
                sb.append("<table><tr><th>Test</th><th>Status</th><th>Duration</th><th>Expected</th><th>Actual</th><th>Message</th><th>Screenshot</th></tr>");
                for (TestOutcome o : entry.getValue()) {
                    sb.append("<tr class='status-").append(esc(o.status().toLowerCase())).append("'>")
                      .append("<td>").append(esc(o.testName())).append("</td>")
                      .append("<td>").append(esc(o.status())).append("</td>")
                      .append("<td>").append(o.durationMs()).append("ms</td>")
                      .append("<td>").append(esc(o.expected())).append("</td>")
                      .append("<td>").append(esc(o.actual())).append("</td>")
                      .append("<td>").append(esc(o.message())).append("</td>")
                      .append("<td>").append(screenshotLink(o.screenshotPath())).append("</td>")
                      .append("</tr>");
                }
                sb.append("</table>");
            }
        }
        sb.append("</section>");

        // --- JMeter section ---
        sb.append("<section><h2>JMeter &mdash; Load / Stress Scenarios</h2>");
        if (loadResults.isEmpty()) {
            sb.append("<p class='empty'>No load test results for this run.</p>");
        } else {
            sb.append("<table><tr><th>Scenario</th><th>Samples</th><th>Errors</th><th>Error rate</th>")
              .append("<th>p50</th><th>p90</th><th>p99</th><th>Min</th><th>Max</th><th>Throughput</th><th>Duration</th><th>Dashboard</th></tr>");
            for (LoadScenarioResult r : loadResults) {
                String rowClass = r.errorRatePct() > 0 ? "bad" : "ok";
                sb.append("<tr class='").append(rowClass).append("'>")
                  .append("<td>").append(esc(r.scenario())).append("</td>")
                  .append("<td>").append(r.samplesCount()).append("</td>")
                  .append("<td>").append(r.errorsCount()).append("</td>")
                  .append("<td>").append(String.format("%.2f%%", r.errorRatePct())).append("</td>")
                  .append("<td>").append(r.p50Ms()).append("ms</td>")
                  .append("<td>").append(r.p90Ms()).append("ms</td>")
                  .append("<td>").append(r.p99Ms()).append("ms</td>")
                  .append("<td>").append(r.minMs()).append("ms</td>")
                  .append("<td>").append(r.maxMs()).append("ms</td>")
                  .append("<td>").append(String.format("%.2f/s", r.throughputPerSec())).append("</td>")
                  .append("<td>").append(r.durationSeconds()).append("s</td>")
                  .append("<td><a href='").append(esc(dashboardHref(r.dashboardRelPath()))).append("'>dashboard</a></td>")
                  .append("</tr>");
            }
            sb.append("</table>");
            sb.append("<p class='hint'>Error rate &gt; 0% or a clear jump in p90/p99 vs. earlier scenarios marks the breaking point for that scenario &mdash; check the linked dashboard for the response-code/timeout breakdown.</p>");
        }
        sb.append("</section>");

        // --- Data footprint ---
        sb.append("<section><h2>Data Footprint (live environment)</h2>");
        if (footprint.isEmpty()) {
            sb.append("<p class='empty'>No data footprint entries logged for this run.</p>");
        } else {
            sb.append("<table><tr><th>Type</th><th>Reference</th><th>Org</th><th>Company</th><th>Created by test</th><th>Note</th><th>Timestamp</th></tr>");
            for (DataFootprintEntry e : footprint) {
                sb.append("<tr>")
                  .append("<td>").append(esc(e.type())).append("</td>")
                  .append("<td>").append(esc(e.reference())).append("</td>")
                  .append("<td>").append(esc(e.org())).append("</td>")
                  .append("<td>").append(esc(e.company())).append("</td>")
                  .append("<td>").append(esc(e.testName())).append("</td>")
                  .append("<td>").append(esc(e.note())).append("</td>")
                  .append("<td>").append(esc(e.timestamp())).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }
        sb.append("</section>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private static int severityRank(String severity) {
        return switch (severity.toUpperCase()) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            default -> 3;
        };
    }

    private static String screenshotLink(String path) {
        if (path == null || path.isBlank()) return "&mdash;";
        return "<a href='" + esc(path) + "'>screenshot</a>";
    }

    private static String card(String label, String value) {
        return card(label, value, null);
    }

    private static String card(String label, String value, String cls) {
        return "<div class='card" + (cls != null ? " " + cls : "") + "'><div class='card-value'>" + esc(value)
                + "</div><div class='card-label'>" + esc(label) + "</div></div>";
    }

    private static String esc(String s) {
        if (s == null) return "&mdash;";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** dashboardRelPath may be a directory (older results) or the full path to index.html (newer) — link either way. */
    private static String dashboardHref(String dashboardRelPath) {
        if (dashboardRelPath == null) return "#";
        return dashboardRelPath.endsWith(".html") ? dashboardRelPath : dashboardRelPath + "/index.html";
    }

    private static final String CSS = """
        body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 2rem; color: #1a1a1a; background: #fafafa; }
        h1 { margin-bottom: 0.25rem; }
        .meta { color: #555; margin-top: 0; }
        section { margin: 2rem 0; }
        h2 { border-bottom: 2px solid #ddd; padding-bottom: 0.25rem; }
        .cards { display: flex; flex-wrap: wrap; gap: 1rem; }
        .card { background: #fff; border: 1px solid #ddd; border-radius: 8px; padding: 1rem 1.5rem; min-width: 140px; }
        .card-value { font-size: 1.8rem; font-weight: 700; }
        .card-label { color: #666; font-size: 0.85rem; }
        .card.ok .card-value { color: #1a7f37; }
        .card.bad .card-value { color: #cf222e; }
        .card.warn .card-value { color: #9a6700; }
        table { border-collapse: collapse; width: 100%; margin: 0.75rem 0 1.5rem; background: #fff; }
        th, td { border: 1px solid #e2e2e2; padding: 0.4rem 0.6rem; text-align: left; font-size: 0.9rem; vertical-align: top; }
        th { background: #f0f0f0; }
        tr.status-failed, tr.bad, tr.sev-high { background: #fff0f0; }
        tr.sev-medium { background: #fff8e6; }
        .notice { background: #fff8e6; border: 1px solid #f0c36d; padding: 0.75rem 1rem; border-radius: 6px; }
        .empty { color: #777; font-style: italic; }
        .hint { color: #555; font-size: 0.85rem; }
        pre { white-space: pre-wrap; margin: 0; font-size: 0.85rem; }
        """;
}
