package com.coresrp.qa.selenium.base;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.report.RunContext;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Selenium counterpart to TimingAndScreenshotExtension — same purpose, WebDriver screenshot API instead of Playwright's. */
public class SeleniumTimingAndScreenshotExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    @Override
    public void beforeTestExecution(ExtensionContext ctx) {
        asBaseTest(ctx).ifPresent(SeleniumBaseTest::markStart);
    }

    @Override
    public void afterTestExecution(ExtensionContext ctx) {
        Optional<SeleniumBaseTest> test = asBaseTest(ctx);
        test.ifPresent(SeleniumBaseTest::markEnd);

        boolean failed = ctx.getExecutionException().isPresent();
        if (!failed || !QaConfig.screenshotOnFailure()) {
            return;
        }
        test.map(SeleniumBaseTest::currentDriver).ifPresent(driver -> {
            if (driver == null) return;
            try {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                String fileName = sanitize(ctx.getRequiredTestClass().getSimpleName() + "_" + ctx.getDisplayName()) + ".png";
                Path target = RunContext.screenshotsDir().resolve(fileName);
                Files.write(target, png);
                test.get().setLastScreenshotPath(RunContext.runDir().relativize(target).toString());
            } catch (IOException | ClassCastException e) {
                // Best-effort: never let screenshot capture mask the real test failure.
            }
        });
    }

    private static Optional<SeleniumBaseTest> asBaseTest(ExtensionContext ctx) {
        return ctx.getTestInstance().filter(SeleniumBaseTest.class::isInstance).map(SeleniumBaseTest.class::cast);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
