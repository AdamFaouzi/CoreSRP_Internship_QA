package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * The Partners page (nav "Partners" -> /app/vendors). Verified live 2026-08-04: vendors are
 * auto-derived from processed invoices, not manually created ("No partners yet. They appear
 * automatically as invoices are processed."). Backing API:
 * GET /companies/{companyId}/vendors?sort_by=display_name&sort_dir=asc&limit=50&offset=0.
 */
public class VendorsPage extends BasePage {

    public VendorsPage(Page page) {
        super(page);
    }

    public void search(String query) {
        page.getByPlaceholder("e.g. EMRAD ELECTRIC").fill(query);
    }

    public void clickApply() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply")).click();
    }

    public void clickClear() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Clear")).click();
    }

    public Response waitForVendorListResponse(Runnable triggerAction) {
        return page.waitForResponse(Pattern.compile("/vendors(\\?.*)?$"), triggerAction::run);
    }
}
