package com.coresrp.site.recon;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Read-only reconnaissance of coresrp.com — purely a normal visitor's-eye view: load the public
 * pages, capture structure (links, forms, tech fingerprint) and response/security headers. No
 * adversarial input, no load, no auth attempts. Output goes to CoreSRP/reports/recon/.
 *
 * Config: -Dcoresrp.base.url (default https://coresrp.com), -Dcoresrp.headless (default true).
 */
public class SiteReconTest {

    private static final String BASE = System.getProperty("coresrp.base.url", "https://coresrp.com");
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("coresrp.headless", "true"));
    private static final Path OUT = Path.of("reports/recon");

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        context = browser.newContext();
        context.setDefaultTimeout(20000);
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    @Test
    void reconHomepage() throws Exception {
        Files.createDirectories(OUT);
        StringBuilder sb = new StringBuilder();

        Response resp = page.navigate(BASE);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        sb.append("=== CoreSRP.com — read-only recon ===\n");
        sb.append("Requested: ").append(BASE).append("\n");
        sb.append("Final URL (after redirects): ").append(page.url()).append("\n");
        sb.append("HTTP status: ").append(resp != null ? resp.status() : "n/a").append("\n");
        sb.append("Title: ").append(page.title()).append("\n\n");

        // --- Response headers (the main document) — security-header hygiene is read-only + valuable ---
        sb.append("=== MAIN DOCUMENT RESPONSE HEADERS ===\n");
        Map<String, String> headers = resp != null ? resp.headers() : Map.of();
        headers.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));

        sb.append("\n=== SECURITY HEADER CHECK ===\n");
        for (String h : List.of("content-security-policy", "strict-transport-security", "x-frame-options",
                "x-content-type-options", "referrer-policy", "permissions-policy",
                "cross-origin-opener-policy", "x-xss-protection")) {
            sb.append("  ").append(h).append(": ")
              .append(headers.containsKey(h) ? "PRESENT (" + headers.get(h) + ")" : "MISSING").append("\n");
        }

        // --- Tech fingerprint (read-only observation) ---
        sb.append("\n=== TECH FINGERPRINT ===\n");
        sb.append("  Server header: ").append(headers.getOrDefault("server", "(none)")).append("\n");
        sb.append("  X-Powered-By: ").append(headers.getOrDefault("x-powered-by", "(none)")).append("\n");
        String generator = (String) page.evaluate(
                "() => document.querySelector('meta[name=generator]')?.content || '(no generator meta)'");
        sb.append("  Generator meta: ").append(generator).append("\n");
        String scriptHosts = (String) page.evaluate("""
            () => Array.from(new Set(Array.from(document.scripts)
                .map(s => { try { return new URL(s.src).host; } catch(e){ return null; } })
                .filter(Boolean))).join(', ')
            """);
        sb.append("  Script hosts (3rd-party fingerprint): ").append(scriptHosts).append("\n");

        // --- Links ---
        String links = (String) page.evaluate("""
            () => {
              const here = location.host;
              const as = Array.from(document.querySelectorAll('a[href]'));
              const norm = h => { try { return new URL(h, location.href).href; } catch(e){ return h; } };
              const all = as.map(a => norm(a.getAttribute('href')));
              const internal = [...new Set(all.filter(h => { try { return new URL(h).host === here; } catch(e){ return false; } }))];
              const external = [...new Set(all.filter(h => { try { return new URL(h).host !== here; } catch(e){ return false; } }))];
              return 'INTERNAL (' + internal.length + '):\\n' + internal.map(h=>'  '+h).join('\\n') +
                     '\\n\\nEXTERNAL (' + external.length + '):\\n' + external.map(h=>'  '+h).join('\\n');
            }
            """);
        sb.append("\n=== LINKS ===\n").append(links).append("\n");

        // --- Forms ---
        String forms = (String) page.evaluate("""
            () => {
              const fs = Array.from(document.querySelectorAll('form'));
              if (fs.length === 0) return '(no <form> elements found)';
              return fs.map((f,i) => {
                const fields = Array.from(f.querySelectorAll('input,select,textarea,button'))
                  .map(el => el.tagName.toLowerCase() + (el.type ? '['+el.type+']' : '') + (el.name ? ' name='+el.name : ''))
                  .join(', ');
                return `FORM ${i}: action="${f.getAttribute('action')||''}" method="${f.getAttribute('method')||'get'}"\\n  fields: ${fields}`;
              }).join('\\n');
            }
            """);
        sb.append("\n=== FORMS ===\n").append(forms).append("\n");

        // --- Body text preview ---
        sb.append("\n=== BODY TEXT (first 1500 chars) ===\n")
          .append(truncate(page.locator("body").innerText(), 1500)).append("\n");

        Files.writeString(OUT.resolve("coresrp-home.txt"), sb.toString());
        page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve("coresrp-home.png")).setFullPage(true));

        System.out.println(sb);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
