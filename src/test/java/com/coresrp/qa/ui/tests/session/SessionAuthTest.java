package com.coresrp.qa.ui.tests.session;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.coresrp.qa.ui.pages.LoginPage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Session/auth boundary tests: expired session mid-action, concurrent logins, quota exhaustion. */
public class SessionAuthTest extends BaseTest {

    @Test
    void sessionClearedMidAction_redirectsToLoginNotCrash() {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("With the session cookie cleared mid-session, the next action redirects to login " +
                "(possibly with a session_expired reason, as seen after the earlier hard-navigation " +
                "bug) rather than crashing or silently showing stale/wrong data");
        page.context().clearCookies();
        page.reload();
        // waitForLoadState() (default 'load') fires before the Angular app finishes bootstrapping
        // and redirecting on a 401 — wait for the network to go quiet instead, matching the app's
        // actual async auth-check-then-redirect flow.
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        String url = page.url();
        String bodyText = page.locator("body").innerText();
        actual("URL after clearing cookies + reload: " + url + " | body starts: " + truncate(bodyText, 150));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Clearing session cookie surfaced a raw server error instead of a login redirect",
                    "URL: " + url + " body: " + truncate(bodyText, 300));
        }
        assertFalse(serverError, "an expired/cleared session should redirect to login, not crash");
        assertTrue(url.contains("/app/login") || url.equals(QaConfig.baseUrl() + "/") || url.equals(QaConfig.baseUrl()),
                "should land back on login or the marketing root once the session is gone, not stay on an authed page");
    }

    @Test
    void concurrentLoginsSameUser_bothSessionsRemainUsable() {
        // Primary session (this.page/context, from BaseTest) + a second independent context.
        DocumentsPage documentsA = loginAndOpenDocuments();

        try (BrowserContext contextB = newExtraContext()) {
            Page pageB = contextB.newPage();
            new LoginPage(pageB).login(QaConfig.baseUrl(), QaConfig.loginEmail(), QaConfig.loginPassword());

            expect("Logging in a second time with the same user (separate cookie jar) does not " +
                    "invalidate the first session — both remain independently usable");

            // Session B: confirm it can load Documents. In-app nav click, not page.navigate()
            // (hard reload) — that races the SPA's post-login auth state into session_expired,
            // the exact bug already fixed for the primary session in BaseTest.loginAndOpenDocuments().
            new com.coresrp.qa.ui.pages.NavBar(pageB).goToDocuments();
            boolean sessionBWorks;
            try {
                pageB.getByPlaceholder("acme...").waitFor();
                sessionBWorks = true;
            } catch (com.microsoft.playwright.TimeoutError e) {
                sessionBWorks = false;
            }

            // Session A: confirm the original session still works after B logged in.
            page.reload();
            page.waitForLoadState();
            boolean sessionAStillWorks = page.url().contains("/app/") && !page.url().contains("/app/login");

            actual("session B loaded Documents: " + sessionBWorks + "; session A still authenticated after B's login: " + sessionAStillWorks);

            if (!sessionAStillWorks) {
                recordFinding("LOW",
                        "Logging in from a second session invalidated the first session (single-session-per-user enforcement)",
                        "Not necessarily a bug — may be intentional — but worth confirming with the team whether " +
                                "concurrent sessions are meant to be supported.");
            }
            assertTrue(sessionBWorks, "the second concurrent login should be able to use the app");
        }
    }

    @Test
    @Disabled("Would require exhausting real invoice quota (dozens of uploads) just to reach 0 " +
            "remaining — expensive and slow to run routinely. Enable deliberately when the trial " +
            "quota is naturally close to empty, or run a dedicated one-off session for it.")
    void freeTrialQuotaExhaustion_uploadBlockedWithClearMessage() {
        // TODO when quota is nearly exhausted: upload until GET /organizations/{orgId}/quota
        // reports remaining=0, then attempt one more upload and assert it's rejected with a clear
        // message (not a silent failure or a raw 500), and that the UI's "FREE TRIAL Â· 0 left"
        // state communicates this to the user.
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
