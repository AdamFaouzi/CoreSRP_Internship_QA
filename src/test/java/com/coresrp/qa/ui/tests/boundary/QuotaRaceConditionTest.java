package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Quota TOCTOU / lost-update race: the invoice quota is a shared counter (invoices_created).
 * Firing many uploads concurrently tests whether that counter is incremented atomically. If it
 * isn't, concurrent increments can be lost — invoices_created ends up lower than the number of
 * uploads actually accepted, meaning a user silently gets MORE than their paid quota (a billing-
 * integrity bypass). The reverse (counter overshooting the cap) would be a correctness bug too.
 *
 * Fires N=15 simultaneous, distinctly-named uploads (unique content so the server's duplicate
 * detection doesn't collapse them) via Promise.all in one browser context, comparing quota before
 * vs. after. Multipart field name "file" and the ci_session cookie were confirmed via
 * UploadFieldReconTest. Costs up to N real quota — run deliberately (user-approved scale).
 */
public class QuotaRaceConditionTest extends BaseTest {

    private static final int CONCURRENT_UPLOADS = 15;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void concurrentUploads_quotaCounterIsAtomic_noLostUpdatesOrOvershoot() throws Exception {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();

        expect("Firing " + CONCURRENT_UPLOADS + " uploads simultaneously increments invoices_created " +
                "by EXACTLY the number of accepted (2xx) uploads — no lost updates (which would grant " +
                "free quota) and no overshoot past the cap. A mismatch indicates a non-atomic counter (TOCTOU).");

        String resultJson = (String) page.evaluate("""
            async (args) => {
              const [base, companyId, orgId, n] = args;
              const quotaUrl = `${base}/organizations/${orgId}/quota`;
              const uploadUrl = `${base}/companies/${companyId}/invoices/upload`;
              const before = await fetch(quotaUrl, {credentials: 'include'}).then(r => r.json());
              const uploads = [];
              for (let i = 0; i < n; i++) {
                const fd = new FormData();
                const content = '%PDF-1.4\\n% qa-race ' + i + ' ' + Math.random() + ' ' + Date.now() + '\\n%%EOF';
                fd.append('file', new Blob([content], {type: 'application/pdf'}), `qa-race-${i}.pdf`);
                uploads.push(
                  fetch(uploadUrl, {method: 'POST', body: fd, credentials: 'include'})
                    .then(r => r.status).catch(e => 'err:' + e)
                );
              }
              const statuses = await Promise.all(uploads);
              await new Promise(res => setTimeout(res, 2500)); // let the counter settle
              const after = await fetch(quotaUrl, {credentials: 'include'}).then(r => r.json());
              return JSON.stringify({before, after, statuses});
            }
            """, new Object[]{QaConfig.baseUrl(), QaConfig.companyId(), QaConfig.orgId(), CONCURRENT_UPLOADS});

        JsonNode result = MAPPER.readTree(resultJson);
        int createdBefore = result.path("before").path("invoices_created").asInt();
        int createdAfter = result.path("after").path("invoices_created").asInt();
        int remainingAfter = result.path("after").path("remaining").asInt();
        int quotaCap = result.path("after").path("invoice_quota").asInt();

        int accepted = 0;
        StringBuilder statusList = new StringBuilder();
        for (JsonNode s : result.path("statuses")) {
            statusList.append(s.asText()).append(" ");
            if (s.isInt() && s.asInt() >= 200 && s.asInt() < 300) accepted++;
        }

        int delta = createdAfter - createdBefore;
        actual("statuses: [" + statusList.toString().trim() + "] | accepted(2xx): " + accepted
                + " | invoices_created " + createdBefore + " -> " + createdAfter + " (delta " + delta + ")"
                + " | remaining after: " + remainingAfter + " | cap: " + quotaCap);

        recordDataFootprint("invoice", CONCURRENT_UPLOADS + "x concurrent qa-race-*.pdf",
                "akf00's organization", "Adam Internship QA",
                "quota race-condition test — " + accepted + " accepted, counter delta " + delta);

        if (delta < accepted) {
            recordFinding("HIGH",
                    "Quota counter is non-atomic: concurrent uploads caused lost updates (free quota)",
                    accepted + " uploads were accepted (2xx) but invoices_created only rose by " + delta +
                            " — " + (accepted - delta) + " increment(s) were lost to a race. A user can exceed " +
                            "their paid invoice quota by uploading concurrently (billing-integrity bypass).");
        } else if (remainingAfter < 0 || createdAfter > quotaCap) {
            recordFinding("MEDIUM",
                    "Quota counter overshoots: concurrent uploads pushed invoices_created past the cap / remaining negative",
                    "invoices_created=" + createdAfter + ", cap=" + quotaCap + ", remaining=" + remainingAfter);
        }

        assertEquals(accepted, delta,
                "invoices_created should increase by exactly the number of accepted uploads (atomic counter)");
    }
}
