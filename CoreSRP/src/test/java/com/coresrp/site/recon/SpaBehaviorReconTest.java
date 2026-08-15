package com.coresrp.site.recon;

import com.coresrp.site.base.SiteBaseTest;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

/** Read-only: what does the SPA render for an unknown path, and where do the CTA buttons actually go? */
public class SpaBehaviorReconTest extends SiteBaseTest {

    @Test
    void unknownPathClientRendering() {
        Response resp = page.navigate(BASE + "/qa-nonexistent-page-xyz");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        System.out.println("UNKNOWN PATH http status: " + (resp != null ? resp.status() : "n/a"));
        System.out.println("UNKNOWN PATH final url: " + page.url());
        System.out.println("UNKNOWN PATH title: " + page.title());
        System.out.println("UNKNOWN PATH body (first 400): " +
                page.locator("body").innerText().replaceAll("\\s+", " ").substring(0, Math.min(400,
                        page.locator("body").innerText().replaceAll("\\s+", " ").length())));
    }

    @Test
    void ctaClickDestinations() {
        for (String label : new String[]{"Sign In", "Get Started"}) {
            page.navigate(BASE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            try {
                page.getByText(label, new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first()
                        .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
                page.waitForTimeout(1500);
                System.out.println("CTA '" + label + "' -> " + page.url());
            } catch (Exception e) {
                System.out.println("CTA '" + label + "' -> click did nothing observable (" + e.getClass().getSimpleName() + ")");
            }
        }
    }
}
