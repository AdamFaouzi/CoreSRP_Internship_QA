package com.coresrp.qa.explore;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One-off: figure out how to open an invoice's detail/edit view from the Documents list. */
public class InvoiceDetailReconTest extends BaseTest {

    private static final Path OUT_DIR = Path.of(
            "/private/tmp/claude-501/-Users-adamfaouzi-Desktop-CoreSRP-Internship/b409d077-3ae2-4fc4-9bc0-794dec89819e/scratchpad/recon");

    @Test
    void openFirstInvoiceRow() throws IOException {
        List<String> networkLog = new ArrayList<>();
        page.onRequest(req -> networkLog.add(req.method() + " " + req.url()));

        DocumentsPage documents = loginAndOpenDocuments();

        // No <table> markup at all (div/grid-based "table look") — walk up from a status badge
        // to find the row-like ancestor container instead.
        String rowInfo = (String) page.evaluate("""
            () => {
              const badges = Array.from(document.querySelectorAll('*')).filter(
                el => el.children.length === 0 && el.textContent.trim() === 'failed'
              );
              if (badges.length === 0) return 'NO "failed" LEAF ELEMENT FOUND';
              let el = badges[0];
              let path = [];
              for (let i = 0; i < 6 && el; i++) {
                path.push(`<${el.tagName.toLowerCase()} class="${el.className}">`.slice(0, 150));
                el = el.parentElement;
              }
              return 'ancestor chain from "failed" badge:\\n' + path.join('\\n');
            }
        """);
        Files.createDirectories(OUT_DIR);
        Files.writeString(OUT_DIR.resolve("invoice-row-html.txt"), rowInfo);

        networkLog.clear();

        // getByText also matches the hidden <option value="4: failed"> inside the Status filter
        // <select> (earlier in DOM order) — use .last() to land on an actual visible row badge.
        page.getByText("failed", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                .last().click();
        page.waitForTimeout(1500);

        // Vendor field is readonly (discovered) — click it to see what opens (autocomplete/picker?).
        page.locator("input[placeholder='—']").first().click();
        page.waitForTimeout(1000);
        String afterVendorClick = (String) page.evaluate("""
            () => {
              const sel = 'a,button,input,select,textarea,[role],[onclick],li';
              return Array.from(document.querySelectorAll(sel)).slice(0, 100).map(el => {
                const tag = el.tagName.toLowerCase();
                const text = (el.innerText || el.value || '').trim().slice(0, 60).replace(/\\s+/g, ' ');
                return `${tag} text="${text}"`;
              }).join('\\n');
            }
        """);
        Files.writeString(OUT_DIR.resolve("invoice-vendor-click.txt"), afterVendorClick);

        String interactiveElements = (String) page.evaluate("""
            () => {
              const sel = 'a,button,input,select,textarea,[role],[onclick]';
              return Array.from(document.querySelectorAll(sel)).slice(0, 250).map(el => {
                const tag = el.tagName.toLowerCase();
                const type = el.getAttribute('type') || '';
                const role = el.getAttribute('role') || '';
                const aria = el.getAttribute('aria-label') || '';
                const placeholder = el.getAttribute('placeholder') || '';
                const text = (el.innerText || el.value || '').trim().slice(0, 60).replace(/\\s+/g, ' ');
                return `${tag}${type ? '[type='+type+']' : ''}${role ? ' role='+role : ''} aria="${aria}" placeholder="${placeholder}" text="${text}"`;
              }).join('\\n');
            }
        """);

        String body = String.join("\n",
                "URL after clicking first row: " + page.url(),
                "",
                "=== INTERACTIVE ELEMENTS ===",
                interactiveElements,
                "",
                "=== NETWORK ===",
                String.join("\n", networkLog),
                "",
                "=== BODY TEXT (first 3000 chars) ===",
                truncate(page.locator("body").innerText(), 3000)
        );

        Files.createDirectories(OUT_DIR);
        Files.writeString(OUT_DIR.resolve("invoice-detail.txt"), body);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
