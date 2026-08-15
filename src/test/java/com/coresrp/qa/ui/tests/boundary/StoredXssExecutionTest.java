package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.microsoft.playwright.Dialog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Earlier tests confirmed adversarial input isn't REFLECTED unescaped in the immediate response.
 * This goes further: several `&lt;script&gt;alert(1)&lt;/script&gt;` payloads are now STORED in the
 * account (API key names, chart-of-accounts entries, company names — created during earlier
 * boundary tests). Stored XSS is more serious than reflected: it fires whenever any user renders
 * the affected view, not just the attacker.
 *
 * The definitive execution test: register a JS dialog handler up front, then visit each page that
 * renders those stored values. If the browser actually parses and executes a stored script, its
 * alert(1) triggers a dialog and we catch it. No dialog = the framework escaped it safely (Angular
 * escapes interpolated text by default, so the expected result is "no execution" — but "expected"
 * isn't "verified", and a single innerHTML/bypassSecurityTrust slip anywhere would break it).
 *
 * Reuses already-stored data — no new entries, no quota.
 */
public class StoredXssExecutionTest extends BaseTest {

    @Test
    void storedScriptPayloads_doNotExecuteWhenTheirViewsRender() {
        List<String> dialogsFired = new ArrayList<>();
        page.onDialog(dialog -> {
            dialogsFired.add(dialog.type() + ": " + dialog.message());
            dialog.dismiss();
        });

        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();

        expect("None of the stored <script>alert(1)</script> payloads (in API key names, " +
                "chart-of-accounts entries, company names) execute as script when their view renders — " +
                "a fired alert() dialog would prove stored XSS");

        String[] payloadRenderingPages = {
                "/app/settings/api-keys",         // stored key named <script>alert(1)</script>
                "/app/settings/chart-of-accounts",// stored account named <script>alert(1)</script>
                "/app/settings/companies",        // stored company named <script>alert(1)</script>
        };
        for (String path : payloadRenderingPages) {
            page.navigate(QaConfig.baseUrl() + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            page.waitForTimeout(1000);
        }

        // Also open the company switcher menu — company names render there too (it's how the
        // stored <script> company name first became visible in earlier recon).
        try {
            new com.coresrp.qa.ui.pages.NavBar(page).currentCompanyLabel();
        } catch (Exception ignored) {
        }
        page.waitForTimeout(1000);

        // Cross-check: is the payload present in the DOM as escaped text (safe) rather than a live
        // element? If querySelectorAll('script') contains one whose text is exactly alert(1) AND it
        // sits inside a data container, that'd be suspicious — but the dialog check above is the
        // real verdict.
        Object liveInjectedScriptCount = page.evaluate("""
            () => Array.from(document.querySelectorAll('script'))
                    .filter(s => s.textContent.trim() === 'alert(1)').length
        """);

        actual("dialogs fired across all payload-rendering views: " + dialogsFired
                + " | live <script>alert(1)</script> elements in DOM: " + liveInjectedScriptCount);

        if (!dialogsFired.isEmpty()) {
            recordFinding("HIGH",
                    "Stored XSS: a saved <script> payload executed when its view rendered",
                    "Dialog(s) fired: " + dialogsFired + " — a stored script payload (API key / " +
                            "chart-of-accounts / company name) is being executed, not escaped. Stored XSS " +
                            "affects every user who views the page, not just the attacker.");
        }
        assertFalse(!dialogsFired.isEmpty(), "no stored payload should execute as script (no alert dialog should fire)");
    }
}
