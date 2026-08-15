package com.coresrp.site.functional;

import com.coresrp.site.base.SiteBaseTest;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Read-only functional QA: pages load, primary CTAs navigate correctly, anchors exist, HTTPS + 404 behavior. */
public class PageAndNavTest extends SiteBaseTest {

    @ParameterizedTest(name = "page loads with content: {0}")
    @ValueSource(strings = {"/", "/privacy-policy", "/terms-of-use", "/login", "/register", "/forgot-password"})
    void keyPagesReturn200WithContent(String path) {
        APIResponse resp = api.get(path);
        assertTrue(resp.ok(), path + " should return 2xx, got " + resp.status());
        assertTrue(resp.text().length() > 200, path + " should return a non-trivial body");
    }

    @Test
    void signInCtaNavigatesToLogin() {
        page.navigate(BASE);
        page.getByText("Sign In", new Page.GetByTextOptions().setExact(true)).first().click();
        page.waitForURL("**/login", new Page.WaitForURLOptions().setTimeout(8000));
        assertTrue(page.url().endsWith("/login"), "Sign In CTA should navigate to /login; got " + page.url());
    }

    @Test
    void getStartedCtaNavigatesToRegister() {
        page.navigate(BASE);
        page.getByText("Get Started", new Page.GetByTextOptions().setExact(true)).first().click();
        page.waitForURL("**/register", new Page.WaitForURLOptions().setTimeout(8000));
        assertTrue(page.url().endsWith("/register"), "Get Started CTA should navigate to /register; got " + page.url());
    }

    @Test
    void homepageAnchorSectionsExist() {
        page.navigate(BASE);
        assertTrue((Boolean) page.evaluate("() => !!document.getElementById('features')"),
                "#features anchor target should exist");
        assertTrue((Boolean) page.evaluate("() => !!document.getElementById('how-it-works')"),
                "#how-it-works anchor target should exist");
    }

    @Test
    void httpRedirectsToHttps() {
        page.navigate(BASE.replace("https://", "http://"));
        assertTrue(page.url().startsWith("https://"),
                "http:// should redirect to https://; ended at " + page.url());
    }

    /**
     * Observation, not a hard bug: unknown paths return HTTP 200 (SPA index fallback) and the client
     * router then redirects to /login rather than rendering a proper 404 view. This is a soft-404 —
     * common for SPAs, but it's an SEO concern (search engines may index non-existent URLs as valid)
     * and a mild UX oddity (an unknown page silently sends visitors to login). The test only asserts
     * the server doesn't 5xx; the soft-404 itself is documented in the QA notes.
     */
    @Test
    void unknownPathDoesNotServerError_softly404sToLogin() {
        Response resp = page.navigate(BASE + "/qa-nonexistent-" + System.nanoTime());
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        int status = resp != null ? resp.status() : -1;
        System.out.println("Unknown-path HTTP status: " + status + ", client landed on: " + page.url());
        assertTrue(status < 500, "unknown path should not 5xx; got " + status);
    }
}
