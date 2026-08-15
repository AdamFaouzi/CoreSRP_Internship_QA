package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * Settings > Audit log. Verified live 2026-08-04: append-only, SHA-256 hash-chained privileged
 * action log, admin-only. Backing API: GET /organizations/{orgId}/audit-log?limit=50&amp;offset=0
 * (+ presumably action/user_id/since/until query params, matching the visible filter fields).
 */
public class AuditLogPage extends BasePage {

    public AuditLogPage(Page page) {
        super(page);
    }

    public void filterByAction(String action) {
        page.getByPlaceholder("e.g. auth.login_succeeded").fill(action);
    }

    public void filterByUserId(String userId) {
        page.getByPlaceholder("UUID").fill(userId);
    }

    public void clickApply() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply")).click();
    }

    public void clickVerifyChainIntegrity() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Verify chain integrity")).click();
    }

    public Response waitForAuditLogResponse(Runnable triggerAction) {
        return page.waitForResponse(Pattern.compile("/audit-log(\\?.*)?$"), triggerAction::run);
    }
}
