package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Settings > API keys. Verified live 2026-08-04: keys authenticate the folder-watcher client,
 * format ci_&lt;prefix&gt;_&lt;secret&gt;, sent as "Authorization: Bearer ci_...". The secret is
 * shown once. Backing API: GET /organizations/{orgId}/api-keys (list), create endpoint unconfirmed.
 */
public class ApiKeysPage extends BasePage {

    public ApiKeysPage(Page page) {
        super(page);
    }

    public void createKey(String name) {
        page.getByPlaceholder("Office MFP").fill(name);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create")).click();
    }
}
