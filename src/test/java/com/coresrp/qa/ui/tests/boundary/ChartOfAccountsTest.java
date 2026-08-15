package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.ChartOfAccountsPage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Adversarial input into the Chart of accounts "Add" form. Additive/safe — no existing config touched. */
public class ChartOfAccountsTest extends BaseTest {

    @ParameterizedTest(name = "account name survives adversarial input: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "' OR '1'='1",
            "𝕬𝖉𝖆𝖒 💰 account"
    })
    void accountNameSurvivesAdversarialInput(String payload) {
        ChartOfAccountsPage chartOfAccounts = loginAndOpenChartOfAccounts();

        expect("Account name is stored/displayed as an opaque string, no raw script execution, no server error");
        chartOfAccounts.addAccount("QA" + System.nanoTime() % 100000, payload);
        page.waitForTimeout(1500);

        boolean scriptReflectedUnescaped = page.content().contains("<script>alert(1)</script>");
        if (scriptReflectedUnescaped) {
            recordFinding("HIGH", "Unsanitized chart-of-accounts name reflected back as raw HTML/script", payload);
        }
        assertFalse(scriptReflectedUnescaped);

        String bodyText = page.locator("body").innerText();
        actual("page body after adding account: " + truncate(bodyText, 300));
        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Adding a chart-of-accounts entry with adversarial input surfaced a raw server error", payload);
        }
        assertFalse(serverError);

        recordDataFootprint("chart-of-accounts-entry", payload, "akf00's organization", "Adam Internship QA",
                "adversarial account name test");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
