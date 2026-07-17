# RubJai Validation Checklist

Use this checklist every time the app UI, slip parsing, sync flow, icon/logo, or release version is changed.

## Required Reading Before Editing

Read these files before code changes:

- `PROJECT_CONTEXT_REQUIRED.md`
- `VALIDATION_CHECKLIST.md`
- `UI_AND_SLIP_FLOW_NOTES.md`
- `README.md`
- `RELEASE_NOTES.md`
- `APP_UPDATE_NOTES_TH.md`

## Required Checks Before Commit

1. Thai text integrity
   - Run `node scripts/check-text-integrity.js`
   - Must pass with no mojibake or replacement characters.

2. Slip sample parsing
   - Run `node scripts/check-slip-samples.js`
   - Must extract recipient/name, amount, date, and time from the sample K PLUS / Dime cases.
   - QR data is primary. OCR is fallback/context only.

3. Redesigned UI flow guard
   - Run `node scripts/check-ui-flow.js`
   - Must confirm:
     - app version is `3.0.1`
     - home renders `HomeReferenceScreen`
     - the home rail is responsive, not fixed at the broken wide value
     - the new RubJai mark is used instead of the old square mascot
     - slip sync status is inline, not a completion popup
     - old home/detail UI symbols are not active
     - transaction editor shows the source slip card and can open the slip full screen
     - pending slip review previews the source slip image from the device
     - editing a saved transaction preserves its local slip image link

4. APK build
   - Run `gradle :app:assembleDebug --no-build-cache --no-parallel --stacktrace`
   - If Windows file locks appear under the local Android SDK, rerun outside the sandbox/permission wrapper.

5. Visual/device review
   - If a device or emulator is connected, install the APK, open the app, and capture screenshots.
   - Compare the home screen, add/edit screen, category picker, pending slip review, full-screen slip image, and update popup against the latest user screenshots/video.
   - Do not release a UI change that has only been checked by static code search when a device/emulator is available.

## Release Rule

Do not push a release tag until all required checks and the APK build pass. GitHub Release notes must stay English. In-app update notes must stay Thai.
