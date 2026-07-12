# RubJai

RubJai is an Android income, expense, and debt-payoff tracker built with Kotlin and Jetpack Compose. Package: `app.rubjai.mobile`; minSdk 23; targetSdk 36; version 1.3.0.

## Features

- Add income or salary directly as a number; income does not require a slip.
- Expenses come from a user-selected slip. RubJai automatically maps the merchant name, positive decimal amount, time, category, and remark; no amount input is shown for scanned expenses.
- Non-slip images or images with no readable positive amount are rejected instead of opening an empty amount form.
- Receive text or slip images through Android Share, including content explicitly shared from LINE.
- Register or sign in with email/password, confirm Firebase's email verification link, and edit display name and phone in a scroll-safe profile dialog.
- Show income, expenses, and current balance; sync transactions to each signed-in user's Firestore path.
- Always show extracted data for confirmation before saving.
- Optional daily K PLUS-only gallery scanning. New results stay in a local approval queue until the user confirms and uploads the transaction or rejects it.
- Check the public RubJai GitHub Release at startup and offer an in-app APK download with progress.
- Filter the scrollable transaction history by today, week, month, or all records and by income or expense.
- Create debts, apply a monthly payment from a selected slip, reject duplicate slips, show payment history with date/time, estimate payoff months from the latest payment and annual interest, and show progress encouragement.

RubJai cannot directly read private LINE chats. LINE does not expose a consumer API for another Android app to download personal bank chats. The safe supported flow is Share to RubJai. Notification or Accessibility scraping is intentionally excluded.

## Firebase setup — do this before GitHub upload

1. Open Firebase Console and create a new project, for example `rubjai-app`. Do not reuse ThaiGuard's project.
2. Add an Android app with package name exactly `app.rubjai.mobile`.
3. Download `google-services.json` and save it locally as `app/google-services.json`. It is ignored by Git.
4. In Authentication > Sign-in method, enable Email/Password. Google and Anonymous are not used. Firebase sends a secure verification link; numeric OTP is not used.
5. Create Cloud Firestore, choose the region nearest your users, then deploy `firestore.rules` with `firebase deploy --only firestore:rules`.
6. In GitHub Secrets, add `GOOGLE_SERVICES_JSON_BASE64` containing the Base64 form of `google-services.json`.
7. Create one permanent release keystore and add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as repository secrets. Never commit the key.
8. Enable App Check with Play Integrity before public distribution and monitor Firestore quotas/costs.

PowerShell can create the Firebase secret value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app/google-services.json"))
```

## Firestore schema

`users/{uid}` stores the user's editable profile. Transactions live under `transactions`. Debt plans live under `debts/{debtId}`, with immutable slip payments under `debts/{debtId}/payments/{slipFingerprint}`. Rules prevent cross-user reads. Collections are created automatically.

## Build

Use JDK 17 and Gradle 9.4.1:

```shell
gradle :app:assembleDebug
```

GitHub Actions builds a signed release APK on every push without running tests. A `v*` tag also creates a GitHub Release using `RELEASE_NOTES.md`.
Before secrets exist, Actions creates a debug artifact with a build-only Firebase placeholder. Configure the Firebase and signing secrets before distributing or tagging; only the signed build with the real Firebase config is a production APK.
The in-app updater reads `https://github.com/phiraponsuk1131/RubJai/releases/latest`; Actions artifacts alone do not trigger update popups. The release must contain a public APK and its tag must be newer than `versionName`.

## Privacy

Manual slip selection and LINE sharing are user-initiated. The optional K PLUS daily scanner requires explicit photo permission, checks only recent images on the device, and can be disabled at any time. OCR and the pending approval queue remain on-device. Only a user-confirmed transaction and at most 3,000 characters of source text are uploaded to the user's Firestore area. RubJai never reads LINE chats.

Admin-only destructive controls are hidden unless the signed-in Firebase ID token contains the custom claim `admin: true`. Admin passwords are never stored in source code, Firestore, GitHub, or the APK.
