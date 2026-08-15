package com.coresrp.qa.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Central config. Precedence (highest first): -D system property, env var
 * (dots -> underscores, upper-cased), qa.properties at repo root, qa.properties.example defaults.
 * qa.properties is gitignored so real credentials never get committed.
 */
public final class QaConfig {

    private static final Properties DEFAULTS = new Properties();
    private static final Properties LOCAL = new Properties();

    static {
        loadInto(DEFAULTS, Path.of("qa.properties.example"));
        loadInto(LOCAL, Path.of("qa.properties"));
    }

    private static void loadInto(Properties target, Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            target.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config file: " + path, e);
        }
    }

    private QaConfig() {
    }

    public static String get(String key) {
        String envKey = key.toUpperCase().replace('.', '_');
        String value = System.getProperty(key);
        if (value == null) value = System.getenv(envKey);
        if (value == null) value = LOCAL.getProperty(key);
        if (value == null) value = DEFAULTS.getProperty(key);
        return value;
    }

    public static String get(String key, String fallback) {
        String value = get(key);
        return value != null ? value : fallback;
    }

    public static int getInt(String key, int fallback) {
        String value = get(key);
        return value != null ? Integer.parseInt(value) : fallback;
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String value = get(key);
        return value != null ? Boolean.parseBoolean(value) : fallback;
    }

    public static String baseUrl() {
        return get("qa.base.url", "https://invoices.coresrp.com");
    }

    public static String envLabel() {
        return get("qa.env.label", "live-free-trial");
    }

    public static String loginEmail() {
        String v = get("qa.login.email");
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "qa.login.email not set. Set it in qa.properties (gitignored), " +
                "or pass -Dqa.login.email=... / env QA_LOGIN_EMAIL.");
        }
        return v;
    }

    public static String loginPassword() {
        String v = get("qa.login.password");
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "qa.login.password not set. Set it in qa.properties (gitignored), " +
                "or pass -Dqa.login.password=... / env QA_LOGIN_PASSWORD.");
        }
        return v;
    }

    /**
     * No standalone Chrome/Firefox app is installed on this machine (verified live 2026-08-06) —
     * only Safari. Defaults to the Chrome-for-Testing binary Playwright already downloaded for
     * its own use, so Selenium tests work without a separate browser install. Override via
     * -Dqa.selenium.chrome.binary=... if a real Chrome install becomes available.
     */
    public static String seleniumChromeBinary() {
        return get("qa.selenium.chrome.binary",
                System.getProperty("user.home") + "/Library/Caches/ms-playwright/chromium-1234/chrome-mac-arm64/"
                        + "Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
    }

    public static boolean headless() {
        return getBoolean("qa.headless", true);
    }

    public static double slowMoMs() {
        return getInt("qa.slowmo.ms", 0);
    }

    public static boolean screenshotOnFailure() {
        return getBoolean("qa.screenshot.on.failure", true);
    }

    public static double defaultTimeoutMs() {
        return getInt("qa.default.timeout.ms", 15000);
    }

    public static String orgId() {
        return get("qa.org.id");
    }

    public static String companyId() {
        return get("qa.company.id");
    }

    public static String companyName() {
        return get("qa.company.name", "Adam Internship QA");
    }

    public static String companyIdB() {
        return get("qa.company.id.b");
    }

    public static String companyNameB() {
        return get("qa.company.name.b", "QA Leakage Test Co");
    }

    /** A real, already-created "failed" test invoice in company A — safe to freely mutate for boundary tests. */
    public static String testInvoiceId() {
        return get("qa.test.invoice.id", "019fcc4f-7296-7b02-a24f-e71f4de73464");
    }

    public static Path reportsDir() {
        return Path.of(get("qa.reports.dir", "reports"));
    }
}
