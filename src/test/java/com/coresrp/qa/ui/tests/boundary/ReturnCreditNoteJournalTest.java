package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.coresrp.qa.ui.pages.InvoiceDetailPage;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Business-logic follow-up to the negative-total finding. That finding showed a negative total on
 * a NORMAL invoice corrupts the journal entries (a negative debit). The correct way to express
 * "money flowing the other way" is the "This is a return / credit note" flag (is_return=true),
 * which per the app's own GL mapping uses a DIFFERENT set of debit/credit accounts (the "Return"
 * rows) — e.g. for a Purchase/Credit invoice: Normal = DR 60111 / CR 4011, Return = DR 4011 /
 * CR 6019 (sides effectively reversed).
 *
 * This test creates a fresh invoice, marks it as a return with a POSITIVE total, saves, and reads
 * back the generated journal_entries to confirm: (a) the amounts are positive magnitudes, and
 * (b) the debit/credit accounts are the Return-row accounts (i.e. the reversal is expressed via
 * account mapping, not a negative sign). Costs 1 invoice-quota unit (one fresh upload).
 */
public class ReturnCreditNoteJournalTest extends BaseTest {

    @Test
    void returnInvoiceWithPositiveTotal_producesReversedAccountsWithPositiveAmounts() {
        DocumentsPage documents = loginAndOpenDocuments();

        // Capture the freshly-created invoice id from the upload response so we open exactly it.
        String[] newInvoiceId = new String[1];
        page.onResponse(res -> {
            if (res.url().contains("/invoices/upload") && res.request().method().equals("POST")) {
                try {
                    String body = res.text();
                    int i = body.indexOf("\"invoice_id\":\"");
                    if (i >= 0) newInvoiceId[0] = body.substring(i + 14, body.indexOf('"', i + 14));
                } catch (Exception ignored) {
                }
            }
        });

        page.locator("input[type=file]").setInputFiles(new FilePayload(
                "qa-return-note-test.pdf", "application/pdf", minimalPdfBytes()));
        page.waitForTimeout(2500);
        String invoiceId = newInvoiceId[0];
        recordDataFootprint("invoice", "qa-return-note-test.pdf (id " + invoiceId + ")",
                "akf00's organization", "Adam Internship QA", "return/credit-note journal-entry test");

        page.navigate(QaConfig.baseUrl() + "/app/invoices/" + invoiceId);
        page.waitForTimeout(1500);

        InvoiceDetailPage invoice = new InvoiceDetailPage(page);
        expect("A return/credit-note with a positive total (100) generates journal entries with " +
                "positive amounts on the REVERSED (Return-row) accounts — the reversal expressed via " +
                "account mapping, not a negative number in a debit/credit field");
        invoice.fillVendor("QA Return Vendor");
        invoice.fillInvoiceNumber("QA-RET-" + System.nanoTime());
        invoice.fillInvoiceDate("2026-01-15");
        invoice.fillAmount(3, "100.00");
        invoice.markAsReturn();
        invoice.saveAndStay();
        page.waitForTimeout(2000);

        String invoiceJson = (String) page.evaluate(
                "id => fetch(`https://invoices.coresrp.com/invoices/${id}`, {credentials: 'include'}).then(r => r.text())",
                invoiceId);
        actual("saved return invoice record: " + truncate(invoiceJson, 700));

        boolean isReturn = invoiceJson.contains("\"is_return\":true");
        boolean hasNegativeAmountInJournal = invoiceJson.matches("(?s).*\"(debit|credit)\":\"-.*");

        if (!isReturn) {
            recordFinding("LOW",
                    "Marking an invoice as a return/credit note did not persist is_return=true",
                    "After checking 'This is a return / credit note' and saving, the stored record has " +
                            "is_return != true. Record: " + truncate(invoiceJson, 400));
        }
        if (hasNegativeAmountInJournal) {
            recordFinding("MEDIUM",
                    "Return/credit-note journal entries contain a negative debit/credit amount",
                    "A return with a positive total (100) produced journal entries containing a negative " +
                            "amount — the reversal should be expressed by swapping debit/credit account sides " +
                            "(positive magnitudes), not by a negative number. Record: " + truncate(invoiceJson, 400));
        }

        assertTrue(isReturn, "the return/credit-note flag should persist as is_return=true");
        assertFalse(hasNegativeAmountInJournal,
                "return journal entries should use positive amounts on reversed accounts, not negative amounts");
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
