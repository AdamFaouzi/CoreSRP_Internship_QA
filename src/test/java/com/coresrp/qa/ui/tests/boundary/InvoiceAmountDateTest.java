package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.InvoiceDetailPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary/adversarial input on the invoice detail/review page's amount and date fields — from
 * the original test plan (negative/zero/huge amounts, malformed dates).
 *
 * TIMELINE OF WHAT ACTUALLY HAPPENED, confirmed live 2026-08-04/05 (worth recording since it's
 * non-obvious and shapes what's testable going forward):
 *
 * 1. A status=failed invoice's review form starts fully editable — Vendor/Invoice#/dates/amounts
 *    can all be filled. Save is client-side-blocked (no network request fires) until Required
 *    fields (Vendor, Invoice #, Invoice Date) are non-empty.
 * 2. Once those are filled — including Total = -500 on a normal (non-return) purchase invoice —
 *    Save succeeded with NO validation rejection, and the invoice transitioned from
 *    status=failed to status=reviewed.
 * 3. status=reviewed invoices are readonly: every field, confirmed via el.readOnly, locks once
 *    reviewed. There's no visible "Edit" toggle to unlock it again.
 *
 * The real finding: GET /invoices/{id} on the now-reviewed invoice shows grand_total="-500.00",
 * is_return=false, AND the negative value flows straight into the generated journal_entries as a
 * negative debit ({"account_code":"60111","debit":"-500.00","credit":null}) — accounting-wise
 * nonsensical, since debit/credit should be positive magnitudes with sign conveyed by which side
 * they're on, not a negative number inside a debit field. This feeds every GL export, which is
 * the product's core value proposition per its own Settings > GL mapping description.
 *
 * Further amount/date boundary testing (zero, huge, malformed dates) needs a fresh failed
 * invoice — this one is now locked. Upload one more test PDF to get a new editable subject
 * before extending this class.
 */
public class InvoiceAmountDateTest extends BaseTest {

    @Test
    void negativeTotalOnNonReturnInvoice_acceptedAndFlowsIntoJournalEntriesUnvalidated() {
        loginAsDefaultUser();
        // Settle the SPA's post-login auth state before firing a raw fetch() — a bare evaluate()
        // right after login can race the auth cookie being committed and get 401 (verified live).
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();

        expect("A negative Total on a normal (non-return) purchase invoice, already saved during " +
                "earlier boundary testing, either got rejected server-side or corrected on review — " +
                "checking the raw record to confirm what actually persisted");
        String invoiceJson = (String) page.evaluate(
                "id => fetch(`https://invoices.coresrp.com/invoices/${id}`, {credentials: 'include'}).then(r => r.text())",
                QaConfig.testInvoiceId());
        actual("invoice record: " + invoiceJson);

        boolean isReturn = invoiceJson.contains("\"is_return\":true");
        boolean negativeTotal = invoiceJson.contains("\"grand_total\":\"-500.00\"");
        boolean negativeInJournal = invoiceJson.contains("\"debit\":\"-500.00\"") || invoiceJson.contains("\"credit\":\"-500.00\"");

        if (negativeTotal && !isReturn && negativeInJournal) {
            recordFinding("MEDIUM",
                    "Negative Total on a non-return invoice saved unvalidated and propagates into journal entries as a negative debit/credit",
                    "Invoice " + QaConfig.testInvoiceId() + ": grand_total=-500.00, is_return=false, " +
                            "journal_entries contain a negative debit amount instead of a positive magnitude " +
                            "with debit/credit determined by account side. Full record: " + truncate(invoiceJson, 500));
        }

        assertTrue(negativeTotal, "confirms the negative total actually persisted (this is the finding, not a bug in this assertion)");
    }

    @Test
    void reviewedInvoiceFieldsAreReadonly_noWayToCorrectAfterReview() {
        InvoiceDetailPage invoice = loginAndOpenInvoiceDetail(QaConfig.testInvoiceId());

        expect("Documenting observed behavior: once an invoice reaches status=reviewed, its fields " +
                "lock (readonly) with no visible way to unlock/re-edit — meaning the negative total " +
                "above can't be corrected through this UI either, only re-uploaded as a new invoice");
        boolean totalReadonly = (Boolean) invoice.amountField(3).evaluate("el => el.readOnly");
        boolean vendorReadonly = (Boolean) page.locator("input[placeholder='—']").nth(0).evaluate("el => el.readOnly");
        actual("readonly — Total: " + totalReadonly + ", Vendor: " + vendorReadonly);

        assertTrue(totalReadonly && vendorReadonly, "confirms the lockout on this now-reviewed invoice");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
