package com.coresrp.qa.explore;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Captures the exact multipart field name the /invoices/upload endpoint expects, WITHOUT spending
 * quota: routes the upload request, reads its multipart body, then aborts it before it reaches the
 * server. Needed so the quota-race test can fire correctly-formed raw concurrent uploads.
 */
public class UploadFieldReconTest extends BaseTest {

    private static final Path OUT_DIR = Path.of(
            "/private/tmp/claude-501/-Users-adamfaouzi-Desktop-CoreSRP-Internship/b409d077-3ae2-4fc4-9bc0-794dec89819e/scratchpad/recon");

    @Test
    void captureUploadMultipartFieldName() throws IOException {
        StringBuilder captured = new StringBuilder();
        page.route("**/invoices/upload", route -> {
            try {
                String body = route.request().postData();
                captured.append("METHOD ").append(route.request().method()).append("\n");
                captured.append("HEADERS ").append(route.request().headers()).append("\n");
                captured.append("BODY (first 600 chars):\n")
                        .append(body == null ? "<null postData>" : body.substring(0, Math.min(600, body.length())));
            } catch (Exception e) {
                captured.append("error: ").append(e.getMessage());
            }
            route.abort(); // never reaches the server -> no quota consumed
        });

        DocumentsPage documents = loginAndOpenDocuments();
        page.locator("input[type=file]").setInputFiles(new FilePayload(
                "qa-fieldname-probe.pdf", "application/pdf", "%PDF-1.4\ntest\n%%EOF".getBytes()));
        page.waitForTimeout(2000);

        Files.createDirectories(OUT_DIR);
        Files.writeString(OUT_DIR.resolve("upload-fieldname.txt"), captured.toString());
    }
}
