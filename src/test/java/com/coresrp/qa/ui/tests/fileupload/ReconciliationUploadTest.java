package com.coresrp.qa.ui.tests.fileupload;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.ReconciliationPage;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Upload-abuse tests for the Reconciliation statement upload. Confirmed live 2026-08-04
 * (StatementUploadQuotaCheckTest): unlike invoice uploads, statement uploads do NOT consume
 * invoice_quota — free to test without the same quota caution as UploadInvoicesTest.
 */
public class ReconciliationUploadTest extends BaseTest {

    @Test
    void wrongFileType_handledGracefully() {
        ReconciliationPage reconciliation = loginAndOpenReconciliation();

        expect("A plain-text file spoofed as a PDF is rejected or fails gracefully, no raw server error");
        reconciliation.uploadStatement(new FilePayload(
                "qa-statement-wrong-type.pdf", "application/pdf",
                "not a pdf, just plain text pretending to be one".getBytes()));
        page.waitForTimeout(1500);

        assertNoServerError("wrong-file-type statement upload");
    }

    @Test
    void zeroByteFile_handledGracefully() {
        ReconciliationPage reconciliation = loginAndOpenReconciliation();

        expect("A zero-byte file is rejected or fails gracefully, no raw server error");
        reconciliation.uploadStatement(new FilePayload("qa-statement-zero-byte.pdf", "application/pdf", new byte[0]));
        page.waitForTimeout(1500);

        assertNoServerError("zero-byte statement upload");
    }

    @Test
    void maliciousFilename_pathTraversal_handledGracefully() {
        ReconciliationPage reconciliation = loginAndOpenReconciliation();

        expect("A path-traversal-style filename is stored/displayed as an opaque string, no raw server error");
        reconciliation.uploadStatement(new FilePayload(
                "../../../../etc/passwd.pdf", "application/pdf", minimalPdfBytes()));
        page.waitForTimeout(1500);

        assertNoServerError("path-traversal-filename statement upload");
    }

    @Test
    void corruptedPdf_handledGracefully() {
        ReconciliationPage reconciliation = loginAndOpenReconciliation();

        expect("A structurally invalid PDF is rejected or ends in a clear failed state, not a raw server error");
        reconciliation.uploadStatement(new FilePayload(
                "qa-statement-corrupted.pdf", "application/pdf", "%PDF-1.4\ngarbage not a real pdf structure".getBytes()));
        page.waitForTimeout(1500);

        assertNoServerError("corrupted-pdf statement upload");
    }

    private void assertNoServerError(String context) {
        String bodyText = page.locator("body").innerText();
        actual(context + " — page body: " + truncate(bodyText, 300));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Reconciliation statement upload surfaced a raw server error: " + context, bodyText);
        }
        assertFalse(serverError, context + " should degrade gracefully, not surface a server error");
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
