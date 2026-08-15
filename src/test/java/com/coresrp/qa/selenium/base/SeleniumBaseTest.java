package com.coresrp.qa.selenium.base;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.report.JsonlWriter;
import com.coresrp.qa.report.RunContext;
import com.coresrp.qa.report.model.DataFootprintEntry;
import com.coresrp.qa.report.model.Finding;
import com.coresrp.qa.selenium.pages.SeleniumLoginPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;

/**
 * Base for the representative Selenium suite — mirrors com.coresrp.qa.ui.base.BaseTest's shape
 * (one fresh driver session per test, expect()/actual()/recordFinding()/recordDataFootprint()
 * feeding the same unified report) but drives ChromeDriver directly instead of Playwright.
 * Selenium 4.6+'s built-in Selenium Manager auto-resolves the matching chromedriver — no manual
 * driver binary setup needed.
 */
@ExtendWith({SeleniumTimingAndScreenshotExtension.class, SeleniumResultWatcher.class})
public abstract class SeleniumBaseTest {

    protected WebDriver driver;

    private long startNs;
    private long endNs;
    private String lastScreenshotPath;
    private String expectedBehavior;
    private String actualBehavior;

    private static final JsonlWriter FINDINGS = new JsonlWriter(RunContext.findingsFile());
    private static final JsonlWriter FOOTPRINT = new JsonlWriter(RunContext.dataFootprintFile());

    @BeforeEach
    void launch() {
        ChromeOptions options = new ChromeOptions();
        // No standalone Chrome install on this machine — reuse Playwright's Chrome-for-Testing
        // binary instead (see QaConfig.seleniumChromeBinary() javadoc).
        options.setBinary(QaConfig.seleniumChromeBinary());
        if (QaConfig.headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1280,800");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis((long) QaConfig.defaultTimeoutMs()));
    }

    @AfterEach
    void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }

    /** Logs in via the credentials in qa.properties / env vars. */
    protected void loginAsDefaultUser() {
        new SeleniumLoginPage(driver).login(QaConfig.baseUrl(), QaConfig.loginEmail(), QaConfig.loginPassword());
    }

    /** Logs in and opens the Documents page — login alone lands on Overview. */
    protected com.coresrp.qa.selenium.pages.SeleniumDocumentsPage loginAndOpenDocuments() {
        loginAsDefaultUser();
        var documents = new com.coresrp.qa.selenium.pages.SeleniumDocumentsPage(driver);
        documents.goToDocuments();
        return documents;
    }

    protected void expect(String expected) {
        this.expectedBehavior = expected;
    }

    protected void actual(String actual) {
        this.actualBehavior = actual;
    }

    protected void recordFinding(String severity, String summary, String evidence) {
        String screenshotPath = null;
        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            String fileName = getClass().getSimpleName() + "_finding_" + System.nanoTime() + ".png";
            var target = RunContext.screenshotsDir().resolve(fileName);
            Files.write(target, png);
            screenshotPath = RunContext.runDir().relativize(target).toString();
        } catch (IOException ignored) {
            // Best-effort screenshot; the finding is recorded either way.
        }
        FINDINGS.append(new Finding(
                severity, categoryOf(), getClass().getName(), currentTestName(),
                summary, evidence, screenshotPath, Instant.now().toString()
        ));
    }

    protected void recordDataFootprint(String type, String reference, String org, String company, String note) {
        FOOTPRINT.append(new DataFootprintEntry(
                type, reference, org, company, currentTestName(), note, Instant.now().toString()
        ));
    }

    private String categoryOf() {
        String pkg = getClass().getPackageName();
        String marker = ".tests.";
        int idx = pkg.indexOf(marker);
        if (idx < 0) return "uncategorized";
        String rest = pkg.substring(idx + marker.length());
        int dot = rest.indexOf('.');
        return (dot < 0 ? rest : rest.substring(0, dot)) + " (selenium)";
    }

    private String currentTestName() {
        return getClass().getSimpleName();
    }

    // --- hooks used by SeleniumTimingAndScreenshotExtension / SeleniumResultWatcher ---

    void markStart() {
        startNs = System.nanoTime();
    }

    void markEnd() {
        endNs = System.nanoTime();
    }

    long elapsedMs() {
        return (endNs - startNs) / 1_000_000;
    }

    WebDriver currentDriver() {
        return driver;
    }

    void setLastScreenshotPath(String path) {
        this.lastScreenshotPath = path;
    }

    String lastScreenshotPath() {
        return lastScreenshotPath;
    }

    String expectedBehavior() {
        return expectedBehavior;
    }

    String actualBehavior() {
        return actualBehavior;
    }
}
