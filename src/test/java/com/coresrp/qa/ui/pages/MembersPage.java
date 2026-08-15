package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Settings > Members. Verified live 2026-08-04: "Add user" form (full name, role, email,
 * password) invites a new workspace member — real invite email sent, real account created on
 * submit. Password requirement shown in the field's placeholder: 10+ chars, 1 uppercase, 1
 * lowercase, 1 digit, must not contain the email username. Backing API:
 * GET /organizations/{orgId}/members (list); invite/create endpoint unconfirmed.
 */
public class MembersPage extends BasePage {

    public MembersPage(Page page) {
        super(page);
    }

    public void fillInviteForm(String fullName, String email, String password) {
        page.getByPlaceholder("Jane Doe").fill(fullName);
        page.getByPlaceholder("teammate@example.com").fill(email);
        page.locator("input[type=password]").fill(password);
    }

    public void submitInvite() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add user")).click();
    }
}
