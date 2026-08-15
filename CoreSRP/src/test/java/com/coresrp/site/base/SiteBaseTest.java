package com.coresrp.site.base;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared Playwright lifecycle for the read-only coresrp.com suite. Provides a browser Page (for
 * DOM/rendering checks) and a lightweight APIRequestContext (for fast GET/HEAD link checks that
 * don't need a full page render). All read-only — normal-visitor GET requests only.
 */
public abstract class SiteBaseTest {

    protected static final String BASE = System.getProperty("coresrp.base.url", "https://coresrp.com");
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("coresrp.headless", "true"));

    private static Playwright playwright;
    private static Browser browser;
    protected static APIRequestContext api;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        api = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(BASE)
                .setIgnoreHTTPSErrors(false));
    }

    @AfterAll
    static void closeBrowser() {
        if (api != null) api.dispose();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        context = browser.newContext();
        context.setDefaultTimeout(20000);
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }
}
