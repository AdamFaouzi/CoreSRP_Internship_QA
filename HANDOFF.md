# CoreSRP Internship QA — Session Handoff / History

**Purpose:** durable record of the full QA engagement so work can resume in a fresh session
(context-window handoff). Last updated: 2026-08-15.

---

## 1. What this engagement is

Solo adversarial QA + security testing, part of Adam Faouzi's internship at **CoreSRP** (a
"Service Resource Planning" SaaS platform, "Powered by Sprint Gates"). Two authorized targets:

- **`invoices.coresrp.com`** — the "Core Invoice" product (invoice capture / OCR / ERP-sync).
  Full adversarial testing. **This is where all the real findings are.**
- **`coresrp.com`** — the corporate site + auth front-end for the SRP platform. **Read-only /
  functional QA only** (adversarial testing of it was NOT authorized — would need explicit sign-off).

Everything runs against the **live free-trial environment** (no staging exists).

---

## 2. Repo / folder layout (all under `~/Desktop/CoreSRP_Internship/`)

```
CoreSRP_Internship/                 <- git repo (local), remote = github.com/AdamFaouzi/CoreSRP_Internship_QA (PRIVATE)
  pom.xml                           <- Core Invoice suite: single Maven project (Playwright + Selenium + JMeter DSL)
  qa.properties                     <- GITIGNORED. Real login creds + org/company IDs. NOT in repo.
  qa.properties.example             <- template (safe, committed)
  src/test/java/com/coresrp/qa/
    ui/          (Playwright: base, pages POM, tests/{boundary,fileupload,multitenant,session,standardflow})
    selenium/    (representative Selenium suite: base, pages, tests)
    load/scenarios/ (JMeter DSL: DocumentListLoadTest, ReadOnlyLoadTest)
    explore/     (one-off recon tests — excluded from report counts)
  src/main/java/com/coresrp/qa/report/  (ReportAggregator/ReportMain -> merges JSONL into report.html)
  reports/
    2026-08-04_consolidated/        <- THE deliverable: report.html + QA_SUMMARY.md + ui/ + load/ + data-footprint.jsonl
    core-invoice-qa-report_2026-08-04.zip  <- self-contained zip sent to supervisor
  test-data/                        <- synthetic upload files (valid/corrupted/zero-byte/wrong-type)
  scripts/run-full-suite.sh

  CoreSRP/                          <- SEPARATE Maven project for coresrp.com (read-only)
    pom.xml, README.md
    src/test/java/com/coresrp/site/{base,recon,functional}/
    reports/SITE_QA_SUMMARY.md, reports/recon/

  qa-video/                         <- the presentation video project
    make_cards.py                   <- generates evidence-card images (PIL)
    QAVID/                          <- HyperFrames project (brand.json, scenes.json, vo_script.txt, assets/)
    QAVID/core-invoice-qa-assessment.mp4   <- FINAL VIDEO (1080p, 68s, narrated)
```

**Nothing has been git-committed/pushed yet** — user never asked. Repo is initialized with the
remote set. If desired: branch, commit, push (creds/reports are gitignored).

---

## 3. Core Invoice results — 76 functional tests + 3 JMeter scenarios, 8 findings

Authoritative write-up: **`reports/2026-08-04_consolidated/QA_SUMMARY.md`** (read this first).
HTML report: `reports/2026-08-04_consolidated/report.html`.

**Findings (all confirmed live):**
1. **CSV / formula injection in exports (Medium)** — vendor name `=1+1+cmd|' /C calc'!A0` exported to CSV completely unescaped.
2. **Login rate-limiting gap (Medium)** — 6 sequential failed logins = all 400, no throttle; login DOES 429 under heavy concurrent load (found via JMeter). Threshold too high for low-volume brute force; no account lockout. Read-endpoint rate-limiting is *tighter* than login — inverted priority.
3. **Password-reset account enumeration (Medium)** — `POST /auth/forgot-password`: registered email → 202, unknown → 422.
4. **Negative total corrupts journal entries (Medium)** — negative `grand_total` on a non-return invoice → negative debit in journal_entries → feeds every GL export. (Confirmed the return/credit-note path IS correct, so this is a real bug not an alt path.)
5. **Upload endpoint accepts wrong content-type, consumes quota (Medium)** — text-file-as-PDF → 202, quota decremented, no content validation.
6. **Document filter intermittent race (Low/Med)** — dropdown value updates but Apply request often omits the param (~80% repro). Left as an intentionally-failing regression test.
7. **API key rejected even on its own endpoint (Low/info)** — freshly-created key 401s everywhere incl. its documented upload endpoint; no admin-scope leak though.
8. Process note: reviewed invoices lock **readonly** with no edit path.

**What was verified SOUND (clean negatives on critical surfaces):** no cross-tenant data leakage;
JWT `ci_session` forgery fully rejected (alg:none, tampered exp/sub, garbage sig — with positive
control); quota counter atomic under 15 concurrent uploads (no TOCTOU); no stored/reflected XSS;
delete-disabled enforced server-side; return/credit-note journal logic correct.

---

## 4. coresrp.com results — 15 functional tests, 2 minor observations

Write-up: **`CoreSRP/reports/SITE_QA_SUMMARY.md`**. It's an Angular SPA (nginx), well-hardened
(strong CSP, HSTS preload, X-Frame-Options DENY, etc.). Auth surface discovered: `/login`
(admin), `/client/login` (client portal), `/register` (multi-step), `/forgot-password` (reset-**code**).
Observations: (1) CSP allows `script-src 'unsafe-inline'`; (2) soft-404 (unknown paths → 200 →
redirect to /login). Link integrity clean. **Auth surface mapped but NOT adversarially tested.**

---

## 5. How to run

```bash
# Core Invoice (from repo root). Needs qa.properties with creds (gitignored).
mvn test -Dtest=CompanySwitchTest         # example: safe, no-quota UI test
mvn test -Pselenium-tests                 # Selenium suite
mvn test -Pload-tests -Dtest=ReadOnlyLoadTest#rampingReadOnly_findLatencyBaseline   # read-only load
mvn exec:java -Dexec.mainClass=com.coresrp.qa.report.ReportMain   # rebuild report.html
# NOTE: `mvn -Preport exec:java@aggregate-report` FAILS (Maven prefix quirk) — use -Dexec.mainClass.

# coresrp.com (from CoreSRP/ dir)
mvn test -Dtest='PageAndNavTest,AuthFormValidationTest,LinkIntegrityTest'

# Video (from qa-video/): see qa-video/QAVID; rebuild via catalog-promo-video skill pipeline.
```

---

## 6. Environment / constraints / credentials

- **Test account:** `akf00@aubmed.ac.cy` (account owner = QA account). Password in `qa.properties` only.
- **Org:** "akf00's organization" `019fcc2d-0a4f-74a3-99e7-ab78e2ead53a`.
  **Companies:** "Adam Internship QA" `019fcc2d-ba46-74d0-9909-eb849fa481df`;
  "QA Leakage Test Co" `019fcc67-0b4b-74a1-9c23-6329ee8c6cee` (created for the cross-tenant test).
- **Quota:** free trial, `invoice_quota`=50, ~20 remaining (upload tests consume it; `can_delete:false` = non-recoverable). **Statement uploads (Reconciliation) do NOT consume invoice quota.** Read-only tests are free.
- **Auth:** cookie-based, `POST /auth/cookie/login` (form: username/password) → 204. `ci_session` is an HS256 JWT.
- **Gemini API key** (for the video TTS): written to `~/.gemini_env` (chmod 600). Source file the user provided: `~/Desktop/Tezkar/Code/gemini api/api.rtf` (key format `AQ.…`).
- **Git remote** is PRIVATE. Do NOT push creds/reports (they're gitignored).

---

## 7. Hard-won gotchas (will save a future session hours)

- Login form is at **`/app/login`** (root is a marketing page). `getByLabel("Password")` collides with the "Show password" button → use `setExact(true)`.
- After login you land on **Overview**, not Documents. Use in-app **nav link clicks**, NOT `page.navigate()` — a hard nav right after login races the SPA auth state → `?reason=session_expired`.
- `getByLabel("Status")` fails (label not programmatically associated) → target the combobox by index.
- **`Pattern.quote()` breaks Playwright** — its `\Q…\E` isn't valid JS regex; use plain strings for `getByText`.
- Company switcher pill is **not a semantic `<button>`** → match by visible text.
- Invoice list/detail have **no `<table>`** markup (div-grid); the status badge is the row click target.
- **`.contains("500")`** is too loose for server-error detection (matches quota counters, prices) → check `"internal server error"`.
- `exec-maven-plugin` groupId is **`org.codehaus.mojo`**; pinned `jackson-core` and `commons-io` to resolve transitive conflicts from jmeter-java-dsl.
- Reviewed invoices are readonly; only **failed** invoices are editable (needed for amount/date boundary tests).
- No standalone Chrome installed → Selenium points at Playwright's Chrome-for-Testing binary (`QaConfig.seleniumChromeBinary()`).

---

## 8. What's pending / natural next steps

- **Nothing committed to git** — offer to commit/push if wanted.
- Video is **68s** (target was 60) — can trim ~15 words + re-render; or make a **vertical 9:16** cut.
- **Full amount/date boundary sweep** needs a fresh *failed* invoice (the editable one got used + locked).
- **coresrp.com adversarial testing** (incl. checking its `/forgot-password` for the same enumeration bug the invoices app had) — needs explicit "full adversarial" authorization.
- **True cross-tenant IDOR** needs a *second real user account* — user must create it (creating accounts is off-limits for the assistant).
- **JMeter soak + upload-stress** deliberately not run (soak = don't hammer a 429-ing prod system; upload-stress = quota).
- Supervisor was already sent `core-invoice-qa-report_2026-08-04.zip`.
