package com.coresrp.qa.ui.base;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.report.JsonlWriter;
import com.coresrp.qa.report.RunContext;
import com.coresrp.qa.report.model.DataFootprintEntry;
import com.coresrp.qa.report.model.Finding;
import com.coresrp.qa.ui.pages.LoginPage;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

/**
 * Base for all Playwright tests. One Browser per JVM (fast), one fresh BrowserContext (=
 * isolated cookies/storage) per test method so tests never bleed session/tenant state into
 * each other. Subclasses get a ready `page`, plus helpers for reporting.
 */
@ExtendWith({TimingAndScreenshotExtension.class, ResultWatcher.class})
public abstract class BaseTest {

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    protected Page page;

    private long startNs;
    private long endNs;
    private String lastScreenshotPath;
    private String expectedBehavior;
    private String actualBehavior;

    private static final JsonlWriter FINDINGS = new JsonlWriter(RunContext.findingsFile());
    private static final JsonlWriter FOOTPRINT = new JsonlWriter(RunContext.dataFootprintFile());

    @BeforeEach
    void launch() {
        if (playwright == null) {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(QaConfig.headless())
                    .setSlowMo(QaConfig.slowMoMs()));
        }
        context = browser.newContext();
        context.setDefaultTimeout(QaConfig.defaultTimeoutMs());
        page = context.newPage();
    }

    @AfterEach
    void teardown() {
        if (context != null) {
            context.close();
        }
    }

    /** Logs in via the credentials in qa.properties / env vars. Call explicitly from tests that need auth. */
    protected void loginAsDefaultUser() {
        new LoginPage(page).login(QaConfig.baseUrl(), QaConfig.loginEmail(), QaConfig.loginPassword());
    }

    /**
     * Logs in and lands on the Documents page (nav label "Documents" -> /app/dashboard) — login
     * alone lands on Overview. Uses the in-app nav link (client-side route change), not
     * page.navigate(), which forces a full reload and can race the SPA's post-login auth state,
     * redirecting to /app/login?reason=session_expired (verified live 2026-08-04).
     */
    protected com.coresrp.qa.ui.pages.DocumentsPage loginAndOpenDocuments() {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();
        return new com.coresrp.qa.ui.pages.DocumentsPage(page);
    }

    /**
     * A second, independent BrowserContext (own cookies/storage) sharing the same underlying
     * Browser — for concurrent-session tests. Caller must close() it; the primary `page`'s
     * context is still closed automatically in @AfterEach.
     */
    protected BrowserContext newExtraContext() {
        BrowserContext extra = browser.newContext();
        extra.setDefaultTimeout(QaConfig.defaultTimeoutMs());
        return extra;
    }

    /** Logs in and opens the Partners page (nav "Partners" -> /app/vendors). */
    protected com.coresrp.qa.ui.pages.VendorsPage loginAndOpenPartners() {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToPartners();
        return new com.coresrp.qa.ui.pages.VendorsPage(page);
    }

    /**
     * Logs in and opens the Reconciliation page (nav "Reconciliation" -> /app/reconciliation).
     * Settles on Documents first via in-app nav click, then hard-navigates — a hard navigate
     * straight after login re-triggers the session_expired race (same root cause fixed in
     * loginAndOpenDocuments()); doing it once the SPA/auth state has already settled is safe
     * (verified live 2026-08-04 — every Settings page in ReconTest used this same sequencing).
     */
    protected com.coresrp.qa.ui.pages.ReconciliationPage loginAndOpenReconciliation() {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();
        page.navigate(QaConfig.baseUrl() + "/app/reconciliation");
        return new com.coresrp.qa.ui.pages.ReconciliationPage(page);
    }

    /** Logs in and opens a /app/settings/* sub-page. Settles on Documents first — see loginAndOpenReconciliation(). */
    protected void loginAndOpenSettingsPage(String path) {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();
        page.navigate(QaConfig.baseUrl() + path);
    }

    protected com.coresrp.qa.ui.pages.ApiKeysPage loginAndOpenApiKeys() {
        loginAndOpenSettingsPage("/app/settings/api-keys");
        return new com.coresrp.qa.ui.pages.ApiKeysPage(page);
    }

    protected com.coresrp.qa.ui.pages.AuditLogPage loginAndOpenAuditLog() {
        loginAndOpenSettingsPage("/app/settings/audit-log");
        return new com.coresrp.qa.ui.pages.AuditLogPage(page);
    }

    protected com.coresrp.qa.ui.pages.MembersPage loginAndOpenMembers() {
        loginAndOpenSettingsPage("/app/settings/members");
        return new com.coresrp.qa.ui.pages.MembersPage(page);
    }

    protected com.coresrp.qa.ui.pages.ChartOfAccountsPage loginAndOpenChartOfAccounts() {
        loginAndOpenSettingsPage("/app/settings/chart-of-accounts");
        return new com.coresrp.qa.ui.pages.ChartOfAccountsPage(page);
    }

    /**
     * Logs in and opens a specific invoice's detail/review page. Settles on Documents first via
     * in-app nav click, then hard-navigates — see loginAndOpenReconciliation() for why.
     */
    protected com.coresrp.qa.ui.pages.InvoiceDetailPage loginAndOpenInvoiceDetail(String invoiceId) {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();
        page.navigate(QaConfig.baseUrl() + "/app/invoices/" + invoiceId);
        return new com.coresrp.qa.ui.pages.InvoiceDetailPage(page);
    }

    /** Records what a boundary/adversarial test expected to happen, for the report diff view. */
    protected void expect(String expected) {
        this.expectedBehavior = expected;
    }

    /** Records what actually happened, for the report diff view. */
    protected void actual(String actual) {
        this.actualBehavior = actual;
    }

    /**
     * Flags a suspected real vulnerability (distinct from an ordinary assertion failure):
     * unsanitized input reflected back, cross-org data leakage, unhandled server exception, etc.
     */
    protected void recordFinding(String severity, String summary, String evidence) {
        String screenshotPath = null;
        try {
            String fileName = getClass().getSimpleName() + "_finding_" + System.nanoTime() + ".png";
            var target = RunContext.screenshotsDir().resolve(fileName);
            page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(true));
            screenshotPath = RunContext.runDir().relativize(target).toString();
        } catch (Exception ignored) {
            // Best-effort screenshot; the finding is recorded either way.
        }
        FINDINGS.append(new Finding(
                severity, categoryOf(), getClass().getName(), currentTestName(),
                summary, evidence, screenshotPath, Instant.now().toString()
        ));
    }

    /** Logs a piece of data created in the live environment, so it can be found/cleaned up later. */
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
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private String currentTestName() {
        return getClass().getSimpleName();
    }

    // --- hooks used by TimingAndScreenshotExtension / ResultWatcher ---

    void markStart() {
        startNs = System.nanoTime();
    }

    void markEnd() {
        endNs = System.nanoTime();
    }

    long elapsedMs() {
        return (endNs - startNs) / 1_000_000;
    }

    Page currentPage() {
        return page;
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
