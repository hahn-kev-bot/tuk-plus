# Signing

`debug.keystore` signs all debug builds, on every computer and in CI. So a new
debug APK installs over the old one (Android refuses an update with another signature).

- Store password: `android`, key alias: `androiddebugkey`, key password: `android`.
- The key is public, because it is in the repository. Use it only for debug and
  test builds.

The release key for the Play Store (phase 6) must not be in the repository. It
will be a GitHub Actions secret.
