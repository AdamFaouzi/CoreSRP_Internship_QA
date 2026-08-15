package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.ApiKeysPage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Adversarial input into the API key name field (Settings > API keys). Creates real keys — low risk (no email/billing impact). */
public class ApiKeysTest extends BaseTest {

    @ParameterizedTest(name = "API key name survives adversarial input: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "' OR '1'='1",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "𝕬𝖉𝖆𝖒 🔑 key"
    })
    void apiKeyNameSurvivesAdversarialInput(String payload) {
        ApiKeysPage apiKeys = loginAndOpenApiKeys();

        expect("API key name is stored/displayed as an opaque string, no raw script execution, no server error");
        apiKeys.createKey(payload);
        page.waitForTimeout(1500);

        boolean scriptReflectedUnescaped = page.content().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized API key name reflected back as raw HTML/script",
                    "Payload: " + payload);
        }
        assertFalse(scriptReflectedUnescaped, "key name should not be reflected as raw, unescaped HTML");

        String bodyText = page.locator("body").innerText();
        actual("page body after creating key: " + truncate(bodyText, 300));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Creating an API key with adversarial name surfaced a raw server error", payload);
        }
        assertFalse(serverError, "API key creation should degrade gracefully, not surface a server error");

        recordDataFootprint("api-key", payload.length() > 60 ? payload.substring(0, 60) + "..." : payload,
                "akf00's organization", "Adam Internship QA", "adversarial API key name test");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
