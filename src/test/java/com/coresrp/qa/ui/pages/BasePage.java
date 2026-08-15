package com.coresrp.qa.ui.pages;

import com.microsoft.playwright.Page;

/** Common helpers shared by every page object. */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }
}
