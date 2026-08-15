package com.coresrp.qa.report;

import com.coresrp.qa.config.QaConfig;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

/**
 * Resolves the single timestamped run directory shared by the UI suite, load suite and
 * report generator for one end-to-end run. Pass -Dqa.run.id=<id> (or env QA_RUN_ID) from an
 * orchestration script so all three JVM invocations agree on the same folder; otherwise each
 * process mints its own timestamp (fine for running a single suite standalone).
 */
public final class RunContext {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String RUN_ID = QaConfig.get("qa.run.id", LocalDateTime.now().format(FORMAT));

    private RunContext() {
    }

    public static String runId() {
        return RUN_ID;
    }

    public static Path runDir() {
        Path dir = QaConfig.reportsDir().resolve(RUN_ID);
        dir.toFile().mkdirs();
        return dir;
    }

    public static Path uiDir() {
        Path dir = runDir().resolve("ui");
        dir.toFile().mkdirs();
        return dir;
    }

    public static Path screenshotsDir() {
        Path dir = uiDir().resolve("screenshots");
        dir.toFile().mkdirs();
        return dir;
    }

    public static Path loadDir() {
        Path dir = runDir().resolve("load");
        dir.toFile().mkdirs();
        return dir;
    }

    public static Path uiResultsFile() {
        return uiDir().resolve("results.jsonl");
    }

    public static Path findingsFile() {
        return uiDir().resolve("findings.jsonl");
    }

    public static Path dataFootprintFile() {
        return runDir().resolve("data-footprint.jsonl");
    }

    public static Path reportHtmlFile() {
        return runDir().resolve("report.html");
    }
}
