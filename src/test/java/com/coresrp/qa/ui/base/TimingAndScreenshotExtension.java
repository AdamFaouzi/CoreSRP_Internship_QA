package com.coresrp.qa.ui.base;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.report.RunContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Times each test and, on failure, grabs a screenshot from the test's Playwright Page (if the
 * instance is a BaseTest and a page exists) into reports/&lt;run&gt;/ui/screenshots/. Runs before
 * ResultWatcher so the screenshot path/duration are available when the outcome is recorded.
 */
public class TimingAndScreenshotExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    @Override
    public void beforeTestExecution(ExtensionContext ctx) {
        asBaseTest(ctx).ifPresent(BaseTest::markStart);
    }

    @Override
    public void afterTestExecution(ExtensionContext ctx) {
        Optional<BaseTest> test = asBaseTest(ctx);
        test.ifPresent(BaseTest::markEnd);

        boolean failed = ctx.getExecutionException().isPresent();
        if (!failed || !QaConfig.screenshotOnFailure()) {
            return;
        }
        test.map(BaseTest::currentPage).ifPresent(page -> {
            if (page == null || page.isClosed()) return;
            try {
                String fileName = sanitize(ctx.getRequiredTestClass().getSimpleName() + "_" + ctx.getDisplayName()) + ".png";
                Path target = RunContext.screenshotsDir().resolve(fileName);
                page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(true));
                test.get().setLastScreenshotPath(RunContext.runDir().relativize(target).toString());
            } catch (Exception e) {
                // Best-effort: never let screenshot capture mask the real test failure.
            }
        });
    }

    private static Optional<BaseTest> asBaseTest(ExtensionContext ctx) {
        return ctx.getTestInstance().filter(BaseTest.class::isInstance).map(BaseTest.class::cast);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
