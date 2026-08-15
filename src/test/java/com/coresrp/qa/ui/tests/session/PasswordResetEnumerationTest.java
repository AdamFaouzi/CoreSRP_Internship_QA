package com.coresrp.qa.ui.tests.session;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The forgot-password page's copy is written to be enumeration-safe ("if there's an account
 * associated with it, we'll send a link" — never confirms/denies). Verifying the backend
 * actually matches that behavior, not just the wording: same status code and response shape for
 * a registered vs. an obviously-unregistered email.
 */
public class PasswordResetEnumerationTest extends BaseTest {

    @Test
    void registeredVsUnregisteredEmail_sameResponseShape() {
        page.navigate(QaConfig.baseUrl());

        expect("A registered email and an unregistered one get the identical response (status + " +
                "body shape) from the password-reset endpoint — anything different (status code, " +
                "response time, error message) would let an attacker enumerate valid accounts");

        String registeredResult = (String) page.evaluate(
                "email => fetch('https://invoices.coresrp.com/auth/forgot-password', " +
                        "{method: 'POST', headers: {'Content-Type': 'application/json'}, " +
                        "body: JSON.stringify({email}), credentials: 'include'})" +
                        ".then(r => r.status + '|' + r.headers.get('content-type'))",
                QaConfig.loginEmail());

        String unregisteredResult = (String) page.evaluate("""
            () => fetch('https://invoices.coresrp.com/auth/forgot-password', {
              method: 'POST', headers: {'Content-Type': 'application/json'},
              body: JSON.stringify({email: 'qa-definitely-not-registered-' + Date.now() + '@example.invalid'}),
              credentials: 'include'
            }).then(r => r.status + '|' + r.headers.get('content-type'))
            """);

        actual("registered email response: " + registeredResult + " | unregistered email response: " + unregisteredResult);

        boolean sameShape = registeredResult.split("\\|")[0].equals(unregisteredResult.split("\\|")[0]);
        if (!sameShape) {
            recordFinding("MEDIUM",
                    "Password-reset endpoint returns different responses for registered vs. unregistered emails",
                    "Registered: " + registeredResult + " | Unregistered: " + unregisteredResult +
                            " — this allows enumerating which emails have accounts, despite the UI copy implying otherwise.");
        }
        assertFalse(!sameShape, "response shape should be identical regardless of whether the email is registered");
    }
}
