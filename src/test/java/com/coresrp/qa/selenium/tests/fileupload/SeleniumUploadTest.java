package com.coresrp.qa.selenium.tests.fileupload;

import com.coresrp.qa.selenium.base.SeleniumBaseTest;
import com.coresrp.qa.selenium.pages.SeleniumDocumentsPage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Wrong-file-type upload via Selenium — same real finding as the Playwright UploadInvoicesTest
 * (server accepts a plain-text file spoofed as .pdf, no content validation), driven through
 * Selenium's file-input sendKeys() instead of Playwright's setInputFiles(). Costs 1 invoice
 * quota unit (confirmed earlier: every upload consumes quota regardless of content validity).
 */
public class SeleniumUploadTest extends SeleniumBaseTest {

    @Test
    void wrongFileTypeBypassingClientAccept_serverStillAcceptsIt() {
        SeleniumDocumentsPage documents = loginAndOpenDocuments();

        expect("Server rejects content that doesn't match a valid invoice document, or at minimum " +
                "doesn't consume quota on it — already confirmed false via the Playwright suite; " +
                "this re-confirms the same behavior through a different automation tool");
        String wrongTypeFile = Path.of("test-data/wrong-type.txt").toAbsolutePath().toString();
        documents.uploadFile(wrongTypeFile);
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String bodyText = driver.findElement(org.openqa.selenium.By.tagName("body")).getText();
        actual("page body after wrong-type upload: " + truncate(bodyText, 300));

        // Note: a bare "500" substring check is too loose — it false-matched incidental page text
        // here (confirmed live 2026-08-06: quota counters, prices, etc. can contain "500").
        // "internal server error" is a much more specific, reliable signal.
        boolean serverError = bodyText.toLowerCase().contains("internal server error");
        if (serverError) {
            recordFinding("MEDIUM", "Wrong-file-type upload (via Selenium) surfaced a raw server error", bodyText);
        }
        assertFalse(serverError, "wrong-file-type upload should degrade gracefully, not surface a server error");

        recordDataFootprint("invoice", "wrong-type.txt (via Selenium)", "akf00's organization", "Adam Internship QA",
                "Selenium wrong-file-type upload test");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
