package com.coresrp.qa.explore;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.NavBar;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-off recon: not a real test (no assertions). Logs in once and walks Partners,
 * Reconciliation, and every Settings sub-page, dumping page structure + network calls to files
 * for selector capture — same purpose as manually driving the browser pane, without needing a
 * live logged-in session handed over each time. Output goes to the scratchpad, not the repo.
 */
public class ReconTest extends BaseTest {

    private static final Path OUT_DIR = Path.of(
            "/private/tmp/claude-501/-Users-adamfaouzi-Desktop-CoreSRP-Internship/b409d077-3ae2-4fc4-9bc0-794dec89819e/scratchpad/recon");

    @Test
    void exploreNewAreas() throws IOException {
        List<String> networkLog = new ArrayList<>();
        page.onRequest(req -> networkLog.add(req.method() + " " + req.url()));

        loginAsDefaultUser();
        NavBar nav = new NavBar(page);

        dump("overview", () -> nav.goToOverview(), networkLog);
        dump("partners", () -> nav.goToPartners(), networkLog);
        dump("reconciliation", () -> page.navigate(QaConfig.baseUrl() + "/app/reconciliation"), networkLog);

        String invoiceJson = (String) page.evaluate(
                "() => fetch('https://invoices.coresrp.com/invoices/" + QaConfig.testInvoiceId() + "', "
                        + "{credentials: 'include'}).then(r => r.text())");
        Files.writeString(OUT_DIR.resolve("test-invoice-current-state.json"), invoiceJson);

        String statementsJson = (String) page.evaluate(
                "() => fetch('https://invoices.coresrp.com/companies/" + QaConfig.companyId() + "/statements', "
                        + "{credentials: 'include'}).then(r => r.text())");
        Files.writeString(OUT_DIR.resolve("statements-list.json"), statementsJson);

        for (var entry : SETTINGS_PAGES.entrySet()) {
            dump("settings-" + entry.getKey(), () -> page.navigate(QaConfig.baseUrl() + entry.getValue()), networkLog);
        }
    }

    private static final java.util.LinkedHashMap<String, String> SETTINGS_PAGES = new java.util.LinkedHashMap<>();

    static {
        SETTINGS_PAGES.put("gl-accounts", "/app/settings/gl-accounts");
        SETTINGS_PAGES.put("chart-of-accounts", "/app/settings/chart-of-accounts");
        SETTINGS_PAGES.put("members", "/app/settings/members");
        SETTINGS_PAGES.put("api-keys", "/app/settings/api-keys");
        SETTINGS_PAGES.put("retention", "/app/settings/retention");
        SETTINGS_PAGES.put("audit-log", "/app/settings/audit-log");
    }

    private void dump(String label, Runnable navigate, List<String> networkLog) throws IOException {
        networkLog.clear();
        navigate.run();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        String interactiveElements = (String) page.evaluate("""
            () => {
              const sel = 'a,button,input,select,textarea,[role],[onclick]';
              return Array.from(document.querySelectorAll(sel)).slice(0, 200).map(el => {
                const tag = el.tagName.toLowerCase();
                const type = el.getAttribute('type') || '';
                const role = el.getAttribute('role') || '';
                const aria = el.getAttribute('aria-label') || '';
                const placeholder = el.getAttribute('placeholder') || '';
                const id = el.id || '';
                const text = (el.innerText || el.value || '').trim().slice(0, 60).replace(/\\s+/g, ' ');
                return `${tag}${type ? '[type='+type+']' : ''}${role ? ' role='+role : ''} id="${id}" aria="${aria}" placeholder="${placeholder}" text="${text}"`;
              }).join('\\n');
            }
        """);

        String body = String.join("\n",
                "URL: " + page.url(),
                "TITLE: " + page.title(),
                "",
                "=== INTERACTIVE ELEMENTS ===",
                interactiveElements,
                "",
                "=== NETWORK (this page) ===",
                String.join("\n", networkLog),
                "",
                "=== BODY TEXT (first 2000 chars) ===",
                truncate(page.locator("body").innerText(), 2000)
        );

        Files.createDirectories(OUT_DIR);
        Files.writeString(OUT_DIR.resolve(label + ".txt"), body);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
