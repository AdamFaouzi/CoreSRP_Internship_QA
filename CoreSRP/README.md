# CoreSRP.com — Site QA

Read-only reconnaissance + functional QA of the **CoreSRP corporate site** (`coresrp.com`).
Separate, self-contained Maven project from the Core Invoice app suite in the parent folder.

## Scope (important)

- **Authorized**, read-only / functional depth **only** (confirmed 2026-08-12).
- **In scope:** loading public pages, mapping structure, checking links/forms/navigation, observing
  response/security headers, and client-side (HTML5) form-validation behavior.
- **Deliberately NOT done** (would need explicit "full adversarial" authorization, which was deferred):
  injection payloads, brute-force / credential testing, load/stress, account creation, form
  submissions, or any probing of the authenticated app / admin / client-portal areas.
- No form is ever submitted; validation is checked via the browser's native `checkValidity()` API
  (pure client-side DOM, no network, no credentials).

## Layout

- `src/test/java/.../recon` — one-off recon (homepage structure + headers, SPA behavior, auth-page
  form structure). Output → `reports/recon/`.
- `src/test/java/.../functional` — functional QA: page loads, CTA navigation, link integrity,
  HTML5 form validation, HTTPS/404 behavior.
- `reports/SITE_QA_SUMMARY.md` — findings + what the site is.

## Running

```bash
mvn test -Dtest=SiteReconTest          # homepage recon (headers, links, tech fingerprint)
mvn test -Dtest='PageAndNavTest,AuthFormValidationTest,LinkIntegrityTest'   # functional suite
```

Config: `-Dcoresrp.base.url` (default `https://coresrp.com`), `-Dcoresrp.headless` (default `true`).
Reuses the Playwright browser binaries already installed by the parent project.
