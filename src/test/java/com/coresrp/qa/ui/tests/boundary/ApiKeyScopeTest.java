package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.ApiKeysPage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * API keys authenticate the Windows folder-watcher client (Bearer ci_&lt;prefix&gt;_&lt;secret&gt;,
 * verified live 2026-08-06). The intended use is uploading invoices unattended. Does the key's
 * actual access match that narrow intended scope, or can it also do admin-level things (create
 * more keys, invite members, delete data) if it leaked? Tested in a fresh cookie-less context so
 * only the Bearer token is ever sent — no ambient session auth to muddy the result.
 */
public class ApiKeyScopeTest extends BaseTest {

    @Test
    void apiKeyScope_matchesIntendedUploadOnlyPurpose() {
        ApiKeysPage apiKeys = loginAndOpenApiKeys();

        String[] secretHolder = new String[1];
        page.onResponse(res -> {
            if (res.url().contains("api-keys") && res.request().method().equals("POST")) {
                try {
                    String body = res.text();
                    int idx = body.indexOf("\"plaintext\":\"");
                    if (idx >= 0) {
                        int start = idx + "\"plaintext\":\"".length();
                        int end = body.indexOf('"', start);
                        secretHolder[0] = body.substring(start, end);
                    }
                } catch (Exception ignored) {
                }
            }
        });
        apiKeys.createKey("QA Scope Test Key");
        page.waitForTimeout(1500);
        String secret = secretHolder[0];
        recordDataFootprint("api-key", "QA Scope Test Key", "akf00's organization", "Adam Internship QA",
                "API key scope test — revoke after review");

        expect("The key can fetch invoices (its intended purpose) but cannot perform admin actions " +
                "(create more API keys, list members, delete an invoice) — if it can, a leaked key " +
                "grants far more than the folder-watcher use case needs");

        try (BrowserContext freshContext = newExtraContext()) {
            Page freshPage = freshContext.newPage();
            freshPage.navigate(QaConfig.baseUrl());

            // The key's stated purpose is authenticating the automated upload client — try that
            // specific endpoint first, in case API-key auth is only wired up there, not on the
            // general browser-facing endpoints the cookie session uses.
            String uploadResult = fetchWithBearer(freshPage,
                    QaConfig.baseUrl() + "/companies/" + QaConfig.companyId() + "/invoices/upload", "POST", secret);
            String invoicesResult = fetchWithBearer(freshPage,
                    QaConfig.baseUrl() + "/companies/" + QaConfig.companyId() + "/invoices?limit=1", "GET", secret);
            String membersResult = fetchWithBearer(freshPage,
                    QaConfig.baseUrl() + "/organizations/" + QaConfig.orgId() + "/members", "GET", secret);
            String createKeyResult = fetchWithBearer(freshPage,
                    QaConfig.baseUrl() + "/organizations/" + QaConfig.orgId() + "/api-keys", "POST", secret);
            String deleteResult = fetchWithBearer(freshPage,
                    QaConfig.baseUrl() + "/invoices/" + QaConfig.testInvoiceId(), "DELETE", secret);

            actual("secret captured: " + (secret != null ? "yes, starts with " + secret.substring(0, Math.min(10, secret.length())) : "NO - null")
                    + " | POST upload (its stated purpose): " + uploadResult
                    + " | GET invoices: " + invoicesResult + " | GET members: " + membersResult
                    + " | POST api-keys (create another key): " + createKeyResult + " | DELETE invoice: " + deleteResult);

            // 401 = auth itself rejected. Any other status (2xx validation-passed, or 4xx
            // validation-failed-but-authenticated, e.g. 422 for a missing file body) means the
            // key format/auth was accepted for that endpoint.
            boolean uploadAuthAccepted = !uploadResult.startsWith("401");
            boolean membersAuthAccepted = !membersResult.startsWith("401");
            boolean createKeyAuthAccepted = !createKeyResult.startsWith("401");
            boolean deleteAuthAccepted = !deleteResult.startsWith("401") && !deleteResult.startsWith("403");

            boolean canDoAdminActions = membersAuthAccepted || createKeyAuthAccepted || deleteAuthAccepted;
            if (canDoAdminActions) {
                recordFinding("HIGH",
                        "API key (intended for automated invoice upload only) is accepted for admin-level endpoints",
                        "GET members: " + membersResult + ", POST api-keys: " + createKeyResult
                                + ", DELETE invoice: " + deleteResult + " — a leaked upload-only API key " +
                                "should not authenticate for listing members, minting new keys, or deleting data.");
            }
            if (!uploadAuthAccepted) {
                recordFinding("LOW",
                        "API key was rejected (401) even on its own documented endpoint (invoice upload)",
                        "POST " + QaConfig.baseUrl() + "/companies/" + QaConfig.companyId() + "/invoices/upload "
                                + "with a freshly-created key's Bearer token returned " + uploadResult
                                + " — either the auth header format assumed here is wrong, or the feature " +
                                "doesn't work as documented. Worth a manual check with the real folder-watcher client.");
            }
            assertFalse(canDoAdminActions, "key should NOT authenticate for admin-level actions");
        }
    }

    private String fetchWithBearer(Page page, String url, String method, String bearerToken) {
        return (String) page.evaluate(
                "([url, method, token]) => fetch(url, {method, headers: {'Authorization': 'Bearer ' + token}})" +
                        ".then(r => r.status + '|' + r.statusText)",
                new Object[]{url, method, bearerToken});
    }
}
