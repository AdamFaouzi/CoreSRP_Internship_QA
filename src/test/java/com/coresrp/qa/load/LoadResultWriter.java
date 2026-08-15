package com.coresrp.qa.load;

import com.coresrp.qa.report.JsonlWriter;
import com.coresrp.qa.report.RunContext;
import com.coresrp.qa.report.model.LoadScenarioResult;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.stats.StatsSummary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/** Call after `testPlan(...).run()` to persist a scenario's stats for the unified report. */
public final class LoadResultWriter {

    private LoadResultWriter() {
    }

    public static void write(String scenarioName, TestPlanStats stats, Path dashboardDir) {
        write(scenarioName, stats.overall(), stats.duration().toSeconds(), dashboardDir);
    }

    /**
     * Reports a specific sampler's stats (via stats.byLabel("...")) rather than the whole plan —
     * used by the read-only load scenarios to isolate the GET-endpoint performance from the
     * once-per-thread login samples that would otherwise skew the percentiles.
     */
    public static void write(String scenarioName, StatsSummary summary, long durationSeconds, Path dashboardDir) {
        long samples = summary.samplesCount();
        long errors = summary.errorsCount();
        double errorRate = samples == 0 ? 0.0 : (100.0 * errors / samples);

        JsonlWriter writer = new JsonlWriter(RunContext.loadDir().resolve(scenarioName + "-result.jsonl"));
        writer.append(new LoadScenarioResult(
                scenarioName,
                samples,
                errors,
                errorRate,
                summary.sampleTime().median().toMillis(),
                summary.sampleTime().perc90().toMillis(),
                summary.sampleTime().perc99().toMillis(),
                summary.sampleTime().min().toMillis(),
                summary.sampleTime().max().toMillis(),
                summary.sampleTime().mean().toMillis(),
                summary.samples().perSecond(),
                durationSeconds,
                RunContext.runDir().relativize(dashboardIndexHtml(dashboardDir)).toString(),
                Instant.now().toString()
        ));
    }

    /**
     * htmlReporter(dashboardDir) actually writes index.html one level deeper, inside a
     * timestamped/UUID-named subfolder it creates itself — find it rather than assuming
     * dashboardDir/index.html directly (that link 404s otherwise, confirmed live 2026-08-04).
     */
    private static Path dashboardIndexHtml(Path dashboardDir) {
        try (var stream = Files.walk(dashboardDir, 3)) {
            Optional<Path> found = stream
                    .filter(p -> p.getFileName().toString().equals("index.html"))
                    .min(Comparator.comparingInt(p -> p.getNameCount()));
            return found.orElse(dashboardDir.resolve("index.html"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to locate dashboard index.html under " + dashboardDir, e);
        }
    }
}
