# Development

## Build

Requirements: JDK 17 or newer (CI uses 21) and the Android SDK (platform 37).
Put the SDK path in `local.properties` (`sdk.dir=/path/to/android-sdk`), or set `ANDROID_HOME`.

```sh
./gradlew test lintDebug assembleDebug     # what CI runs
./gradlew :app:installDebug                # install on a connected phone
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. CI also uploads it
as the `tukplus-debug-apk` artifact.

## Modules

| Module | Kind | Content |
|---|---|---|
| `app` | Android | Application, screens, DI (Hilt), Android parts of logging (crash handler, share) |
| `core:model` | Kotlin/JVM | API data classes, lenient serializers, page and menu parsers |
| `core:network` | Kotlin/JVM | `TukApi` client, interceptors (auth, retry, HTTP log), device uuid |
| `core:logging` | Kotlin/JVM | Session log, day files, redaction, zip export |
| `tools:api-probe` | Kotlin/JVM app | Manual check of the live read-only API |

## Tests

All automatic tests are logic only (PLAN.md §10). They do not use the internet.
`core:network` tests use a local MockWebServer.

`core:model` tests parse the responses in `core/model/src/test/resources/fixtures/live/`.
These were recorded from the live API by the probe.

## Live API probe (manual)

The probe calls only read-only GET endpoints. It checks that our models parse every
response, lists fields that our models do not read, and prints facts (hours formats,
package codes, payment options, response times).

```sh
./gradlew :tools:api-probe:run
./gradlew :tools:api-probe:run --args="--menus 40"
# Record new fixtures, then run the tests:
./gradlew :tools:api-probe:run --args="--record core/model/src/test/resources/fixtures/live"
./gradlew :core:model:test
```

The report of the last recording is `core/model/src/test/resources/fixtures/live/report.md`.
On GitHub: Actions → "API probe (manual)" → Run workflow. The report shows in the
run summary, and the recorded responses are an artifact.

## Logs

The app writes one JSON Lines file per day in its private folder (`files/logs/`).
Each line has the session id (`s`). See PLAN.md §9.

To get logs from a phone: in the app, open Settings → Debug and select
"Share this session", "Share today", "Share last 7 days", or one day. After a crash,
the app asks to share the logs at the next start.

To read an exported zip:

```sh
tools/logview/logview.py tukplus-logs-20260927-1531.zip --sessions
tools/logview/logview.py tukplus-logs-20260927-1531.zip --session k3x9qa
tools/logview/logview.py tukplus-logs-20260927-1531.zip --level W
tools/logview/logview.py tukplus-logs-20260927-1531.zip --tag net --event http --grep transactions --full
```

In debug builds the app also prints each log line to Logcat with the tag `TukPlus`.
