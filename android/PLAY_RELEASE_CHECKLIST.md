# Android Play Release Checklist

This checklist separates repository-verifiable release gates from owner-only
Google Play and physical-device steps. Do not commit keystores, passwords, or
service-account JSON.

## Repository gates

- [x] Target Android 16 / API 36; minimum API 26.
- [x] Endpoint parity check passes for the pinned Hermes server contract.
- [x] Unit tests, Android lint, debug APK, and release AAB build successfully.
- [x] The connected-emulator UI suite covers core navigation, chat, selectors,
  sharing, deep links, localization, and session refresh.
- [x] Generated Android localization catalog stays aligned with the iOS source
  catalog across 18 locales.
- [x] Native libraries are packaged on 16 KiB boundaries.
- [x] Secure/session state is excluded from backup and device transfer.
- [x] No advertising, analytics, or third-party crash-reporting SDK is bundled.
- [x] Release notes exist for the current `versionCode`.

## Permission and data review

Hermex declares only these user-facing capabilities:

- Internet and network state for the user-selected self-hosted server.
- Notifications and a data-sync foreground service for stream recovery.
- Microphone access for dictation and voice notes.
- Legacy external-storage compatibility only through Android 9 (`maxSdkVersion=28`).

The app stores server entries, custom headers, and authentication cookies in
Android encrypted storage. Chat content and uploads are sent to the server the
user configures; the app does not contain an operator analytics backend.

Before production, the owner must make the matching declarations in Play
Console's Data safety and App content forms and publish a privacy-policy URL.
Those declarations depend on how the distributed app and its supported server
service are operated, so they cannot be submitted from repository code alone.

## Owner-only gates

- [ ] Enroll the Play app in Play App Signing and provision a protected upload key.
- [ ] Configure the four `HERMEX_ANDROID_*` signing variables.
- [ ] Configure `SUPPLY_JSON_KEY` with a Play Console service account.
- [ ] Provide production phone screenshots, feature graphic, app category,
  contact details, privacy-policy URL, content rating, and Data safety answers.
- [ ] Upload a signed AAB to the internal track and resolve Play pre-launch report findings.
- [ ] Run the full manual checklist on at least one physical phone, including
  microphone, notifications, sharing, file/photo upload, background recovery,
  light/dark mode, rotation, largest font, and accessibility services.
- [ ] Promote through a staged production rollout with a documented rollback build.

## Commands

```powershell
cd android
./gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease connectedDebugAndroidTest
bundle exec fastlane android internal
```

The Fastlane upload command intentionally fails when signing or Play credentials
are absent.

## Direct GitHub distribution

For the no-fee distribution route, the repository's named-release workflow now
builds a production-signed APK and AAB using protected GitHub Actions secrets.
The permanent signing key is also stored locally under
`%USERPROFILE%\.hermex-signing`; it must be backed up securely and reused for
every future APK update.
