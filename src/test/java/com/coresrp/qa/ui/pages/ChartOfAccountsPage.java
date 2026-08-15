package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Settings > Chart of accounts. Verified live 2026-08-05: "Add" form (Code, Name, Arabic name,
 * Currency), plus bulk "Upload PDF" import. Backing API: GET /companies/{companyId}/chart-of-accounts.
 * Adding an account is additive/safe — doesn't modify existing accounting config.
 */
public class ChartOfAccountsPage extends BasePage {

    public ChartOfAccountsPage(Page page) {
        super(page);
    }

    public void addAccount(String code, String name) {
        // exact:true — "Code" would otherwise substring-match the "Search by code or name…" search box too.
        page.getByPlaceholder("Code", new Page.GetByPlaceholderOptions().setExact(true)).fill(code);
        page.getByPlaceholder("Name", new Page.GetByPlaceholderOptions().setExact(true)).fill(name);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add")).click();
    }
}
