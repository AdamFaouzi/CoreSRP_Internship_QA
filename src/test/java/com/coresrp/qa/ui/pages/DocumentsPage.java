package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The Documents page (nav label "Documents" -> /app/dashboard, heading "Invoices"). Selectors
 * verified live 2026-08-04 against the trial account (org "akf00's organization" / company
 * "Adam Internship QA"). Backing API: GET /companies/{companyId}/invoices (list + ?q=... search,
 * ?status=... filter), /invoices/issue-counts, /vendors. Angular app — file input has no
 * id/name/class, so it's targeted by tag+type (verified unique on this page).
 */
public class DocumentsPage extends BasePage {

    public DocumentsPage(Page page) {
        super(page);
    }

    public void searchVendorOrInvoice(String query) {
        page.getByPlaceholder("acme...").fill(query);
    }

    public void clickApply() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply")).click();
    }

    public void clickClear() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Clear")).click();
    }

    /**
     * Sets files directly on the hidden native file input, bypassing the OS picker (which
     * Playwright can't drive) AND bypassing the input's client-side `accept` attribute
     * (image/*,application/pdf,.heic,.heif) — useful on purpose for wrong-file-type tests,
     * since the browser-level accept filter proves nothing about server-side validation.
     */
    public void uploadFiles(Path... files) {
        page.locator("input[type=file]").setInputFiles(files);
    }

    /** Waits for the invoice-list API call triggered by the next action (e.g. clickApply()) and returns it raw. */
    public Response waitForInvoiceListResponse(Runnable triggerAction) {
        return page.waitForResponse(Pattern.compile("/invoices(\\?.*)?$"), triggerAction::run);
    }

    public void exportCsv() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export CSV")).click();
    }

    public void exportJson() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export JSON")).click();
    }

    public void exportExcel() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export Excel")).click();
    }

    public void openStatusTab(StatusTab tab) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tab.label)).click();
    }

    /**
     * The "Status" &lt;label&gt; is a visual sibling, not programmatically associated (no for=/
     * wrapping) — getByLabel("Status") times out finding nothing (verified live 2026-08-04).
     * Filters render in a fixed order (Status, Document, Type, Settlement), so the first
     * combobox is Status.
     */
    public void selectStatus(String value) {
        page.getByRole(AriaRole.COMBOBOX).nth(0).selectOption(value);
    }

    public void setDateRange(String fromIso, String toIso) {
        // .all() doesn't auto-wait (unlike .fill()/.click()) — can race a still-loading page and
        // return an empty list. Wait for the first date input before snapshotting both.
        page.locator("input[type=date]").first().waitFor();
        List<com.microsoft.playwright.Locator> dates = page.locator("input[type=date]").all();
        if (!fromIso.isBlank()) dates.get(0).fill(fromIso);
        if (!toIso.isBlank()) dates.get(1).fill(toIso);
    }

    public enum StatusTab {
        ALL("All"), ISSUES("⚠ Issues"), FAILED("✕ Failed"),
        TOTAL_MISMATCH("Total mismatch"), MISSING_DATA("Missing data");

        final String label;

        StatusTab(String label) {
            this.label = label;
        }
    }
}
