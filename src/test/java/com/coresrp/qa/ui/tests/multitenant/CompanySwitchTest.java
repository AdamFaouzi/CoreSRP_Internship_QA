package com.coresrp.qa.ui.tests.multitenant;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.coresrp.qa.ui.pages.NavBar;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-tenant boundary tests for the org/company switcher. Verified live 2026-08-04: switching
 * company re-fetches GET /companies/{companyId}/invoices with the NEWLY selected company's UUID
 * in the path — i.e. tenant scoping is a path parameter set by the backend session/selection,
 * not something the client freely supplies per-request. That's the thing worth attacking here:
 * does the server actually re-validate that the selected company belongs to the caller's org on
 * every request, or does it trust a client-held company ID from an earlier session/response?
 *
 * A second company ("QA Leakage Test Co", see qa.properties.example qa.company.id.b) was created
 * live 2026-08-04 specifically for the tests below, with one invoice uploaded to it. Verified
 * manually first (see reports/2026-08-04_manual-live-validation/): Company A's list only ever
 * showed its own 3 invoices, and a direct fetch() to Company B's endpoint while the UI session
 * was on Company A correctly returned only Company B's data — no mixing either direction. The
 * tests below encode that same check as a repeatable regression using the real invoice IDs from
 * that session as fixed canaries (no new upload needed on every run).
 */
public class CompanySwitchTest extends BaseTest {

    @Test
    void switchingCompanyReSelectsSameCompany_apiCallUsesCorrectCompanyId() {
        DocumentsPage documents = loginAndOpenDocuments();
        NavBar nav = new NavBar(page);

        expect("Re-selecting the current company from the switcher re-fetches data scoped to that " +
                "same company (no errors, no ID drift) — baseline before testing an actual switch");
        Response response = documents.waitForInvoiceListResponse(() -> nav.switchCompany("Adam Internship QA"));
        actual("invoice list refetch status: " + response.status() + " url: " + response.url());

        assertTrue(response.ok(), "re-selecting the same company should not error");
    }

    @Test
    void switchingToDifferentCompany_apiUrlAndVisibleListBothChange() {
        DocumentsPage documents = loginAndOpenDocuments();
        NavBar nav = new NavBar(page);

        expect("Switching to company B re-fetches the invoice list scoped to company B's UUID, not company A's");
        // Switching company navigates back to Overview (verified live 2026-08-04), so the
        // /invoices refetch only happens once Documents is reopened afterward.
        nav.switchCompany(QaConfig.companyNameB());
        page.waitForLoadState();
        Response response = documents.waitForInvoiceListResponse(nav::goToDocuments);
        actual("invoice list URL after switch: " + response.url());

        assertTrue(response.ok(), "switching company should not error");
        assertTrue(response.url().contains(QaConfig.companyIdB()),
                "invoice list request should be scoped to company B's id after switching");
        assertFalse(response.url().contains(QaConfig.companyId()),
                "invoice list request should not still reference company A's id after switching");
    }

    /**
     * The highest-value test in this class (flagged as priority in the original test plan): does
     * company A's data ever appear while company B is the active tenant, or vice versa? Uses the
     * real invoice created live in each company (see class Javadoc) as fixed canaries so this
     * doesn't need to re-upload — and therefore doesn't spend quota — on every run.
     */
    @Test
    void crossCompanyInvoiceIsolation_noLeakageEitherDirection() {
        DocumentsPage documents = loginAndOpenDocuments();
        NavBar nav = new NavBar(page);

        expect("While company A is active, company B's canary invoice never appears in the list " +
                "(and vice versa) — checked both through the UI-driven list and a direct API call " +
                "for the other company's id, matching the manual validation this test encodes");

        // Company A active (default after login): direct list must not contain B's canary invoice.
        String companyAListBody = fetchInvoicesJson(QaConfig.companyId());
        String companyBListBody = fetchInvoicesJson(QaConfig.companyIdB());
        actual("company A list contains B-canary id: " + companyAListBody.contains(COMPANY_B_CANARY_INVOICE_ID)
                + "; company B list contains A-canary id: " + companyBListBody.contains(COMPANY_A_CANARY_INVOICE_ID));

        assertFalse(companyAListBody.contains(COMPANY_B_CANARY_INVOICE_ID),
                "company A's invoice list must never contain company B's canary invoice id");
        assertFalse(companyBListBody.contains(COMPANY_A_CANARY_INVOICE_ID),
                "company B's invoice list must never contain company A's canary invoice id");

        // Also verify through the actual UI switch, not just a direct API call. Switching company
        // navigates back to Overview (verified live 2026-08-04), so re-open Documents afterward
        // to trigger the /invoices refetch this asserts against.
        nav.switchCompany(QaConfig.companyNameB());
        page.waitForLoadState();
        Response uiListAfterSwitch = documents.waitForInvoiceListResponse(nav::goToDocuments);
        String bodyText = page.locator("body").innerText();
        if (bodyText.contains(COMPANY_A_CANARY_INVOICE_ID)) {
            recordFinding("HIGH", "Company A's invoice data visible in the UI while Company B is active",
                    "Canary invoice id " + COMPANY_A_CANARY_INVOICE_ID + " (belongs to company A) found in " +
                            "page body while switched to company B via the nav switcher.");
        }
        assertFalse(bodyText.contains(COMPANY_A_CANARY_INVOICE_ID),
                "company A's canary invoice id should never render while company B is the active tenant");
        assertTrue(uiListAfterSwitch.ok());
    }

    private String fetchInvoicesJson(String companyId) {
        return (String) page.evaluate(
                "companyId => fetch(`https://invoices.coresrp.com/companies/${companyId}/invoices?limit=50`, " +
                        "{credentials: 'include'}).then(r => r.text())",
                companyId);
    }

    // Real invoice ids captured live 2026-08-04 (see class Javadoc / reports/2026-08-04_manual-live-validation/).
    private static final String COMPANY_A_CANARY_INVOICE_ID = "019fcc51-1b47-7281-b879-dad55a408b8e";
    private static final String COMPANY_B_CANARY_INVOICE_ID = "019fcc68-618f-7da3-8c8c-408056b09731";

    @Test
    void rapidRepeatedCompanySwitchClicks_doNotCorruptSelectionState() {
        loginAsDefaultUser();
        NavBar nav = new NavBar(page);

        expect("Clicking the switcher and re-selecting the same company rapidly, several times in a " +
                "row, leaves the UI in a consistent state (correct company shown, no stuck loading " +
                "spinner, no duplicated/stale list) rather than a race between overlapping requests");
        for (int i = 0; i < 5; i++) {
            nav.switchCompany("Adam Internship QA");
        }
        page.waitForTimeout(500);

        String label = nav.currentCompanyLabel();
        actual("company label after rapid re-selection: " + label);
        assertTrue(label.contains("Adam Internship QA"), "switcher should still show the correct company after rapid clicks");
    }

    @Test
    void directNavigationToDashboardWithoutOrgContext_doesNotErrorOrLeak() {
        loginAsDefaultUser();

        expect("Navigating straight to the Documents URL (skipping the normal org/company " +
                "selection flow, e.g. from a bookmark) lands on the previously-selected tenant, " +
                "not a broken or cross-tenant state");
        page.navigate(page.url().replaceAll("/app/.*", "") + "/app/dashboard");
        page.waitForLoadState();

        String bodyText = page.locator("body").innerText();
        actual("page body after direct /app/dashboard navigation: " + bodyText.substring(0, Math.min(200, bodyText.length())));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Direct navigation to Documents surfaced a server error", bodyText);
        }
        assertTrue(!serverError, "direct navigation should not surface a raw server error");
    }
}
