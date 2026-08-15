package com.coresrp.qa.explore;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.ApiKeysPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One-off: capture the real API key creation response (the secret is shown once — need its shape). */
public class ApiKeyReconTest extends BaseTest {

    private static final Path OUT_DIR = Path.of(
            "/private/tmp/claude-501/-Users-adamfaouzi-Desktop-CoreSRP-Internship/b409d077-3ae2-4fc4-9bc0-794dec89819e/scratchpad/recon");

    @Test
    void createKeyAndCaptureResponse() throws IOException {
        List<String> bodies = new ArrayList<>();
        page.onResponse(res -> {
            if (res.url().contains("api-keys") && res.request().method().equals("POST")) {
                try {
                    bodies.add(res.status() + " " + res.text());
                } catch (Exception e) {
                    bodies.add("error reading body: " + e.getMessage());
                }
            }
        });

        ApiKeysPage apiKeys = loginAndOpenApiKeys();
        apiKeys.createKey("QA Recon Key");
        page.waitForTimeout(2000);

        String bodyText = page.locator("body").innerText();
        Files.createDirectories(OUT_DIR);
        Files.writeString(OUT_DIR.resolve("api-key-creation.txt"),
                "RESPONSE BODIES:\n" + String.join("\n", bodies) + "\n\nPAGE BODY:\n" + bodyText);
    }
}
