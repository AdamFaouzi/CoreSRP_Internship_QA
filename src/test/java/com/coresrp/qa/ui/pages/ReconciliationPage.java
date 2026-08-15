package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FilePayload;

/**
 * The Reconciliation page (nav "Reconciliation" -> /app/reconciliation). Verified live
 * 2026-08-04: upload a vendor statement (PDF/image), OCR'd and matched against invoices.
 * Backing API: GET /companies/{companyId}/statements (list), upload endpoint not yet confirmed
 * (verify via network capture before assuming a path — don't guess like the JMeter login body was).
 */
public class ReconciliationPage extends BasePage {

    public ReconciliationPage(Page page) {
        super(page);
    }

    public void uploadStatement(FilePayload file) {
        page.locator("input[type=file]").setInputFiles(file);
    }
}
