package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * Invoice detail/review/edit page (/app/invoices/{invoiceId}). Verified live 2026-08-05 by
 * opening a real invoice from the Documents list — no `&lt;table&gt;` markup on the list, and no
 * "view" link/button either; the row's status badge itself is the click target (getByText, since
 * it isn't a semantic button/link). Fields, in DOM order: a return/credit-note checkbox, then
 * three plain text inputs (Vendor, Vendor Tax ID, Invoice #, all placeholder "—"), a Currency
 * input (placeholder "USD"), two date inputs (Invoice Date, Due Date), then four number inputs
 * (Subtotal, Discount, Tax, Total, all placeholder "0.00"). Backing API: GET/likely
 * PUT /invoices/{invoiceId}, plus /sync-history, /payments/summary,
 * /companies/{companyId}/chart-of-accounts, /erp-connections alongside it.
 */
public class InvoiceDetailPage extends BasePage {

    public InvoiceDetailPage(Page page) {
        super(page);
    }

    public void fillVendor(String value) {
        page.locator("input[placeholder='—']").nth(0).fill(value);
    }

    public void fillInvoiceNumber(String value) {
        page.locator("input[placeholder='—']").nth(2).fill(value);
    }

    /**
     * Vendor, Invoice #, and Invoice Date are marked Required (verified live 2026-08-05, this
     * test invoice has none of them since OCR failed) — client-side validation silently blocks
     * Save entirely (no network request fires at all) until they're filled. Call this before
     * testing any other field, then overwrite the specific field under test afterward.
     */
    public void fillRequiredFieldsWithValidDefaults() {
        fillVendor("QA Test Vendor");
        fillInvoiceNumber("QA-" + System.nanoTime());
        fillInvoiceDate("2026-01-15");
    }

    public void fillInvoiceDate(String isoDate) {
        page.locator("input[type=date]").nth(0).fill(isoDate);
    }

    public void fillDueDate(String isoDate) {
        page.locator("input[type=date]").nth(1).fill(isoDate);
    }

    /** index 0=Subtotal, 1=Discount, 2=Tax, 3=Total */
    public void fillAmount(int index, String value) {
        page.locator("input[type=number]").nth(index).fill(value);
    }

    public Locator amountField(int index) {
        return page.locator("input[type=number]").nth(index);
    }

    /** Checks the "This is a return / credit note" checkbox (the only checkbox on the form). */
    public void markAsReturn() {
        page.locator("input[type=checkbox]").first().check();
    }

    public void saveAndStay() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save & stay")).click();
    }

    public Response waitForSaveResponse(Runnable triggerAction) {
        return page.waitForResponse(Pattern.compile("/invoices/[^/]+$"), triggerAction::run);
    }
}
