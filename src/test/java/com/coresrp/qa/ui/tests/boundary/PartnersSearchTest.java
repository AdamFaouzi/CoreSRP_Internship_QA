package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.VendorsPage;
import com.microsoft.playwright.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Adversarial input into the Partners (vendors) search field. Mirrors SearchInjectionTest. */
public class PartnersSearchTest extends BaseTest {

    @ParameterizedTest(name = "vendor search survives injection-style input: {0}")
    @ValueSource(strings = {
            "' OR '1'='1",
            "'; DROP TABLE vendors; --",
            "<script>alert(1)</script>",
            "\" onmouseover=\"alert(1)",
            "𝕬𝖉𝖆𝖒 🧾 vendor"
    })
    void vendorSearchSurvivesInjectionStyleInput(String payload) {
        VendorsPage vendors = loginAndOpenPartners();

        expect("Search runs as a plain string filter: no results, no reflected/raw script execution, no 5xx");
        Response response = vendors.waitForVendorListResponse(() -> {
            vendors.search(payload);
            vendors.clickApply();
        });
        actual("vendor search response: " + response.status() + " " + response.url());

        boolean scriptReflectedUnescaped = page.content().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized vendor search input reflected back as raw HTML/script",
                    "Payload: " + payload + " — found verbatim <script> tag in page HTML after search.");
        }
        assertFalse(scriptReflectedUnescaped, "search payload should not be reflected as raw, unescaped HTML");

        String bodyText = page.locator("body").innerText().toLowerCase();
        boolean serverError = bodyText.contains("internal server error") || bodyText.contains("500")
                || bodyText.contains("stack trace");
        if (serverError) {
            recordFinding("MEDIUM", "Vendor search with adversarial input surfaced a raw server error",
                    "Payload: " + payload);
        }
        assertFalse(serverError, "vendor search should degrade gracefully, not surface a server error");
    }
}
