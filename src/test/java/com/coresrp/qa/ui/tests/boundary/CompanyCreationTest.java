package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Adversarial input into Settings > Companies "Add a company" name field. Additive/safe. */
public class CompanyCreationTest extends BaseTest {

    @ParameterizedTest(name = "company name survives adversarial input: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "' OR '1'='1"
    })
    void companyNameSurvivesAdversarialInput(String payload) {
        loginAndOpenSettingsPage("/app/settings/companies");

        expect("Company name is stored/displayed as an opaque string, no raw script execution, no server error");
        page.getByPlaceholder("e.g. Sprint Gates SAL").fill(payload);
        page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Create")).click();
        page.waitForTimeout(1500);

        boolean scriptReflectedUnescaped = page.content().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized company name reflected back as raw HTML/script", payload);
        }
        assertFalse(scriptReflectedUnescaped);

        String bodyText = page.locator("body").innerText();
        actual("page body after creating company: " + truncate(bodyText, 300));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Creating a company with adversarial input surfaced a raw server error", payload);
        }
        assertFalse(serverError);

        recordDataFootprint("company", payload, "akf00's organization", null, "adversarial company name test");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
