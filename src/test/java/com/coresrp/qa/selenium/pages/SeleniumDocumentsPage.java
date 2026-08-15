package com.coresrp.qa.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * The Documents page — selectors reused from the Playwright DocumentsPage.java live inspection
 * (search placeholder "acme...", Apply/Clear buttons, hidden file input). Login lands on
 * Overview, not Documents — goToDocuments() uses an in-app link click (not driver.get(), a hard
 * reload) to avoid the same post-login session_expired race documented on the Playwright side.
 */
public class SeleniumDocumentsPage {

    private final WebDriver driver;

    public SeleniumDocumentsPage(WebDriver driver) {
        this.driver = driver;
    }

    public void goToDocuments() {
        driver.findElement(By.linkText("Documents")).click();
        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='acme...']")));
    }

    public void search(String query) {
        driver.findElement(By.cssSelector("input[placeholder='acme...']")).sendKeys(query);
    }

    public void clickApply() {
        driver.findElement(By.xpath("//button[normalize-space()='Apply']")).click();
    }

    /**
     * Selenium's sendKeys() on a file input requires it to be "interactable" — the real input is
     * `hidden` (same discovery as the Playwright side), so make it visible via JS first. This is
     * a testing-only workaround; real users never see this input, they trigger it through the
     * "+ Upload invoices" button, which opens a native OS picker Selenium can't drive either.
     */
    public void uploadFile(String absoluteFilePath) {
        WebElement input = driver.findElement(By.cssSelector("input[type=file]"));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].removeAttribute('hidden'); arguments[0].style.display='block';", input);
        input.sendKeys(absoluteFilePath);
    }
}
