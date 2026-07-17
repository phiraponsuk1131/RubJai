# UI and Slip Flow Notes

## Goal

Version 3.0.0 moves the main home, transaction entry, category picker, and slip-sync surface closer to the provided reference video/screenshot:

- dark navy home timeline
- yellow monthly summary card
- left day rail
- blue extended "จดเพิ่ม" action
- yellow top command area
- dark navy transaction canvas
- expense / income / transfer-style tabs
- large amount card
- blue outlined category selector
- bottom category sheet with a white background and icon grid

The implementation keeps RubJai's own data model and does not copy another app exactly.

## Home Timeline Flow

1. The active home screen uses `HomeReferenceScreen`.
2. The old active home widgets are no longer called from the main tab:
   - `SummaryCard`
   - `HomeActions`
   - `KPlusSyncStatus`
   - `SpendingOverview`
   - `EntryRow`
3. The top of the home screen shows the latest save time and a yellow month summary card.
4. Slip sync is shown as an inline band below the month card.
5. Transactions are grouped by day with a left rail, daily summary block, and category-icon rows.
6. The primary add action is the blue extended `จดเพิ่ม` button.
7. Tapping a transaction opens the same dark full-screen editor used for new entries, not the old cream detail screen.

## Transaction Entry Flow

1. User opens a scanned slip or creates a transaction.
2. RubJai opens a full-screen editor instead of the old alert dialog.
3. User can edit:
   - amount
   - merchant / recipient / title
   - category
   - note
4. Tapping the category row opens a bottom sheet similar to the reference image.
5. Selecting a category closes the sheet and returns to the editor.
6. Saving writes the confirmed transaction.

## Category Sheet

The category picker is built in Compose with:

- white bottom sheet
- grey header
- close button
- add-tag button placeholder
- four-column category grid
- circular icon buttons
- selected category border

Category icons are inferred from category text, so custom categories still display a fallback grid icon.

## Slip Sync Flow

Manual slip selection still parses a selected image with ML Kit OCR.

The home screen now includes an inline slip-sync band that shows:

- current sync state
- pending slip count
- manual slip picker
- retry/resync action
- review action when pending slips exist

Auto slip sync now starts when entering the app if:

- the user previously gave scan consent
- the app already has image-read permission
- no sync is currently running

Pending slips no longer save immediately from the pending list. They now open the same full-screen editor first, so the user can confirm or fix name, amount, category, and note before saving.

Slip sync completion must not display a popup. Completion/failure copy stays inline in the home timeline through `syncStatusText`.

Real-time sync now registers a MediaStore observer while the app is open. When a new image is added and the user has already granted consent and image permission, RubJai triggers a throttled sync instead of waiting for the next app start.

## OCR Limits

RubJai now uses a QR-first slip flow:

- scan QR / mini-QR from the slip image first
- use QR reference as the most stable slip fingerprint for duplicate protection
- append QR amount, merchant, bank, and reference into the text stream when the QR payload exposes them
- use OCR as fallback and as the source for visible fields that QR does not expose
- keep every detected slip in the review editor before saving

OCR quality still depends on:

- slip image clarity
- bank/wallet layout
- language model recognition
- whether the amount and recipient appear as readable text

The app should always let users correct OCR output before saving because 100% OCR accuracy cannot be guaranteed on every slip image.

## Files Changed

- `app/src/main/java/app/rubjai/mobile/MainActivity.kt`
  - rebuilt transaction editor UI
  - added bottom category sheet
  - changed pending slip approval flow
  - added app-entry auto sync when consent and permission are already available
- `app/src/main/java/app/rubjai/mobile/SlipQrReader.kt`
  - reads slip QR codes with ML Kit barcode scanning
  - extracts transaction reference, amount, merchant, and bank when present
  - creates a stable fingerprint from QR reference/raw payload
- `app/src/main/java/app/rubjai/mobile/AutoSlipScanner.kt`
  - runs OCR plus QR-first detection for today's images
  - registers a real-time MediaStore observer while the app is open and sync consent is active
- `scripts/check-slip-samples.js`
  - validates expected recipient, amount, date, and time for K PLUS and Dime sample slip text
- `scripts/check-ui-flow.js`
  - validates version `3.0.0`
  - blocks old active home UI calls from returning to the main tab
  - blocks slip-sync popup regression
  - confirms the redesigned home timeline components are wired before APK build
- `CHANGELOG.md`
  - documents the UI and slip flow update

## GitHub Delivery Flow

RubJai updates must be delivered through GitHub, not only built locally.

Required release steps:

1. Login GitHub CLI with the repository owner account.
   - `gh auth login -h github.com -p https -w`
   - confirm `gh auth status` shows a valid `repo` token.
2. Run local checks before commit:
   - `node scripts/check-text-integrity.js`
   - `node scripts/check-slip-samples.js`
   - `node scripts/check-ui-flow.js`
   - `gradle :app:assembleDebug --build-cache --parallel --stacktrace`
3. After every UI or slip-parser edit, verify the changed behavior against the reference screenshots/video before committing:
   - home timeline still uses the dark/yellow RubJai redesign
   - transaction entry still uses the dark full-screen editor
   - category picker still uses the white bottom sheet with circular icons
   - slip sync still stays inline and does not show a completion popup
   - sample slips still return recipient, amount, date, and time
4. Do not request a release tag or push a release until the checks and APK build pass.
5. Commit and push to `main`.
6. Tag the release, for example `v3.0.0`, and push the tag.
7. Wait for GitHub Actions to pass.
8. Confirm GitHub Release assets include:
   - APK
   - SHA-256
   - `APP_UPDATE_NOTES_TH.md`
2. Bump Android version.
   - update `versionCode`
   - update `versionName`
   - update README, changelog, English `RELEASE_NOTES.md`, and Thai `APP_UPDATE_NOTES_TH.md` because the in-app update popup displays the Thai asset.
3. Run a local assemble build as a safety check.
   - `gradle :app:assembleDebug`
   - `node scripts/check-text-integrity.js`
   - `node scripts/check-slip-samples.js`
4. Commit the exact release scope.
   - include app code, workflow changes, version files, changelog, release notes, and maintainer notes.
   - do not commit `app/google-services.json`, signing keys, `.toolchain/`, or `.tools/`.
5. Push `main`.
   - GitHub Actions should build an APK artifact for the branch push.
6. Create and push the version tag.
   - example: `git tag v2.0.6`
   - `git push origin v2.0.6`
7. Let GitHub Actions build the tag.
   - the tag workflow creates the GitHub Release.
   - `RELEASE_NOTES.md` is used as the release body.
   - the release must include the APK and SHA-256 file.
8. Verify the release page.
   - check that `v*` exists.
   - check that the APK asset is attached.
   - the app update checker reads GitHub Releases, so the tag version must be newer than the installed `versionName`.
