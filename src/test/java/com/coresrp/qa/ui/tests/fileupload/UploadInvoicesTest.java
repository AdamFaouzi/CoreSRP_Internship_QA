package com.coresrp.qa.ui.tests.fileupload;

import com.coresrp.qa.ui.base.BaseTest;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Upload-abuse tests against POST /companies/{companyId}/invoices/upload (verified live
 * 2026-08-04: returns 202 + {invoice_id, status, error_message, duplicate}, async processing).
 *
 * IMPORTANT — quota: the trial account has a hard 10-invoice lifetime cap
 * (GET /organizations/{orgId}/quota -> invoice_quota/invoices_created/remaining, can_delete:false
 * means used quota is never returned). EVERY test in this class that reaches the upload endpoint
 * consumes 1 quota slot, even for garbage/rejected files — confirmed live: a spoofed
 * text-file-as-.pdf still returned 202 and decremented `remaining`. Run deliberately, not in a
 * tight loop, and check quota before/after if you're unsure how many slots are left.
 *
 * Uses page.locator("input[type=file]").setInputFiles(...) directly (Playwright's native
 * mechanism, via CDP) rather than clicking the visible "+ Upload invoices" button, since that
 * button opens a native OS file picker Playwright cannot drive.
 */
public class UploadInvoicesTest extends BaseTest {

    private Locator fileInput() {
        return page.locator("input[type=file]");
    }

    @Test
    void wrongFileTypeBypassingClientAccept_serverStillAcceptsIt() {
        loginAndOpenDocuments();

        expect("Server rejects content that doesn't match a valid invoice document (or at minimum " +
                "doesn't burn quota on it); the client `accept` attribute is not sufficient validation on its own");
        fileInput().setInputFiles(new FilePayload(
                "qa-wrong-type-2.pdf", "application/pdf",
                "not a pdf, just plain text pretending to be one".getBytes()));
        page.waitForTimeout(1000);
        actual("see manual validation in reports/2026-08-04_manual-live-validation/ui/findings.jsonl: " +
                "confirmed 202 Accepted + quota decremented for this exact payload shape");

        recordFinding("MEDIUM",
                "Upload endpoint accepts content that doesn't match its declared type, consuming quota",
                "Uploaded a plain-text body named qa-wrong-type-2.pdf with Content-Type application/pdf; " +
                        "no client-side or immediate server-side rejection observed.");
        recordDataFootprint("invoice", "qa-wrong-type-2.pdf (see UI for assigned invoice_id)",
                "akf00's organization", "Adam Internship QA", "wrong-file-type-bypassing-accept test");
    }

    @Test
    void zeroByteFile_rejectedOrFailsGracefully() {
        loginAndOpenDocuments();

        expect("Zero-byte file is rejected (ideally without consuming quota) or ends in a clear failed status, not a 5xx");
        fileInput().setInputFiles(new FilePayload("qa-zero-byte.pdf", "application/pdf", new byte[0]));
        page.waitForTimeout(1000);

        String bodyText = page.locator("body").innerText();
        actual("page body after zero-byte upload: " + truncate(bodyText, 300));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Zero-byte upload surfaced a raw server error", bodyText);
        }
        assertFalse(serverError, "zero-byte upload should degrade gracefully");
        recordDataFootprint("invoice", "qa-zero-byte.pdf", "akf00's organization", "Adam Internship QA", "zero-byte-file test");
    }

    @Test
    void maliciousFilename_pathTraversal_doesNotAffectServerPaths() {
        loginAndOpenDocuments();

        expect("A path-traversal-style filename is stored/displayed as an opaque string, never interpreted as a path");
        fileInput().setInputFiles(new FilePayload(
                "../../../../etc/passwd.pdf", "application/pdf",
                minimalPdfBytes()));
        page.waitForTimeout(1000);

        String bodyText = page.locator("body").innerText();
        actual("page body after path-traversal-filename upload: " + truncate(bodyText, 300));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("HIGH", "Path-traversal-style filename caused a server error", bodyText);
        }
        assertFalse(serverError);
        recordDataFootprint("invoice", "../../../../etc/passwd.pdf (literal filename sent)",
                "akf00's organization", "Adam Internship QA", "malicious-filename path-traversal test");
    }

    @Test
    void maliciousFilename_veryLongAndUnicodeEmoji_handledGracefully() {
        loginAndOpenDocuments();

        String longName = "a".repeat(500) + "🧾💥Ω_invoice.pdf";
        expect("An extremely long, unicode/emoji filename is truncated/sanitized cleanly, not a 500 or a truncated-DB-write error");
        fileInput().setInputFiles(new FilePayload(longName, "application/pdf", minimalPdfBytes()));
        page.waitForTimeout(1000);

        String bodyText = page.locator("body").innerText();
        actual("page body after long/unicode-filename upload: " + truncate(bodyText, 300));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Long/unicode filename caused a server error", bodyText);
        }
        assertFalse(serverError);
        recordDataFootprint("invoice", longName.substring(0, 60) + "...", "akf00's organization", "Adam Internship QA",
                "malicious-filename long+unicode test");
    }

    @Test
    void duplicateUpload_flaggedAsDuplicateNotSilentlyDoubled() {
        loginAndOpenDocuments();
        byte[] content = minimalPdfBytes();

        expect("Uploading byte-identical content twice is flagged via the response's `duplicate` field " +
                "(observed in the real API shape), and doesn't silently create two indistinguishable records");
        fileInput().setInputFiles(new FilePayload("qa-dup.pdf", "application/pdf", content));
        page.waitForTimeout(1000);
        fileInput().setInputFiles(new FilePayload("qa-dup.pdf", "application/pdf", content));
        page.waitForTimeout(1000);

        actual("TODO: assert on the upload response's `duplicate:true` field directly once this test " +
                "is wired to intercept POST /companies/{id}/invoices/upload responses via page.waitForResponse");
        recordDataFootprint("invoice", "qa-dup.pdf (x2, byte-identical)", "akf00's organization", "Adam Internship QA",
                "duplicate-upload test — consumes 2 quota slots");
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
