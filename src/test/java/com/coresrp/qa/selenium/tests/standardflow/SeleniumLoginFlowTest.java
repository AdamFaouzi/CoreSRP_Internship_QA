package com.coresrp.qa.selenium.tests.standardflow;

import com.coresrp.qa.selenium.base.SeleniumBaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Golden-path login, driven via Selenium instead of Playwright — same app, different tool. */
public class SeleniumLoginFlowTest extends SeleniumBaseTest {

    @Test
    void loginLandsInAppWithNoServerError() {
        loginAsDefaultUser();

        expect("Login redirects into the app (Overview by default) with no raw server error");
        String bodyText = driver.findElement(org.openqa.selenium.By.tagName("body")).getText();
        actual("URL after login: " + driver.getCurrentUrl() + " | body starts: " + truncate(bodyText, 150));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        assertFalse(serverError, "login should never surface a raw server error");
        assertTrue(driver.getCurrentUrl().contains("/app/"), "should land somewhere under /app/ after login");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
