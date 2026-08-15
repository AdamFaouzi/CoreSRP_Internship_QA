package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Filter/export edge cases on Documents, using invoice data already in the account — no new
 * uploads, no quota spent.
 */
public class FilterExportEdgeCaseTest extends BaseTest {

    @Test
    void malformedDateRange_fromAfterTo_handledGracefully() {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("From date after To date returns 0 results or a validation message, not a 5xx");
        documents.setDateRange("2026-12-31", "2026-01-01");
        Response response = documents.waitForInvoiceListResponse(documents::clickApply);
        actual("response for From>To date range: " + response.status() + " body: " + truncate(response.text(), 200));

        assertTrue(response.status() < 500, "malformed date range should not cause a server error");
    }

    /**
     * REAL BUG confirmed live 2026-08-04/05, not a test artifact — but INTERMITTENT, not
     * deterministic: across 5 runs, failed 4 times and passed once. The "Document" dropdown's
     * underlying &lt;select&gt; value always correctly updates to "receipt" (verified via
     * el.value), but the Apply request usually omits document_kind from the query entirely
     * (only ?status=failed) — occasionally it's included and the filter works. Looks like a race
     * between the select's change event and whatever builds the request params. Left as a real
     * test (not a fixed skip) so its pass/fail pattern in CI is itself useful signal.
     */
    @Test
    void contradictoryFilters_statusFailedAndDocumentReceipt_returnsEmptyNotError() {
        // All seeded invoices are document_kind=invoice, so Status=failed + Document=receipt is contradictory.
        DocumentsPage documents = loginAndOpenDocuments();

        expect("A filter combination that matches nothing (failed status + receipt document type, " +
                "when all data is document_kind=invoice) returns an empty list, not an error");
        documents.selectStatus("failed");
        var documentDropdown = page.getByRole(com.microsoft.playwright.options.AriaRole.COMBOBOX).nth(1);
        documentDropdown.selectOption("receipt");
        String selectedValue = (String) documentDropdown.evaluate("el => el.value");
        Response response = documents.waitForInvoiceListResponse(documents::clickApply);
        String body = response.text();
        actual("Document dropdown value after selectOption(\"receipt\"): " + selectedValue
                + " | contradictory-filter request URL: " + response.url() + " | response: " + response.status()
                + " body: " + truncate(body, 200));

        assertTrue(response.ok());
        assertEquals("[]", body.trim(), "contradictory filters should return an empty array, not stale/wrong data");
    }

    @Test
    void rapidApplyClearClicking_leavesConsistentState() {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Rapidly alternating Apply/Clear several times leaves the UI in a consistent state " +
                "(no stuck loading spinner, no error), not a race between overlapping requests");
        for (int i = 0; i < 5; i++) {
            documents.searchVendorOrInvoice("test" + i);
            documents.clickApply();
            documents.clickClear();
        }
        page.waitForTimeout(1000);

        String bodyText = page.locator("body").innerText();
        actual("page body after rapid Apply/Clear: " + truncate(bodyText, 200));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Rapid Apply/Clear clicking surfaced a raw server error", bodyText);
        }
        assertFalse(serverError);
    }

    @Test
    void exportWithZeroResults_downloadsValidEmptyFileNotError() throws IOException {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Exporting CSV when the current filter matches 0 invoices downloads a valid " +
                "(header-only or empty) file, not an error page or a broken download");
        documents.searchVendorOrInvoice("qa-definitely-no-match-" + System.nanoTime());
        documents.waitForInvoiceListResponse(documents::clickApply);

        Download download = page.waitForDownload(documents::exportCsv);
        Path path = download.path();
        actual("zero-result export: filename=" + download.suggestedFilename()
                + ", failure=" + download.failure());

        assertTrue(download.suggestedFilename().endsWith(".csv"));
        assertNull(download.failure(), "zero-result export should still succeed as a download, not fail");
        assertNotNull(path, "download should produce a file even with 0 matching results");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
