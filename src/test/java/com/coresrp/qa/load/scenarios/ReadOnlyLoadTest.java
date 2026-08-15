package com.coresrp.qa.load.scenarios;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.load.LoadResultWriter;
import com.coresrp.qa.report.RunContext;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.Test;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

/**
 * Read-only load/stress scenarios against the document-list endpoint
 * (GET /companies/{companyId}/invoices...). No uploads, no writes — nothing here consumes invoice
 * quota. Each thread logs in ONCE (onceOnlyController; jmeter-java-dsl gives each thread its own
 * cookie jar), then repeatedly hits the list endpoint for the scenario's duration.
 *
 * Deliberately CONSERVATIVE thread counts: this runs against the live production app (authorized
 * QA on our own product, but still a shared production system — the point is to find where latency
 * or error rate starts to degrade, not to DoS it). The named "list-read" sampler is reported in
 * isolation (stats.byLabel) so the once-per-thread login samples don't skew the GET percentiles;
 * the full per-request breakdown is in each scenario's linked JMeter HTML dashboard.
 *
 * Config (qa.properties / -D): qa.load.threads, qa.load.rampup.seconds, qa.load.duration.seconds,
 * qa.load.spike.threads. Run: mvn test -Pload-tests -Dtest=ReadOnlyLoadTest
 */
public class ReadOnlyLoadTest {

    private static final String LIST_LABEL = "list-read";

    private String listUrl() {
        return QaConfig.baseUrl() + "/companies/" + QaConfig.companyId()
                + "/invoices?sort_by=created_at&sort_dir=desc&limit=10&offset=0";
    }

    private us.abstracta.jmeter.javadsl.core.threadgroups.BaseThreadGroup.ThreadGroupChild loginOnce() {
        return onceOnlyController(
                httpSampler("login", QaConfig.baseUrl() + "/auth/cookie/login")
                        .post("username=" + QaConfig.loginEmail() + "&password=" + QaConfig.loginPassword(),
                                ContentType.APPLICATION_FORM_URLENCODED));
    }

    @Test
    void rampingReadOnly_findLatencyBaseline() throws IOException {
        int peak = QaConfig.getInt("qa.load.threads", 10);
        int rampSecs = QaConfig.getInt("qa.load.rampup.seconds", 15);
        int holdSecs = QaConfig.getInt("qa.load.duration.seconds", 45);
        Path dash = RunContext.loadDir().resolve("ramping-readonly");

        TestPlanStats stats = testPlan(
                threadGroup("ramping").rampToAndHold(peak, Duration.ofSeconds(rampSecs), Duration.ofSeconds(holdSecs))
                        .children(loginOnce(), httpSampler(LIST_LABEL, listUrl()).method("GET")),
                htmlReporter(dash.toString())
        ).run();

        LoadResultWriter.write("ramping-readonly", stats.byLabel(LIST_LABEL), stats.duration().toSeconds(), dash);
    }

    @Test
    void spikeReadOnly_suddenBurst() throws IOException {
        int spike = QaConfig.getInt("qa.load.spike.threads", 20);
        Path dash = RunContext.loadDir().resolve("spike-readonly");

        TestPlanStats stats = testPlan(
                threadGroup("spike")
                        .rampTo(spike, Duration.ofSeconds(2))   // sudden burst
                        .holdFor(Duration.ofSeconds(15))
                        .rampTo(0, Duration.ofSeconds(2))
                        .children(loginOnce(), httpSampler(LIST_LABEL, listUrl()).method("GET")),
                htmlReporter(dash.toString())
        ).run();

        LoadResultWriter.write("spike-readonly", stats.byLabel(LIST_LABEL), stats.duration().toSeconds(), dash);
    }

    @Test
    void soakReadOnly_sustainedModerateLoad() throws IOException {
        int threads = QaConfig.getInt("qa.load.soak.threads", 6);
        int soakSecs = QaConfig.getInt("qa.load.soak.seconds", 120); // shortened soak (2 min) — enough to spot obvious degradation
        Path dash = RunContext.loadDir().resolve("soak-readonly");

        TestPlanStats stats = testPlan(
                threadGroup("soak").rampToAndHold(threads, Duration.ofSeconds(10), Duration.ofSeconds(soakSecs))
                        .children(loginOnce(), httpSampler(LIST_LABEL, listUrl()).method("GET")),
                htmlReporter(dash.toString())
        ).run();

        LoadResultWriter.write("soak-readonly", stats.byLabel(LIST_LABEL), stats.duration().toSeconds(), dash);
    }
}
