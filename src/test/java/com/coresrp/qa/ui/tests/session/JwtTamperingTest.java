package com.coresrp.qa.ui.tests.session;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session cookie `ci_session` is a signed JWT (HS256, per its header — confirmed live). If
 * the server doesn't rigorously validate the signature and algorithm, an attacker can forge a
 * session for any user. This tests the classic JWT attack surface against the real endpoint,
 * using the tester's OWN valid token as the base and tampering it:
 *
 *   1. alg:none forgery — re-encode with {"alg":"none"} and an empty signature (the canonical
 *      "does the library accept unsigned tokens" bypass).
 *   2. Tampered exp, original signature kept — extends session lifetime; must be rejected because
 *      changing the payload invalidates the HS256 signature.
 *   3. Tampered sub (user id), garbage signature — impersonation attempt; must be rejected.
 *   4. Garbage signature on the untouched payload — sanity check that the signature is checked.
 *
 * Each tampered token is planted as the ci_session cookie in a fresh, otherwise-cookieless
 * context, then used to call GET /users/me. A 200 returning a real user identity = auth bypass
 * (critical). A 401/403 = the token was correctly rejected. No quota consumed.
 */
public class JwtTamperingTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    @Test
    void tamperedJwtSessionTokens_areAllRejected() throws Exception {
        loginAsDefaultUser();
        new com.coresrp.qa.ui.pages.NavBar(page).goToDocuments();

        // Grab the real, valid session token.
        String realToken = page.context().cookies(QaConfig.baseUrl()).stream()
                .filter(c -> c.name.equals("ci_session"))
                .map(c -> c.value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ci_session cookie not found after login"));

        String[] parts = realToken.split("\\.");
        String origHeader = parts[0], origPayload = parts[1], origSig = parts.length > 2 ? parts[2] : "";
        JsonNode payload = MAPPER.readTree(new String(B64URL_DEC.decode(origPayload), StandardCharsets.UTF_8));

        // --- Build tampered variants ---
        String noneHeader = B64URL.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String algNoneToken = noneHeader + "." + origPayload + ".";

        ObjectNode extendedExp = payload.deepCopy();
        extendedExp.put("exp", 9999999999L);
        String tamperedExpToken = origHeader + "." + enc(extendedExp) + "." + origSig; // orig sig, changed payload

        ObjectNode impersonate = payload.deepCopy();
        impersonate.put("sub", "00000000-0000-4000-8000-000000000000");
        String tamperedSubToken = origHeader + "." + enc(impersonate) + ".ZmFrZXNpZw"; // garbage sig

        String garbageSigToken = origHeader + "." + origPayload + ".AAAAAAAAAAAAAAAAAAAAAA";

        // Positive control: the REAL token, planted the same way into a fresh context, MUST return
        // 200 — otherwise the 401s below could just mean the mechanism itself doesn't work, making
        // the whole test meaningless.
        String controlOutcome = tryTokenAgainstUsersMe(realToken);
        assertTrue(controlOutcome.startsWith("200") && controlOutcome.contains("\"email\""),
                "positive control failed: the real token should authenticate via the same mechanism " +
                        "(otherwise the tampered-token 401s prove nothing). Got: " + controlOutcome);

        expect("Every tampered/forged JWT is rejected by GET /users/me (401/403) — none should return " +
                "a 200 with a real user identity, which would prove the signature/alg isn't validated. " +
                "(Positive control confirmed the real token DOES authenticate via the same path.)");

        String realSubShort = payload.path("sub").asText().substring(0, 8);
        StringBuilder report = new StringBuilder();
        boolean anyBypass = false;

        record Variant(String name, String token) {}
        for (Variant v : List.of(
                new Variant("alg:none forgery", algNoneToken),
                new Variant("tampered exp + original sig", tamperedExpToken),
                new Variant("tampered sub + garbage sig", tamperedSubToken),
                new Variant("garbage sig on real payload", garbageSigToken))) {

            String outcome = tryTokenAgainstUsersMe(v.token());
            report.append(v.name()).append(" => ").append(outcome).append("  |  ");
            // A bypass = a 200 whose body looks like a real user record.
            if (outcome.startsWith("200") && outcome.contains("\"email\"")) {
                anyBypass = true;
                recordFinding("HIGH",
                        "JWT session token not properly validated — a tampered/forged token was accepted (auth bypass): " + v.name(),
                        "GET /users/me with a " + v.name() + " token returned " + outcome +
                                " — the server accepted a token it should have rejected. This is a critical " +
                                "authentication bypass (forge a session for any user).");
            }
        }

        actual("positive control (real token): " + controlOutcome + " | real token sub starts " + realSubShort
                + " | tampered results: " + report);
        assertFalse(anyBypass, "no tampered/forged JWT should be accepted by an authenticated endpoint");
    }

    /** Plants the given token as ci_session in a fresh context and calls /users/me. Returns "status|bodyPrefix". */
    private String tryTokenAgainstUsersMe(String token) {
        try (BrowserContext ctx = newExtraContext()) {
            ctx.addCookies(List.of(new Cookie("ci_session", token)
                    .setDomain("invoices.coresrp.com").setPath("/")));
            Page p = ctx.newPage();
            p.navigate(QaConfig.baseUrl() + "/app/login"); // land on-origin without needing a valid session
            return (String) p.evaluate(
                    "base => fetch(base + '/users/me', {credentials: 'include'})" +
                            ".then(async r => r.status + '|' + (await r.text()).slice(0, 120))",
                    QaConfig.baseUrl());
        }
    }

    private static String enc(JsonNode node) throws Exception {
        return B64URL.encodeToString(MAPPER.writeValueAsBytes(node));
    }
}
