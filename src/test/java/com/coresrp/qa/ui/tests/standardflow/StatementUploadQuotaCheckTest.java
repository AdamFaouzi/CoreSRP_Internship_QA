package com.coresrp.qa.ui.tests.standardflow;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.ReconciliationPage;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.Test;

/**
 * One-off: does uploading a reconciliation statement consume the same invoice_quota as an
 * invoice upload, or a separate resource? Deliberately a single upload, not a suite — run once
 * to inform whether further Reconciliation upload tests need the same quota caution as
 * UploadInvoicesTest, then fold the finding into this class's Javadoc.
 */
public class StatementUploadQuotaCheckTest extends BaseTest {

    @Test
    void singleStatementUpload_checkQuotaImpact() {
        ReconciliationPage reconciliation = loginAndOpenReconciliation();
        String quotaBefore = fetchQuotaJson();

        reconciliation.uploadStatement(new FilePayload("qa-statement-quota-check.pdf", "application/pdf", minimalPdfBytes()));
        page.waitForTimeout(2000);

        String quotaAfter = fetchQuotaJson();

        expect("Determine whether statement uploads consume invoice_quota or a separate resource");
        actual("quota before: " + quotaBefore + " | quota after: " + quotaAfter);

        recordDataFootprint("statement", "qa-statement-quota-check.pdf", "akf00's organization", "Adam Internship QA",
                "one-off quota-impact check for Reconciliation uploads");
    }

    private String fetchQuotaJson() {
        return (String) page.evaluate(
                "() => fetch('https://invoices.coresrp.com/organizations/" + QaConfig.orgId() + "/quota', " +
                        "{credentials: 'include'}).then(r => r.text())");
    }

    private static byte[] minimalPdfBytes() {
        return ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n" +
                "trailer<</Size 4/Root 1 0 R>>\n%%EOF").getBytes();
    }
}
