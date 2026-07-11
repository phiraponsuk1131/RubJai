# RubJai

RubJai is an Android income and expense tracker built with Kotlin and Jetpack Compose. Package: `app.rubjai.mobile`; minSdk 23; targetSdk 36; version 1.0.0.

## Features

- Add income, expenses, and salary manually.
- Select a slip image and extract Thai text/amount locally with ML Kit OCR.
- Receive text or slip images through Android Share, including content explicitly shared from LINE.
- Show income, expenses, and current balance; sync transactions to each signed-in user's Firestore path.
- Always show extracted data for confirmation before saving.
- Check the public RubJai GitHub Release at startup and offer an in-app APK download with progress.

RubJai cannot directly read private LINE chats. LINE does not expose a consumer API for another Android app to download personal bank chats. The safe supported flow is Share to RubJai. Notification or Accessibility scraping is intentionally excluded.

## Firebase setup — do this before GitHub upload

1. Open Firebase Console and create a new project, for example `rubjai-app`. Do not reuse ThaiGuard's project.
2. Add an Android app with package name exactly `app.rubjai.mobile`.
3. Download `google-services.json` and save it locally as `app/google-services.json`. It is ignored by Git.
4. In Authentication > Sign-in method, enable Anonymous. This MVP uses anonymous accounts; deleting/reinstalling the app can create a new account, so add a permanent sign-in method before relying on it for long-term backup.
5. Create Cloud Firestore, choose the region nearest your users, then deploy `firestore.rules` with `firebase deploy --only firestore:rules`.
6. In GitHub Secrets, add `GOOGLE_SERVICES_JSON_BASE64` containing the Base64 form of `google-services.json`.
7. Create one permanent release keystore and add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as repository secrets. Never commit the key.
8. Enable App Check with Play Integrity before public distribution and monitor Firestore quotas/costs.

PowerShell can create the Firebase secret value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app/google-services.json"))
```

## Firestore schema

`users/{uid}/transactions/{transactionId}` stores `id`, `amount`, `title`, `type` (`INCOME`/`EXPENSE`), `source`, limited `rawText`, and `createdAt`. Rules prevent users reading other users' data.

## Build

Use JDK 17 and Gradle 9.4.1:

```shell
gradle :app:assembleDebug
```

GitHub Actions builds a signed release APK on every push without running tests. A `v*` tag also creates a GitHub Release using `RELEASE_NOTES.md`.
The in-app updater reads `https://github.com/phiraponsuk1131/RubJai/releases/latest`; Actions artifacts alone do not trigger update popups. The release must contain a public APK and its tag must be newer than `versionName`.

## Privacy

Slip selection and LINE sharing are user-initiated. OCR runs on the device. Only a confirmed transaction and at most 3,000 characters of source text are uploaded to the user's Firestore area. RubJai never automatically scans all photos or reads LINE chats.
