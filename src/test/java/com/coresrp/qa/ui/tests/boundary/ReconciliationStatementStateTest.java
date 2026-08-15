package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Confirmed live 2026-08-05: unparseable statement uploads (empty/wrong-type test files, same
 * root cause as our synthetic invoice PDFs) get marked status="processed" with vendor_name=null,
 * statement_total=null, matched_count=0 — i.e. "succeeded" with nothing extracted. Invoices
 * facing the identical situation (nothing extractable) get status="failed" with an explicit
 * error_message ("OCR returned no invoices") instead. Same underlying situation, different
 * signal to the user — worth flagging as a minor consistency gap, not a functional bug (the page
 * itself renders both states without error).
 *
 * Deeper statement-to-invoice MATCHING logic (matched/mismatch/missing counts, amount
 * reconciliation) could not be exercised — every statement in this workspace extracted zero line
 * items, so there's nothing for the matcher to actually match against. Needs a real,
 * OCR-parseable statement (and a real, OCR-parseable invoice to match it against) to test
 * meaningfully — same limitation documented in InvoiceAmountDateTest for the invoice side.
 */
public class ReconciliationStatementStateTest extends BaseTest {

    @Test
    void unparseableStatementsShowProcessedNotFailed_inconsistentWithInvoiceBehavior() {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();

        expect("Documenting an observed inconsistency, not asserting a fix: statements with " +
                "nothing extractable show status=processed (silently empty), while invoices in the " +
                "identical situation show status=failed with an explicit error message");
        String statementsJson = (String) page.evaluate(
                "() => fetch('https://invoices.coresrp.com/companies/" + QaConfig.companyId() + "/statements', "
                        + "{credentials: 'include'}).then(r => r.text())");
        actual("statements list: " + truncate(statementsJson, 500));

        boolean serverError = statementsJson.contains("Internal Server Error") || statementsJson.contains("\"detail\"");
        assertFalse(serverError, "statements list should return normally, not an error, regardless of individual statement outcomes");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
