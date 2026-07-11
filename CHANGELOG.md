# Changelog

## 2026-07-11

- Created the separate RubJai Android project with package `app.rubjai.mobile`.
- Added Compose dashboard, manual income/expense entry, salary-compatible income entry, and Firestore sync.
- Added Thai ML Kit OCR for user-selected slip images and Android Share handling for LINE text.
- Added confirmation before saving extracted values and documented the LINE API limitation.
- Added owner-only Firestore rules, Firebase setup guide, privacy boundaries, launcher icon assets, and GitHub Actions for signed APK artifacts and tagged releases.
- GitHub upload is pending because the local GitHub CLI authentication token is invalid and Firebase/signing secrets must be created first.
- Added numeric GitHub Release update checks, a scroll-safe update popup, in-app download progress, and Android package installation handoff.
- Replaced the launcher artwork with the user-provided black-and-gold wallet, coins, receipt, and income/expense arrows image without visual alteration.
- Fixed the unavailable ML Kit Thai artifact by using the published on-device text recognition package; documented that Thai labels may require correction.
- Added GitHub Actions debug/placeholder fallback so every push can produce an APK before owner secrets exist; production and tagged distribution still require real Firebase and signing secrets.
- Fixed the missing closing scope in the transaction row composable found by GitHub Actions compilation.
- Added email/password registration and sign-in, Google sign-in integration with safe missing-OAuth handling, and optional anonymous trial access.
- Added a profile dialog for editing display name and optional phone, signing out, and storing owner-only profile data in Firestore.
- Confirmed the Firebase project `rubjai-60e6d` and default Firestore database; collections are created automatically by the app.
- Confirmed Firestore security rules were published and replaced the Firebase config with the post-Google-provider file containing an OAuth web client for Google sign-in.
