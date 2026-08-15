package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Verified live 2026-08-04. The root URL is a marketing landing page ("Invoice OCR built for the
 * ERP, not the inbox.") — the actual login form lives at /app/login, tabs "Log in"/"Sign up",
 * fields labeled Email (type=email) and Password (type=password), submit button "Log in".
 * Auth backend: POST /auth/cookie/login (cookie session, 204 on success).
 */
public class LoginPage extends BasePage {

    public LoginPage(Page page) {
        super(page);
    }

    public void login(String baseUrl, String email, String password) {
        page.navigate(baseUrl + "/app/login");
        page.getByLabel("Email").fill(email);
        // exact:true — "Password" would otherwise substring-match the "Show password" reveal button too.
        page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true)).fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
        page.waitForURL("**/app/**");
    }
}
