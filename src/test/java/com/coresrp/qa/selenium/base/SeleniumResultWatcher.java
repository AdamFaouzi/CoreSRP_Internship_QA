package com.coresrp.qa.selenium.base;

import com.coresrp.qa.report.JsonlWriter;
import com.coresrp.qa.report.RunContext;
import com.coresrp.qa.report.model.TestOutcome;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.time.Instant;
import java.util.Optional;

/**
 * Selenium counterpart to com.coresrp.qa.ui.base.ResultWatcher — writes to the SAME
 * results.jsonl so Selenium and Playwright results appear together in one unified report.
 * Duplicated rather than shared: the two base test classes have unrelated driver lifecycles
 * (WebDriver vs. Playwright Page), not worth a forced common interface for a handful of tests.
 */
public class SeleniumResultWatcher implements TestWatcher {

    private static final JsonlWriter WRITER = new JsonlWriter(RunContext.uiResultsFile());

    private static String categoryOf(ExtensionContext ctx) {
        String pkg = ctx.getRequiredTestClass().getPackageName();
        String marker = ".tests.";
        int idx = pkg.indexOf(marker);
        if (idx < 0) return "uncategorized";
        String rest = pkg.substring(idx + marker.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private void record(ExtensionContext ctx, String status, String message) {
        SeleniumBaseTest instance = ctx.getTestInstance()
                .filter(SeleniumBaseTest.class::isInstance)
                .map(SeleniumBaseTest.class::cast)
                .orElse(null);

        String screenshot = instance != null ? instance.lastScreenshotPath() : null;
        long durationMs = instance != null ? instance.elapsedMs() : 0;
        String expected = instance != null ? instance.expectedBehavior() : null;
        String actual = instance != null ? instance.actualBehavior() : null;

        WRITER.append(new TestOutcome(
                categoryOf(ctx) + " (selenium)",
                ctx.getRequiredTestClass().getName(),
                ctx.getDisplayName(),
                status,
                durationMs,
                expected,
                actual,
                message,
                screenshot,
                Instant.now().toString()
        ));
    }

    @Override
    public void testSuccessful(ExtensionContext ctx) {
        record(ctx, "PASSED", null);
    }

    @Override
    public void testFailed(ExtensionContext ctx, Throwable cause) {
        record(ctx, "FAILED", Optional.ofNullable(cause.getMessage()).orElse(cause.toString()));
    }

    @Override
    public void testAborted(ExtensionContext ctx, Throwable cause) {
        record(ctx, "ABORTED", Optional.ofNullable(cause).map(Throwable::getMessage).orElse(null));
    }

    @Override
    public void testDisabled(ExtensionContext ctx, Optional<String> reason) {
        record(ctx, "DISABLED", reason.orElse(null));
    }
}
