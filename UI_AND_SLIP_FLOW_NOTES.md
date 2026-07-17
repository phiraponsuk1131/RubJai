# UI and Slip Flow Notes

## Current Direction

RubJai 3.x is a full app redesign. The old cream/card home screen is no longer the target. The active direction is a RubJai-owned dark navy/yellow timeline inspired by the provided reference videos and screenshots, without copying them exactly.

Key visual requirements:

- dark navy app background
- yellow monthly summary card
- narrow responsive left day rail
- blue `จดเพิ่ม` action
- inline slip sync band
- daily transaction groups
- category-icon transaction rows
- white bottom category sheet with circular colored icons
- dark full-screen add/edit transaction flow
- new RubJai wallet mark instead of the old square mascot

## Home Timeline Flow

1. The active home screen must use `HomeReferenceScreen`.
2. The old active widgets must not return:
   - `SummaryCard`
   - `HomeActions`
   - `KPlusSyncStatus`
   - `SpendingOverview`
   - `EntryRow`
3. The month summary card must stay wide enough on phone screens. Do not bring back a fixed `138.dp` rail.
4. Thai text and amount labels must render horizontally.
5. The mascot/logo on the home card must be cropped as a badge, not shown as a large square image.
6. Slip sync must show status inline on the home timeline. Do not show a completion popup for slip sync.

## Transaction Entry Flow

1. Manual entry, scanned slip review, pending slip review, and editing a saved transaction all use the same full-screen dark editor.
2. The editor must show:
   - date/time
   - amount
   - title / recipient
   - category selector
   - note
   - source slip card when `slipUri` exists
3. Tapping the source slip card opens the original slip image full screen.
4. Saved transaction details must reopen the original source slip from the device through `LocalSlipLinkStore`.
5. Firestore must not store the slip image or local image URI.

## Category Sheet

The category picker is a white bottom sheet with:

- grey header
- close button
- add-tag button placeholder
- four-column category grid
- circular icon buttons
- centered labels
- selected category border

## Slip Sync Flow

Slip parsing is QR-first:

- scan QR / mini-QR from the slip image first
- use QR reference/raw payload for duplicate protection
- prepend QR fields into parse text when available
- use OCR only as fallback/additional context
- auto-sync only queues a slip when amount, recipient/title, and date/time are complete
- pending slips open the editor before saving
- real-time sync watches MediaStore while the app is open after consent and permission
- tests must use synthetic slip text or generated assets only; never commit real slip screenshots or local Downloads paths

Auto-sync starts when opening the app if consent and image permission already exist. It also runs again when a new image is added while the app is open. Completion/failure copy stays inline through `syncStatusText`.

The sync window must cover at most the latest 31 days. Real-time sync is still driven by MediaStore changes while the app is open, so new slip screenshots from the current day are picked up quickly without waiting for the next app start.

## Required Validation

Run these after every UI or slip change:

- `node scripts/check-text-integrity.js`
- `node scripts/check-privacy.js`
- `node scripts/check-slip-samples.js`
- `node scripts/check-ui-flow.js`
- APK build
- no-admin desktop layout preview: `node scripts/check-ui-layout.js`
- real device/emulator screenshot review when available

## GitHub Delivery Flow

1. Commit the exact release scope.
2. Push `main`.
3. Push a version tag newer than the installed `versionName`.
4. Wait for GitHub Actions to pass.
5. Verify release assets:
   - APK
   - SHA-256
   - `APP_UPDATE_NOTES_TH.md`

GitHub Release notes must be English. In-app update notes must be Thai.
