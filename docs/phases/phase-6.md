# Phase 6 – Polish and release

Status: **not started**.

## Goal

A release build that other people can use for a week without help.

**Done when** (PLAN.md §8): beta testers use it for a week.

## Scope

- Release build:
  - R8 (minify and shrink) with rules for kotlinx.serialization and Hilt;
  - a release signing key that is **not** in the repository (a GitHub Actions
    secret); the debug key in `app/signing/` stays for test builds only;
  - measure the start-up and parse times again in the release build (debug
    builds are much slower; see the `perf` and `cache` log events).
- Design: dark theme check on every screen, the screens that were not designed
  yet, the design page updated to the Lorikeet colors.
- Accessibility: TalkBack labels, touch targets of 48 dp, text scaling up to 200 %.
- Offline mode: every screen works with cached data and says so.
- Error reporting: a clear message for each kind of failure, and the log for details.
- Play Store: listing text, screenshots, privacy policy (the app keeps logs on
  the phone only; it sends nothing to us), data safety form.
- Before release: run the API probe, record new fixtures, run all tests.

Not in scope: other languages (Thai later), other regions.

## Draft owner test list

1. Install the release build from the Play Store internal test track.
2. Use every screen in dark mode.
3. Use the app with TalkBack for one order.
4. Use the app with flight mode on: cached screens work and say "Offline".
5. Give the app to two testers for a week. Collect their logs.
