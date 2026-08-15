package com.coresrp.qa.explore;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One-off recon: capture the forgot-password page structure and its backing endpoint. */
public class ForgotPasswordReconTest extends BaseTest {

    private static final Path OUT_DIR = Path.of(
            "/private/tmp/claude-501/-Users-adamfaouzi-Desktop-CoreSRP-Internship/b409d077-3ae2-4fc4-9bc0-794dec89819e/scratchpad/recon");

    @Test
    void exploreForgotPassword() throws IOException {
        java.util.List<String> networkLog = new java.util.ArrayList<>();
        page.onRequest(req -> networkLog.add(req.method() + " " + req.url()));

        page.navigate(QaConfig.baseUrl() + "/app/forgot-password");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        networkLog.clear();
        page.locator("input[type=email]").fill(QaConfig.loginEmail());
        page.locator("button[type=submit]").click();
        page.waitForTimeout(2000);

        String interactiveElements = (String) page.evaluate("""
            () => {
              const sel = 'a,button,input,select,textarea,[role],[onclick]';
              return Array.from(document.querySelectorAll(sel)).map(el => {
                const tag = el.tagName.toLowerCase();
                const type = el.getAttribute('type') || '';
                const placeholder = el.getAttribute('placeholder') || '';
                const text = (el.innerText || el.value || '').trim().slice(0, 60).replace(/\\s+/g, ' ');
                return `${tag}${type ? '[type='+type+']' : ''} placeholder="${placeholder}" text="${text}"`;
              }).join('\\n');
            }
        """);

        String body = String.join("\n",
                "URL: " + page.url(),
                "=== INTERACTIVE ELEMENTS ===",
                interactiveElements,
                "=== NETWORK ===",
                String.join("\n", networkLog),
                "=== BODY TEXT ===",
                page.locator("body").innerText()
        );

        Files.createDirectories(OUT_DIR);
        Files.writeString(OUT_DIR.resolve("forgot-password.txt"), body);
    }
}
