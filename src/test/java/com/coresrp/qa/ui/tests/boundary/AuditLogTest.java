package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.AuditLogPage;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audit log filter injection + chain-integrity verification (Settings > Audit log). Read-only, no quota/data impact. */
public class AuditLogTest extends BaseTest {

    @ParameterizedTest(name = "audit log action filter survives adversarial input: {0}")
    @ValueSource(strings = {
            "' OR '1'='1",
            "<script>alert(1)</script>",
            "'; DROP TABLE audit_log; --"
    })
    void actionFilterSurvivesAdversarialInput(String payload) {
        AuditLogPage auditLog = loginAndOpenAuditLog();

        expect("Filtering by an adversarial action string returns no results or a validation error, no raw server error");
        Response response = auditLog.waitForAuditLogResponse(() -> {
            auditLog.filterByAction(payload);
            auditLog.clickApply();
        });
        actual("audit log filter response: " + response.status() + " " + response.url());

        boolean scriptReflectedUnescaped = page.content().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized audit log filter input reflected back as raw HTML/script", payload);
        }
        assertFalse(scriptReflectedUnescaped);

        String bodyText = page.locator("body").innerText().toLowerCase();
        boolean serverError = bodyText.contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Audit log filter with adversarial input surfaced a raw server error", payload);
        }
        assertFalse(serverError, "audit log filter should degrade gracefully, not surface a server error");
    }

    @Test
    void verifyChainIntegrityCompletesWithoutError() {
        AuditLogPage auditLog = loginAndOpenAuditLog();

        expect("Clicking 'Verify chain integrity' completes and reports a result, without a raw server error " +
                "(this is a privileged, presumably non-trivial operation — worth checking it doesn't time out or 500 " +
                "even on an empty/near-empty log)");
        auditLog.clickVerifyChainIntegrity();
        page.waitForTimeout(2000);

        String bodyText = page.locator("body").innerText();
        actual("page body after verify chain integrity: " + truncate(bodyText, 300));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Verify chain integrity surfaced a raw server error", bodyText);
        }
        assertFalse(serverError);
        assertTrue(page.url().contains("/audit-log"), "should stay on the audit log page after verification");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
