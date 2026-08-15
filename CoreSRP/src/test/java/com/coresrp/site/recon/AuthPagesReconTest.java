package com.coresrp.site.recon;

import com.coresrp.site.base.SiteBaseTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only: dump the form structure + HTML5 validation attributes of /login and /register. No submissions. */
public class AuthPagesReconTest extends SiteBaseTest {

    @Test
    void dumpAuthPageForms() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String path : new String[]{"/login", "/register", "/forgot-password"}) {
            page.navigate(BASE + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            sb.append("\n===== ").append(path).append(" (final ").append(page.url()).append(", title '")
              .append(page.title()).append("') =====\n");
            String fields = (String) page.evaluate("""
                () => {
                  const els = Array.from(document.querySelectorAll('input,select,textarea,button,a'));
                  return els.map(el => {
                    const tag = el.tagName.toLowerCase();
                    const type = el.getAttribute('type') || '';
                    const name = el.getAttribute('name') || '';
                    const req = el.hasAttribute('required');
                    const ml = el.getAttribute('minlength') || '';
                    const ph = el.getAttribute('placeholder') || '';
                    const txt = (el.innerText||'').trim().slice(0,40);
                    return `${tag}${type?'['+type+']':''}${name?' name='+name:''}${req?' required':''}${ml?' minlength='+ml:''}${ph?' ph="'+ph+'"':''}${txt?' text="'+txt+'"':''}`;
                  }).join('\\n');
                }
                """);
            sb.append(fields).append("\n");
        }
        Path out = Path.of("reports/recon/auth-pages.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        System.out.println(sb);
    }
}
