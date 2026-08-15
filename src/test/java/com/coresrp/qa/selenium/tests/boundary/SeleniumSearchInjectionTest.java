package com.coresrp.qa.selenium.tests.boundary;

import com.coresrp.qa.selenium.base.SeleniumBaseTest;
import com.coresrp.qa.selenium.pages.SeleniumDocumentsPage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Same adversarial search-field coverage as the Playwright SearchInjectionTest, driven via Selenium. */
public class SeleniumSearchInjectionTest extends SeleniumBaseTest {

    @ParameterizedTest(name = "search field survives injection-style input: {0}")
    @ValueSource(strings = {
            "' OR '1'='1",
            "'; DROP TABLE invoices; --",
            "<script>alert(1)</script>",
            "\" onmouseover=\"alert(1)"
    })
    void searchFieldSurvivesInjectionStyleInput(String payload) {
        SeleniumDocumentsPage documents = loginAndOpenDocuments();

        expect("Search runs as a plain string filter: no results, no reflected/raw script execution, no 5xx");
        documents.search(payload);
        documents.clickApply();

        String bodyText = driver.findElement(org.openqa.selenium.By.tagName("body")).getText();
        actual("page body after search: " + truncate(bodyText, 300));

        boolean scriptReflectedUnescaped = driver.getPageSource().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized search input reflected back as raw HTML/script",
                    "Payload: " + payload + " — found verbatim <script> tag in page HTML after search.");
        }
        assertFalse(scriptReflectedUnescaped, "search payload should not be reflected as raw, unescaped HTML");

        boolean serverError = bodyText.toLowerCase().contains("internal server error")
                || bodyText.toLowerCase().contains("500")
                || bodyText.toLowerCase().contains("stack trace");
        if (serverError) {
            recordFinding("MEDIUM", "Search with adversarial input surfaced a raw server error to the user",
                    "Payload: " + payload);
        }
        assertFalse(serverError, "search should degrade gracefully, not surface a server error");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
