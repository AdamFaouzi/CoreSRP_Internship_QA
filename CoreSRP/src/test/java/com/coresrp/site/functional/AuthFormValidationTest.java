package com.coresrp.site.functional;

import com.coresrp.site.base.SiteBaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Read-only functional QA of the /login form's client-side validation. Uses the browser's native
 * constraint-validation API (el.checkValidity() / el.validity) after setting field values — this
 * is pure client-side DOM inspection: NO form is submitted, no network request is made, no
 * credentials are attempted. It verifies the form's validation contract, not the auth backend.
 */
public class AuthFormValidationTest extends SiteBaseTest {

    @Test
    void loginEmailFieldEnforcesEmailFormat() {
        page.navigate(BASE + "/login");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        // Type an invalid email (no submit) and read the field's own validity state.
        boolean typeMismatch = (Boolean) page.evaluate("""
            () => {
              const el = document.querySelector('input[type=email][name=email]');
              if (!el) return false;
              el.value = 'not-an-email';
              el.dispatchEvent(new Event('input', {bubbles:true}));
              return el.validity.typeMismatch === true;
            }
            """);
        assertTrue(typeMismatch, "login email field should flag an invalid email format via HTML5 validation");
    }

    @Test
    void loginRequiredFieldsAreMarkedRequired() {
        page.navigate(BASE + "/login");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        boolean emailRequiredWhenEmpty = (Boolean) page.evaluate("""
            () => { const el = document.querySelector('input[type=email][name=email]');
                    if (!el) return false; el.value=''; return el.validity.valueMissing === true; }
            """);
        boolean passwordRequiredWhenEmpty = (Boolean) page.evaluate("""
            () => { const el = document.querySelector('input[type=password][name=password]');
                    if (!el) return false; el.value=''; return el.validity.valueMissing === true; }
            """);
        assertTrue(emailRequiredWhenEmpty, "login email should be a required field");
        assertTrue(passwordRequiredWhenEmpty, "login password should be a required field");
    }

    @Test
    void loginPasswordFieldIsMasked() {
        page.navigate(BASE + "/login");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        String type = (String) page.evaluate(
                "() => document.querySelector('input[name=password]')?.getAttribute('type')");
        assertEquals("password", type, "password field should be type=password (masked input)");
    }
}
