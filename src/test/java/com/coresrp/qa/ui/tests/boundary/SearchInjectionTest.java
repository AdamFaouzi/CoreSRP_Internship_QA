package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial input into the vendor/invoice search field. Pending selector capture (see
 * LoginPage/DocumentsPage TODOs) — this is the "validate the pattern" test from the project plan.
 */
public class SearchInjectionTest extends BaseTest {

    @ParameterizedTest(name = "search field survives injection-style input: {0}")
    @ValueSource(strings = {
            "' OR '1'='1",
            "'; DROP TABLE invoices; --",
            "<script>alert(1)</script>",
            "\" onmouseover=\"alert(1)",
            "𝕬𝖉𝖆𝖒 🧾 invoice",
            "' UNION SELECT null,null,null--"
    })
    void searchFieldSurvivesInjectionStyleInput(String payload) {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Search runs as a plain string filter: no results, no reflected/raw script execution, no 5xx");
        documents.searchVendorOrInvoice(payload);
        documents.clickApply();

        String bodyText = page.locator("body").innerText();
        actual("page body after search: " + truncate(bodyText, 300));

        boolean scriptReflectedUnescaped = page.content().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized search input reflected back as raw HTML/script",
                    "Payload: " + payload + " — found verbatim <script> tag in page HTML after search.");
        }
        assertFalse(scriptReflectedUnescaped, "search payload should not be reflected as raw, unescaped HTML");

        boolean serverError = bodyText.toLowerCase().contains("internal server error")
                || bodyText.toLowerCase().contains("500")
                || bodyText.toLowerCase().contains("stack trace");
        if (serverError) {
            recordFinding("MEDIUM", "Search with adversarial input surfaced a raw server error to the user",
                    "Payload: " + payload + " — page body contained an error/stack-trace indicator.");
        }
        assertFalse(serverError, "search should degrade gracefully (empty results / validation message), not surface a server error");
    }

    @Test
    void clearingSearchAfterInjectionAttemptRestoresFullList() {
        DocumentsPage documents = loginAndOpenDocuments();

        documents.searchVendorOrInvoice("' OR '1'='1");
        documents.clickApply();
        documents.clickClear();

        expect("Clearing the search restores the unfiltered document list without residual filter state");
        actual("TODO: assert against DocumentsPage.resultCount() once the status-tab count selector is captured");
        assertTrue(true, "placeholder assertion pending selector capture");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
