package com.coresrp.qa.ui.tests.session;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Is there any brute-force protection on login? Deliberately bounded to 6 failed attempts (not
 * an aggressive/prolonged attack) against the real account, followed immediately by a real login
 * to confirm we haven't locked ourselves out of the account we're actively using for everything
 * else in this suite.
 */
public class LoginRateLimitTest extends BaseTest {

    @Test
    void repeatedFailedLoginAttempts_checkForRateLimitingOrLockout() {
        page.navigate(QaConfig.baseUrl());

        expect("Repeated failed login attempts against a real account either get rate-limited " +
                "(429) or locked out after some threshold — checking what actually happens, since " +
                "unlimited attempts with no throttling would mean the login form is brute-forceable");
        String statusesJson = (String) page.evaluate("""
            async (email) => {
              const results = [];
              for (let i = 0; i < 6; i++) {
                const res = await fetch('https://invoices.coresrp.com/auth/cookie/login', {
                  method: 'POST',
                  headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                  body: `username=${encodeURIComponent(email)}&password=WrongPassword${i}!Qa`,
                  credentials: 'include'
                });
                results.push(res.status);
              }
              return JSON.stringify(results);
            }
            """, QaConfig.loginEmail());
        actual("status codes across 6 failed attempts: " + statusesJson);

        boolean anyRateLimitSignal = statusesJson.contains("429") || statusesJson.contains("423");
        if (!anyRateLimitSignal) {
            recordFinding("MEDIUM",
                    "No rate limiting or account lockout observed after 6 rapid failed login attempts",
                    "Status codes: " + statusesJson + ". All returned the same 4xx with no 429 (Too Many " +
                            "Requests) or 423 (Locked) signal, suggesting the login endpoint may be " +
                            "brute-forceable with enough attempts/time. Not conclusive from 6 attempts alone " +
                            "— a longer/slower probe would be needed to confirm, but worth flagging.");
        }

        // Confirm we haven't locked ourselves out before anything else in the suite runs.
        loginAsDefaultUser();
        assertTrue(page.url().contains("/app/"), "real login should still succeed after the failed attempts above");
    }
}
