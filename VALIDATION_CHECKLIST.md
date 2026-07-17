# RubJai Validation Checklist

Use this checklist every time the app UI, slip parsing, sync flow, or release version is changed.

## Required Reading Before Editing

Before changing code, read the project notes that match the work:

- `VALIDATION_CHECKLIST.md` for the required validation workflow
- `PROJECT_CONTEXT_REQUIRED.md` when starting from a new Codex chat/thread
- `UI_AND_SLIP_FLOW_NOTES.md` for the reference-video UI and slip-sync rules
- `README.md` for app setup, privacy, build, and release behavior
- `RELEASE_NOTES.md` and `APP_UPDATE_NOTES_TH.md` when changing app version, update popup text, or release behavior

Do not start a UI/slip/release edit without checking these notes first. This prevents missing conditions from earlier requests.

## Required Checks Before Commit

1. Thai text integrity
   - Run `node scripts/check-text-integrity.js`
   - Must pass with no mojibake or replacement characters.

2. Slip sample parsing
   - Run `node scripts/check-slip-samples.js`
   - Must extract recipient/name, amount, date, and time from the sample K PLUS / Dime slip cases.
   - Auto-sync must not queue QR-only incomplete slips that do not have amount, recipient/name, and date/time.

3. Redesigned UI flow guard
   - Run `node scripts/check-ui-flow.js`
   - Must confirm:
     - app version is `3.0.0`
     - home renders `HomeReferenceScreen`
     - slip sync status is inline, not a completion popup
     - old home/detail UI symbols are not left in `MainActivity.kt`
     - transaction editor shows the source slip card and can open the slip full screen

4. APK build
   - Run `gradle :app:assembleDebug --no-build-cache --no-parallel --stacktrace`
   - If Windows file locks appear under the local Android SDK, rerun outside the sandbox/permission wrapper.

## Visual/Flow Review

Compare the changed app flow against the latest reference videos/screenshots:

- home screen keeps RubJai's own dark navy/yellow timeline style
- month summary card, day rail, daily groups, and blue `จดเพิ่ม` action are present
- category picker is a white bottom sheet with circular icons and centered labels
- transaction entry and edit screens use the same dark full-screen layout
- slip details are visible in the editor through the `ข้อมูลจากสลิป` card and thumbnail
- sync on app open and real-time sync do not show a completion popup

## Release Rule

Do not request or push a release tag until all required checks and the APK build pass.
