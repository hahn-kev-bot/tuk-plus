# Phase 0 – Foundations

Status: **done** (2026-09-27). Checked on the owner's Pixel 7a (Android 17).

## Goal

A project that builds, tests and logs, before any shop screens. Also a way to
check the live Tuk API without the app.

**Done when** (PLAN.md §8):

1. The API probe parses all Chiang Mai eateries and 25+ menus without errors.
2. The log export works.

Both are true: the probe passes 114 of 114 checks with 30 menus, and the owner
exported a log zip after a test crash.

## Scope

- Gradle project: Gradle 9.8, Android Gradle Plugin 9.4.1, Kotlin 2.4.20,
  Compose BOM 2026.09.00, Hilt 2.60.1. Application id `app.hahn.tukplus`,
  min SDK 26, target SDK 37.
- Modules:
  - `core:model`: API models with lenient serializers (prices can be text or
    numbers), page and menu parsers. Menu items keep their raw JSON for the web
    checkout fallback.
  - `core:network`: `TukApi` read client, auth header, retries for GET only
    (502/503/504 and no answer; never for 500, never for POST), redacted HTTP log,
    v1 device uuid.
  - `core:logging`: session log (PLAN.md §9).
  - `tools:api-probe`: the manual live API check.
  - `app`: Hilt setup, home screen with a backend check, Debug screen.
- Session log: one JSON Lines file per day, sessions inside, 10 MB per file,
  14 days kept, redaction of ids, phones, emails and account numbers, crash
  handler, zip export through the share sheet.
- `tools/logview/logview.py` to read exported logs.
- CI (GitHub Actions): unit tests, lint, debug APK on every push. A second
  workflow runs the API probe when started by hand.
- Shared debug signing key in `app/signing/` (added after the owner could not
  update the app over an earlier build).

## Decisions

| Decision | Why |
|---|---|
| Own small client on OkHttp, no Retrofit. | The API mixes JSON, plain text, `null` bodies and HTTP 500 answers that are not errors. |
| All automatic tests are logic only. The live API is checked only by hand. | Owner's rule. Tuk gives no test shops. |
| Fixtures recorded by the probe, with personal data removed. | The first recording had about 180 emails and 300 phone numbers, and customer block lists. |
| One log file per day, many sessions inside. | The owner asked for per session or per day; this gives both. |

## Facts found

- With one reused HTTP/2 connection a call takes about 0.3 s (the web app waits
  about 1 s per call, mostly for new TLS connections). On the owner's phone:
  577–787 ms with a new connection, 171 ms reused.
- `helpers/shop_open` answers HTTP 500 for a closed shop. That is a normal answer.
- Shop search with no match gives `{"businesses":null}`.
- Package codes seen: `r`, `p`, `f`, `apple`, `elderberry`, `fig`, `durian`.
- Opening hours come in about 25 text forms.

## Owner test list (done)

1. Install the debug APK. Home shows the backend version. ✅
2. Settings → Debug → Share this session. The zip opens and has the session file. ✅
3. Debug → Test the crash handler. At the next start the app asks to share logs. ✅
