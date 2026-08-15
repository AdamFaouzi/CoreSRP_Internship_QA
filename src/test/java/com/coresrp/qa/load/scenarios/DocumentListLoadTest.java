package com.coresrp.qa.load.scenarios;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.load.LoadResultWriter;
import com.coresrp.qa.report.RunContext;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

import java.io.IOException;
import java.nio.file.Path;

import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

/**
 * Scenarios against the real document-list endpoint, verified live 2026-08-04:
 * GET /companies/{companyId}/invoices?sort_by=created_at&sort_dir=desc&limit=10&offset=0.
 * Auth is cookie-based (POST /auth/cookie/login, 204 on success); jmeter-java-dsl enables a
 * cookie manager per thread by default, so the login sampler's Set-Cookie is replayed on every
 * later request in that thread automatically.
 *
 * Requires qa.login.email / qa.login.password in your own local qa.properties (gitignored) or
 * QA_LOGIN_EMAIL / QA_LOGIN_PASSWORD env vars — never commit real credentials.
 */
public class DocumentListLoadTest {

    /**
     * Single request, 1 thread, no ramp: just confirms the assumed login field names
     * (username/password form-urlencoded, matching the FastAPI-Users default cookie-login
     * convention this path implies) actually work, since that couldn't be observed from browser
     * network history (method+URL+status only, no request body). Run this before ever enabling
     * the real ramping/spike/soak scenarios in this class.
     *
     * Run: mvn test -Pload-tests -Dtest=DocumentListLoadTest#loginFieldNamesFailFastProbe
     */
    @Test
    void loginFieldNamesFailFastProbe() throws IOException {
        Path dashboardDir = RunContext.loadDir().resolve("login-probe");
        String listUrl = QaConfig.baseUrl() + "/companies/" + QaConfig.companyId()
                + "/invoices?sort_by=created_at&sort_dir=desc&limit=10&offset=0";

        TestPlanStats stats = testPlan(
                threadGroup(1, 1,
                        httpSampler(QaConfig.baseUrl() + "/auth/cookie/login")
                                .post("username=" + QaConfig.loginEmail() + "&password=" + QaConfig.loginPassword(),
                                        ContentType.APPLICATION_FORM_URLENCODED),
                        httpSampler(listUrl).method("GET")
                ),
                htmlReporter(dashboardDir.toString())
        ).run();

        LoadResultWriter.write("login-probe", stats, dashboardDir);

        System.out.println("Login probe: " + stats.overall().samplesCount() + " samples, "
                + stats.overall().errorsCount() + " errors. Check " + dashboardDir + "/index.html "
                + "for the actual response codes/bodies per sampler (login vs. list).");
    }

    /**
     * The real ramping scenario, using configurable thread/rampup/duration. Stays @Disabled until
     * the login probe above has passed AND thread counts are agreed with the user — this hits the
     * live trial repeatedly, not a one-shot request.
     */
    @Test
    @Disabled("Enable deliberately once loginFieldNamesFailFastProbe passes and thread counts are agreed")
    void rampingLoadOnDocumentList() throws IOException {
        int threads = QaConfig.getInt("qa.load.threads", 5);
        int rampupSeconds = QaConfig.getInt("qa.load.rampup.seconds", 10);
        int durationSeconds = QaConfig.getInt("qa.load.duration.seconds", 60);

        Path dashboardDir = RunContext.loadDir().resolve("document-list-ramp");
        String listUrl = QaConfig.baseUrl() + "/companies/" + QaConfig.companyId()
                + "/invoices?sort_by=created_at&sort_dir=desc&limit=10&offset=0";

        TestPlanStats stats = testPlan(
                threadGroup()
                        .rampToAndHold(threads, java.time.Duration.ofSeconds(rampupSeconds), java.time.Duration.ofSeconds(durationSeconds))
                        .children(
                                httpSampler(QaConfig.baseUrl() + "/auth/cookie/login")
                                        .post("username=" + QaConfig.loginEmail() + "&password=" + QaConfig.loginPassword(),
                                                ContentType.APPLICATION_FORM_URLENCODED),
                                httpSampler(listUrl).method("GET")
                        ),
                htmlReporter(dashboardDir.toString())
        ).run();

        LoadResultWriter.write("document-list-ramp", stats, dashboardDir);
    }
}
