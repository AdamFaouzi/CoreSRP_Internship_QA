package com.coresrp.qa.ui.pages;

import com.coresrp.qa.config.QaConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * Top nav + org/company switcher + free trial quota indicator.
 *
 * Verified live 2026-08-04 at desktop width (1280x720, Playwright's default): the header shows
 * three pills — "● {org name}" (static text, not clickable), "● {current company name} ▾" (a
 * button — this IS the switcher, there is no separately-labeled "Switch company" control), and
 * "● FREE TRIAL · {n} left" (a button). An earlier manual inspection at a narrower ~800px viewport
 * in the browser pane showed everything collapsed behind a hamburger with a menu item whose
 * accessible name really was "Switch company" — that markup may be mobile-only and wasn't
 * re-verified at 1280px (couldn't inspect logged-in without handling credentials). The company
 * pill is matched by its current text (either known company name) so this works at desktop
 * regardless; openNavIfCollapsed() falls back to the hamburger for the mobile case.
 */
public class NavBar extends BasePage {

    public NavBar(Page page) {
        super(page);
    }

    public void goToDocuments() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Documents")).click();
    }

    public void goToOverview() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Overview")).click();
    }

    public void goToPartners() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Partners")).click();
    }

    public void goToReconciliation() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Reconciliation")).click();
    }

    /** Opens the company switcher (the current-company pill) and selects a company by its visible name. */
    public void switchCompany(String companyName) {
        openNavIfCollapsed();
        companyPill().click();
        // force: true — the dropdown list re-renders on selection (Angular *ngFor), which makes
        // Playwright's actionability/stability check loop ("element was detached, retrying")
        // until timeout on a plain click. Verified live 2026-08-04.
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(companyName))
                .click(new Locator.ClickOptions().setForce(true));
    }

    public void searchCompanies(String query) {
        page.getByPlaceholder("Search companies…").fill(query);
    }

    /** e.g. "akf00's organization" — shown next to the company switcher, not itself a control. */
    public String currentOrgLabel() {
        openNavIfCollapsed();
        return page.locator("text=/.+'s organization/").first().innerText();
    }

    public String currentCompanyLabel() {
        openNavIfCollapsed();
        return companyPill().innerText();
    }

    /** "FREE TRIAL" quota button; backing call is GET /organizations/{orgId}/quota. */
    public void openQuotaDetails() {
        openNavIfCollapsed();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Free trial"))).click();
    }

    public void logout() {
        openNavIfCollapsed();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Account menu")).click();
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("Log out")).click();
    }

    private Locator companyPill() {
        // Not a semantic <button> — role-based lookup found nothing at desktop width
        // (verified live 2026-08-04); match on visible text instead, which works regardless.
        // Plain strings, not java.util.regex.Pattern: Pattern.quote()'s \Q...\E wrapping isn't
        // valid JS regex syntax, which is what Playwright actually evaluates in the browser —
        // silently matched nothing (confirmed live 2026-08-04, cost real debugging time).
        return page.getByText(QaConfig.companyName()).or(page.getByText(QaConfig.companyNameB())).first();
    }

    /** If the switcher isn't visible within a short wait, assume it's collapsed behind the hamburger and open it. */
    private void openNavIfCollapsed() {
        try {
            companyPill().waitFor(new Locator.WaitForOptions().setTimeout(1500));
        } catch (com.microsoft.playwright.TimeoutError e) {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Toggle navigation menu")).click();
            companyPill().waitFor(new Locator.WaitForOptions().setTimeout(5000));
        }
    }
}
