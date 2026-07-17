# UI and Slip Flow Notes

## Goal

This change moves the transaction entry experience closer to the provided reference video/screenshot:

- yellow top command area
- dark navy transaction canvas
- expense / income / transfer-style tabs
- large amount card
- blue outlined category selector
- bottom category sheet with a white background and icon grid

The implementation keeps RubJai's own data model and does not copy another app exactly.

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

The home screen now includes a slip-sync header that shows:

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

## OCR Limits

RubJai still uses on-device ML Kit text recognition. OCR quality depends on:

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
- `CHANGELOG.md`
  - documents the UI and slip flow update

## GitHub Delivery Flow

RubJai updates must be delivered through GitHub, not only built locally.

Required release steps:

1. Login GitHub CLI with the repository owner account.
   - `gh auth login -h github.com -p https -w`
   - confirm `gh auth status` shows a valid `repo` token.
2. Bump Android version.
   - update `versionCode`
   - update `versionName`
   - update README, changelog, and release notes.
3. Run a local assemble build as a safety check.
   - `gradle :app:assembleDebug`
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
