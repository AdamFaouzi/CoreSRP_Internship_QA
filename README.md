# Core Invoice Adversarial QA

Solo adversarial QA suite for **Core Invoice** (`invoices.coresrp.com`): Playwright UI/functional
tests + JMeter (Java DSL) load/stress tests + a small representative Selenium suite, with a
unified HTML report generated after each run.

Runs against the **live free-trial environment** — there is no staging. Every invoice/document/
company created during a run is logged to the report's Data Footprint section.

## Setup

```bash
cp qa.properties.example qa.properties   # fill in qa.login.email / qa.login.password locally, gitignored
mvn -q -DskipTests dependency:resolve    # first-time dependency download
mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install  # install browser binaries
```

Every setting in `qa.properties.example` can be overridden with `-Dkey=value` or an env var
(`qa.base.url` -> `QA_BASE_URL`), without editing the file.

## Layout

- `src/test/java/.../ui/base` — `BaseTest` (Playwright lifecycle, login helper, `recordFinding`,
  `recordDataFootprint`), plus JUnit5 extensions that write structured results to `reports/<run>/ui/`.
- `src/test/java/.../ui/pages` — Page Object Model. **Selectors are TODO placeholders** until the
  live codegen walkthrough (see below) replaces them with verified ones.
- `src/test/java/.../ui/tests/{boundary,fileupload,multitenant,session,standardflow}` — adversarial
  test categories.
- `src/test/java/.../load/scenarios` — JMeter Java DSL scenarios (ramping, spike, soak, upload
  stress). Each calls `LoadResultWriter.write(...)` after `.run()` so its stats feed the report.
- `src/main/java/.../report` — `ReportAggregator`/`ReportMain`: merges `ui/results.jsonl`,
  `ui/findings.jsonl`, `data-footprint.jsonl` and `load/*-result.jsonl` into one `report.html`.
- `src/test/java/.../selenium` — a small representative suite (login flow, search injection,
  one upload test) alongside the primary Playwright suite, not full parity — mostly to
  demonstrate Selenium specifically. Writes into the same `ui/results.jsonl` etc., so it appears
  in the same unified report, tagged `<category> (selenium)`. No standalone Chrome/Firefox is
  installed on this machine, so `SeleniumBaseTest` points `ChromeOptions` at the Chrome-for-Testing
  binary Playwright already downloaded for itself (see `QaConfig.seleniumChromeBinary()`) —
  override with `-Dqa.selenium.chrome.binary=...` if a real Chrome install becomes available.

## Running

```bash
mvn test -Dtest=CompanySwitchTest                 # multi-tenant isolation — safe, no quota cost
mvn test -Dtest=SearchInjectionTest                # boundary/injection input — safe, no quota cost
mvn test -Dtest=UploadInvoicesTest#methodName      # ONE upload-abuse test at a time — spends quota
mvn test                                           # everything under ui/tests/** in one go — spends quota (uploads included)
mvn test -Pload-tests -Dtest=DocumentListLoadTest#loginFieldNamesFailFastProbe  # one-shot JMeter probe
mvn test -Pselenium-tests                          # the representative Selenium suite
mvn exec:java -Dexec.mainClass=com.coresrp.qa.report.ReportMain   # build report.html from whatever ran

# or, to share one run folder across all three:
scripts/run-full-suite.sh
```

Note: `mvn exec:java@<executionId>` (with the `@id` suffix) fails on this machine's Maven with a
plugin-resolution error even though the plugin's groupId is correct in the pom — a known Maven
CLI quirk. Drop the `@id` and pass `-Dexec.mainClass=...` directly instead, as above.

Reports land in `reports/<yyyy-MM-dd_HH-mm-ss>/report.html`, with raw JSONL/CSV and screenshots
alongside it.

## Selectors/endpoints

Page objects (`LoginPage`, `DocumentsPage`, `NavBar`) and the confirmed API endpoints were
captured live 2026-08-04 against the trial account — see each class's Javadoc for what's verified
vs. still assumed (e.g. `LoginPage`'s field names haven't been directly observed, only inferred
from the FastAPI-Users convention the login path implies).
