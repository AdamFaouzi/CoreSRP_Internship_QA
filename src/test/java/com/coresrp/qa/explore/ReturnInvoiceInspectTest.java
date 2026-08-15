package com.coresrp.qa.explore;

import com.coresrp.qa.ui.base.BaseTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only: dump the full journal_entries of the return invoice created by ReturnCreditNoteJournalTest. */
public class ReturnInvoiceInspectTest extends BaseTest {

    // The return invoice id from the ReturnCreditNoteJournalTest run.
    private static final String RETURN_INVOICE_ID = "019fefc6-c1e2-7941-ac53-88f2aa310b1e";

    @Test
    void dumpReturnInvoiceJournalEntries() throws IOException {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();

        String json = (String) page.evaluate(
                "id => fetch(`https://invoices.coresrp.com/invoices/${id}`, {credentials: 'include'}).then(r => r.text())",
                RETURN_INVOICE_ID);

        Path out = Path.of("/private/tmp/claude-501/-Users-adamfaouzi-Desktop-CoreSRP-Internship/"
                + "b409d077-3ae2-4fc4-9bc0-794dec89819e/scratchpad/recon/return-invoice-full.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json);
        System.out.println("is_return present: " + json.contains("\"is_return\":true"));
        int i = json.indexOf("journal_entries");
        System.out.println("journal_entries: " + (i >= 0 ? json.substring(i) : "NOT PRESENT"));
    }
}
