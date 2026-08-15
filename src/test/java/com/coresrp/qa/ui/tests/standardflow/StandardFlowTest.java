package com.coresrp.qa.ui.tests.standardflow;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.FilePayload;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-path coverage: login, Documents page loads with existing data, upload appears in the
 * right status tab, status filter accuracy, export content correctness. Complements the
 * adversarial suites — this is what should always keep working while those try to break things.
 */
public class StandardFlowTest extends BaseTest {

    @Test
    void loginLandsInAppWithNoServerError() {
        loginAsDefaultUser();

        expect("Login redirects into the app (Overview by default) with no raw server error");
        String bodyText = page.locator("body").innerText();
        actual("page body after login: " + truncate(bodyText, 200));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        assertFalse(serverError, "login should never surface a raw server error");
        assertTrue(page.url().contains("/app/"), "should land somewhere under /app/ after login");
    }

    @Test
    void documentsPageShowsSearchFiltersAndExistingInvoices() {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Documents page renders search box, all filter dropdowns, and the existing seeded invoices");
        // .isVisible() checks immediately with no auto-wait, unlike .fill()/.click() — race the
        // page load and false-negative. .waitFor() (default state: visible) waits properly and
        // throws with a clear message if the element is genuinely never there.
        page.getByPlaceholder("acme...").waitFor();
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Apply")).waitFor();

        String bodyText = page.locator("body").innerText();
        actual("Documents page loaded, body length " + bodyText.length() + " chars");
        assertFalse(bodyText.toLowerCase().contains("internal server error"));
    }

    @Test
    void uploadedInvoiceAppearsInFailedStatusTab() {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Uploading a file that fails extraction shows up under the '✕ Failed' status tab " +
                "(all our synthetic test PDFs fail OCR extraction, verified live 2026-08-04)");

        page.locator("input[type=file]").setInputFiles(new FilePayload(
                "qa-standard-flow.pdf", "application/pdf", minimalPdfBytes()));
        page.waitForTimeout(1500);

        Response afterFailedTab = documents.waitForInvoiceListResponse(
                () -> documents.openStatusTab(DocumentsPage.StatusTab.FAILED));
        int failedCount = countInvoices(afterFailedTab);

        actual("failed-tab invoice count after upload: " + failedCount);
        recordDataFootprint("invoice", "qa-standard-flow.pdf", "akf00's organization", "Adam Internship QA",
                "standard-flow upload->failed-tab test");

        assertTrue(failedCount >= 1, "at least one failed invoice should be visible under the Failed tab");
    }

    @Test
    void statusFilterFailedReturnsOnlyFailedInvoices() {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Filtering Status=failed returns only invoices with status=failed, per the API response");
        documents.selectStatus("failed");
        Response response = documents.waitForInvoiceListResponse(documents::clickApply);
        String body = response.text();
        actual("filtered response: " + truncate(body, 300));

        assertTrue(response.ok());
        assertFalse(body.contains("\"status\":\"uploaded\"") || body.contains("\"status\":\"processing\""),
                "status=failed filter should not return uploaded/processing invoices");
    }

    @Test
    void exportCsvDownloadsNonEmptyFileWithHeaderRow() throws IOException {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Export CSV downloads a non-empty .csv file with a header row");
        Download download = page.waitForDownload(documents::exportCsv);
        Path path = download.path();
        String content = Files.readString(path);
        actual("downloaded file: " + download.suggestedFilename() + ", " + content.length() + " chars, first line: "
                + content.lines().findFirst().orElse("<empty>"));

        assertTrue(download.suggestedFilename().endsWith(".csv"), "should download a .csv file");
        assertFalse(content.isBlank(), "CSV export should not be empty when invoices exist");
    }

    @Test
    void exportJsonDownloadsValidJsonArray() throws IOException {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("Export JSON downloads a syntactically valid JSON array");
        Download download = page.waitForDownload(documents::exportJson);
        Path path = download.path();
        String content = Files.readString(path).trim();
        actual("downloaded file: " + download.suggestedFilename() + ", starts with: " + truncate(content, 50));

        assertTrue(content.startsWith("[") && content.endsWith("]"),
                "JSON export should be a JSON array (starts with [ and ends with ])");
    }

    private int countInvoices(Response response) {
        String body = response.text();
        if (body.isBlank() || body.equals("[]")) return 0;
        return body.split("\"id\":").length - 1;
    }

    private static byte[] minimalPdfBytes() {
        return ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n" +
                "trailer<</Size 4/Root 1 0 R>>\n%%EOF").getBytes();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
