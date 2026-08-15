package com.coresrp.qa.ui.base;

import com.coresrp.qa.report.JsonlWriter;
import com.coresrp.qa.report.RunContext;
import com.coresrp.qa.report.model.TestOutcome;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.time.Instant;
import java.util.Optional;

/**
 * JUnit5 extension: records every test's outcome to reports/&lt;run&gt;/ui/results.jsonl so the
 * report aggregator doesn't have to parse surefire XML. Category is derived from the package
 * name segment right after "tests" (e.g. ui.tests.boundary.SearchInjectionTest -> "boundary").
 */
public class ResultWatcher implements TestWatcher {

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
        BaseTest instance = ctx.getTestInstance()
                .filter(BaseTest.class::isInstance)
                .map(BaseTest.class::cast)
                .orElse(null);

        String screenshot = instance != null ? instance.lastScreenshotPath() : null;
        long durationMs = instance != null ? instance.elapsedMs() : 0;
        String expected = instance != null ? instance.expectedBehavior() : null;
        String actual = instance != null ? instance.actualBehavior() : null;

        WRITER.append(new TestOutcome(
                categoryOf(ctx),
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
