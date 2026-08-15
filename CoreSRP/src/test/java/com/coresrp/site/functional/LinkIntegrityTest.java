package com.coresrp.site.functional;

import com.coresrp.site.base.SiteBaseTest;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broken-link integrity: collect every internal link + resolvable asset reference from the key
 * pages, then GET each once and flag anything that returns 404 or 5xx. Classic read-only
 * functional QA — nothing here is adversarial or repeated-enough to be load.
 */
public class LinkIntegrityTest extends SiteBaseTest {

    private static final List<String> SEED_PAGES = List.of("/", "/privacy-policy", "/terms-of-use", "/login", "/register");

    @Test
    void noInternalLinkOrAssetReturns404Or5xx() {
        Set<String> internalTargets = new LinkedHashSet<>();

        for (String seed : SEED_PAGES) {
            page.navigate(BASE + seed);
            @SuppressWarnings("unchecked")
            List<String> found = (List<String>) page.evaluate("""
                () => {
                  const here = location.host;
                  const urls = new Set();
                  const add = h => { try { const u = new URL(h, location.href); if (u.host === here) urls.add(u.pathname + u.search); } catch(e){} };
                  document.querySelectorAll('a[href]').forEach(a => add(a.getAttribute('href')));
                  document.querySelectorAll('link[href]').forEach(l => add(l.getAttribute('href')));
                  document.querySelectorAll('script[src]').forEach(s => add(s.getAttribute('src')));
                  document.querySelectorAll('img[src]').forEach(i => add(i.getAttribute('src')));
                  return Array.from(urls);
                }
                """);
            internalTargets.addAll(found);
        }

        // Drop pure in-page anchors (e.g. "/#features") — those are section jumps, not separate resources.
        internalTargets.removeIf(t -> t.equals("/#") || t.startsWith("/#") || t.isBlank());

        TreeMap<String, Integer> broken = new TreeMap<>();
        int checked = 0;
        for (String target : internalTargets) {
            try {
                APIResponse resp = api.get(target);
                int st = resp.status();
                checked++;
                if (st == 404 || st >= 500) broken.put(target, st);
            } catch (Exception e) {
                broken.put(target, -1); // request itself failed (DNS/timeout/etc.)
            }
        }

        System.out.println("Checked " + checked + " internal targets across " + SEED_PAGES.size() + " pages.");
        System.out.println("Targets: " + internalTargets);
        if (!broken.isEmpty()) System.out.println("BROKEN: " + broken);

        assertTrue(broken.isEmpty(), "no internal link/asset should return 404 or 5xx; broken = " + broken);
    }
}
