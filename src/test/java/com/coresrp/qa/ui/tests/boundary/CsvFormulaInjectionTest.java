package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.coresrp.qa.ui.pages.InvoiceDetailPage;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * CSV/Excel formula injection: if a vendor name starting with =, +, -, or @ ends up unescaped in
 * an exported CSV, opening that file in Excel executes it as a formula — a well-known vuln class
 * (CVE-worthy in many products) never tested in this suite until now. Uses a fresh invoice
 * (costs 1 quota) since our previously-editable test invoice is now locked/reviewed.
 */
public class CsvFormulaInjectionTest extends BaseTest {

    private static final String FORMULA_PAYLOAD = "=1+1+cmd|' /C calc'!A0";

    @Test
    void vendorNameWithFormulaPayload_notEvaluableWhenExported() throws IOException {
        DocumentsPage documents = loginAndOpenDocuments();

        // Upload a fresh failed invoice — the one from earlier boundary testing is locked (reviewed).
        page.locator("input[type=file]").setInputFiles(new FilePayload(
                "qa-csv-injection-test.pdf", "application/pdf", minimalPdfBytes()));
        page.waitForTimeout(2000);

        // Open it via the Failed tab (mirrors StandardFlowTest's pattern for finding the new upload).
        documents.openStatusTab(DocumentsPage.StatusTab.FAILED);
        page.waitForTimeout(1000);
        page.getByText("failed", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).last().click();
        page.waitForTimeout(1000);

        InvoiceDetailPage invoice = new InvoiceDetailPage(page);
        expect("A vendor name starting with a formula-trigger character (=, +, -, @) is either " +
                "rejected, or exported with a neutralizing prefix (e.g. a leading apostrophe) so " +
                "opening the CSV/Excel export in a spreadsheet app never executes it as a formula");
        invoice.fillVendor(FORMULA_PAYLOAD);
        page.locator("input[placeholder='—']").nth(2).fill("QA-CSV-" + System.nanoTime());
        invoice.fillInvoiceDate("2026-01-15");
        invoice.fillAmount(3, "100.00"); // Total — also Required, easy to miss
        invoice.saveAndStay();
        page.waitForTimeout(1500);

        recordDataFootprint("invoice", "qa-csv-injection-test.pdf (vendor=" + FORMULA_PAYLOAD + ")",
                "akf00's organization", "Adam Internship QA", "CSV formula injection test");

        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();
        Download download = page.waitForDownload(documents::exportCsv);
        Path path = download.path();
        String content = Files.readString(path);

        boolean unescapedFormula = Pattern.compile(
                "(?<![\"'])" + Pattern.quote(FORMULA_PAYLOAD)).matcher(content).find();
        actual("CSV contains payload: " + content.contains(FORMULA_PAYLOAD)
                + " | unescaped (no quote/apostrophe prefix): " + unescapedFormula
                + " | relevant snippet: " + snippetAround(content, FORMULA_PAYLOAD));

        if (unescapedFormula) {
            recordFinding("MEDIUM",
                    "CSV export does not neutralize formula-trigger characters in vendor names (CSV/formula injection)",
                    "Vendor name \"" + FORMULA_PAYLOAD + "\" appears unescaped in the exported CSV. " +
                            "Opening this export in Excel/Sheets would execute it as a formula, potentially " +
                            "running arbitrary commands (CVE-class: CSV/Excel formula injection).");
        }
        assertFalse(unescapedFormula, "exported CSV should neutralize leading formula-trigger characters");
    }

    private static String snippetAround(String content, String needle) {
        int idx = content.indexOf(needle.substring(0, Math.min(10, needle.length())));
        if (idx < 0) return "(payload not found in export)";
        int start = Math.max(0, idx - 20);
        int end = Math.min(content.length(), idx + needle.length() + 20);
        return content.substring(start, end);
    }

    private static byte[] minimalPdfBytes() {
        return ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n" +
                "trailer<</Size 4/Root 1 0 R>>\n%%EOF").getBytes();
    }
}
