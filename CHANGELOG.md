# Changelog

## 1.3.2

- Switched launcher and Recent Apps metadata to the adaptive `mipmap` icon instead of scaling the raw PNG as a legacy drawable.
- Reused the supplied square RubJai artwork for adaptive and legacy launchers without changing its aspect ratio.
- Skipped previously processed MediaStore images before OCR, recognized K PLUS `DTF` references, and raised the bounded daily coverage to 200 new images.
- Ensured the ML Kit recognizer and MediaStore cursor close safely when scanning fails or permissions change.
- Refactored scanner scheduling and processing branches for clearer maintenance without changing the approval requirement.
- Replaced automatic periodic scanning with an immediate `Sync today` button; first use requires an explicit consent dialog and consent can be revoked.
- Shortened current release notes so the in-app update popup shows only essential changes.

## 1.3.1

- Expanded K PLUS recipient extraction to use the recipient block before the second masked account, covering personal transfers as well as known merchants.
- Added a safe account-ending fallback when Thai recipient text cannot be recognized instead of displaying an unrelated sender or an empty merchant.
- Normalized garbled Thai month OCR with the image timestamp while retaining the transaction day and time from the slip.
- Passed image metadata into manual, debt, and daily K PLUS scanning for complete date recovery.
- Fixed profile writes rejected by stricter deployed Firestore rules by storing only bounded `displayName` and `phone` fields.
- Removed unused income-detection branches from the expense-only slip parser.
- Restricted daily automatic scanning to MediaStore images added between local 00:00 today and 00:00 tomorrow; removed the previous two-day lookback.
- Scheduled the daily attempt near 23:30 so the current day has content to scan, while keeping Android's deferrable-work limitation.
- Cleared Firebase test usage data and non-admin test accounts for a fresh onboarding test while preserving the verified admin account and its secure custom claim.
- Changed the admin destructive control from deleting Firebase Authentication to clearing usage data only, including the local pending-slip queue, while preserving password, verification, and admin claim.

## 1.3.0

- Added explicit opt-in daily scanning of recent gallery images for K PLUS slips only.
- Added an on-device approval queue: users must choose `confirm and keep` before Firestore receives a transaction, or `remove` to discard it permanently from the queue.
- Added processed-image tracking to prevent accepted or rejected K PLUS slips from returning as duplicates.
- Scheduled approximate daily scanning with WorkManager; Android may defer execution beyond midnight for battery optimization.
- Added a visible switch to disable automatic scanning at any time and clear permission messaging when access is denied.
- Fixed the pending-approval dialog scope found by the first GitHub Actions compile of v1.3.0.

## 1.2.2

- Fixed automatic email-verification polling so the waiting screen closes immediately after Firebase reports a verified account.
- Separated the verification flag from the mutable Firebase user object so Compose reliably redraws the screen.
- Added scroll-safe transaction history filters for today, week, month, all records, income, and expenses.
- Added debt payment history with merchant, amount, and slip date/time, falling back to the stored payment timestamp.
- Split debt management into a scalable debt list and a separate detail/payment-history screen for each selected debt.
- Added an accessible circular spending overview by category that follows the selected day, week, or month period, with text labels and amounts in addition to color.
- Refined transaction, empty-state, and debt cards for a cleaner, more readable UI.
- Fixed K PLUS MR.D.I.Y. merchant extraction by prioritizing the recipient's structured brand code and rejecting label-like fallback lines.
- Added K PLUS Thai textual date extraction (for example `11 ก.ค. 69`) from the same line as the transaction time.

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
- Removed Google and anonymous login at the user's request; the first screen is now email/password login and registration only.
- Final authentication decision: Firebase email verification link is required before entering the app; numeric OTP, Google, and anonymous login are not used.
- Added automatic slip amount, merchant, category, and Remark/Note/Memo extraction with editable confirmation and category-aware summaries.
- Added a more useful dashboard overview for transaction count and leading category.
- Cropped only the empty outer margin from the user-provided launcher artwork so the original wallet icon appears larger without changing its design.
- Prepared verified account email as the destination identity for a future monthly report feature; automated report delivery is not part of v1.0.0 yet.
- Released signed `v1.0.0` with APK and SHA-256 through GitHub Releases.
- Added debt plans with starting balance, optional annual interest, remaining balance and monthly payment history.
- Added payment-slip application with duplicate-slip fingerprint protection and automatic balance reduction.
- Added payoff-month estimation from the latest payment (including entered annual interest) and progress-based encouragement.
- Bumped the app to `1.1.0`; devices on `1.0.0` will discover it through the GitHub Release update popup after release publication.
- Fixed password fields to hide input by default with an explicit eye toggle.
- Fixed verified accounts remaining stuck on the waiting screen by automatically reloading Firebase verification state every 2.5 seconds.
- Prepared patch version `1.1.1` for the centered launcher crop and authentication UX fixes.

## 2026-07-12

- Diagnosed missing K PLUS amounts: the old parser required Thai labels that the Latin OCR model often omits even when it recognizes `15.00` or `29.00`.
- Added a safe positive-decimal fallback, time extraction, merchant-name mapping, category and remark mapping for scanned expense slips.
- Separated flows: the `+` button adds numeric income, while selected slips always create expense summaries without editable amount fields.
- Rejected non-slip images and unreadable slips in both the dashboard and debt payment flow.
- Added safe status-bar insets to the main and debt app bars.
- Replaced the launcher asset directly with the latest user-provided centered image without additional cropping.
- Bumped the app to `1.2.0` for the new scan and UI behavior.
- Updated the project agreement to avoid repeated conversational approval requests for the already-authorized RubJai delivery workflow and to keep reports token-efficient.
- Added an account-and-owned-data deletion workflow, hidden unless Firebase returns the secure custom claim `admin: true`; rejected hardcoded admin credentials because APKs can be reverse engineered.
- Assigned Firebase custom claim `admin: true` to the user-provided UID using the local service account without committing credentials.
