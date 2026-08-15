package com.coresrp.qa.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Login form at /app/login (root URL is a marketing landing page — see Playwright LoginPage.java
 * for the same discovery). Verified selectors reused from that live inspection: type=email /
 * type=password inputs, submit button, redirect lands under /app/*.
 */
public class SeleniumLoginPage {

    private final WebDriver driver;

    public SeleniumLoginPage(WebDriver driver) {
        this.driver = driver;
    }

    public void login(String baseUrl, String email, String password) {
        driver.get(baseUrl + "/app/login");
        driver.findElement(By.cssSelector("input[type=email]")).sendKeys(email);
        driver.findElement(By.cssSelector("input[type=password]")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.urlContains("/app/"));
    }
}
