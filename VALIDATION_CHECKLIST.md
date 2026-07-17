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

These checks must be run before every push to GitHub. Do not push first and rely on GitHub Actions to discover issues. For markdown-only edits, at minimum run the text and privacy checks; for app, UI, slip, sync, icon/logo, workflow, version, or release edits, run the full relevant checklist before pushing.

1. Thai text integrity
   - Run `node scripts/check-text-integrity.js`
   - Must pass with no mojibake or replacement characters.

2. Privacy
   - Run `node scripts/check-privacy.js`
   - Must pass before commit.
   - Do not commit real slip images, screenshots, videos, local Downloads paths, real transaction references, or other private slip data.
   - Parser tests must use synthetic names and references that preserve format only.

3. Slip sample parsing
   - Run `node scripts/check-slip-samples.js`
   - Must extract recipient/name, amount, date, and time from the sample K PLUS / Dime cases.
   - QR data is primary. OCR is fallback/context only.

4. Redesigned UI flow guard
   - Run `node scripts/check-ui-flow.js`
   - Must confirm:
     - app version is `3.0.3`
     - home renders `HomeReferenceScreen`
     - the home rail is responsive, not fixed at the broken wide value
     - the new RubJai mark is used instead of the old square mascot
     - slip sync status is inline, not a completion popup
     - old home/detail UI symbols are not active
     - transaction editor shows the source slip card and can open the slip full screen
     - pending slip review previews the source slip image from the device
     - editing a saved transaction preserves its local slip image link

5. APK build
   - Run `gradle :app:assembleDebug --no-build-cache --no-parallel --stacktrace`
   - If Windows file locks appear under the local Android SDK, rerun outside the sandbox/permission wrapper.

6. No-admin desktop layout check
   - Run `node scripts/check-ui-layout.js`
   - Open `build/ui-preview/home-360.svg` to inspect the generated 360dp home preview on the computer.
   - This does not replace a real device test, but it catches the narrow-layout text collapse before APK build.

7. Visual/device review
   - If a device or emulator is connected, install the APK, open the app, and capture screenshots.
   - On Windows with a connected phone and ADB available, run `powershell -ExecutionPolicy Bypass -File scripts\local-device-smoke.ps1`.
   - Compare the home screen, add/edit screen, category picker, pending slip review, full-screen slip image, and update popup against the latest user screenshots/video.
   - Do not release a UI change that has only been checked by static code search when a device/emulator is available.
   - GitHub Actions intentionally does not run Android emulator smoke tests. GitHub is used for privacy/static/parser/layout gates and APK packaging only; emulator/device UI smoke tests must run locally on this machine to avoid slow CI loops and private slip exposure.

## Release Rule

Do not push a release tag until all required checks, local device/emulator review when available, and the APK build pass. GitHub Release notes must stay English. In-app update notes must stay Thai.

For every new app version, always do the full update path:

1. Bump `versionName` and `versionCode`.
2. Update `RELEASE_NOTES.md` in English.
3. Update `APP_UPDATE_NOTES_TH.md` in readable Thai for the in-app update popup.
4. Run the required validation checks before pushing.
5. Push to GitHub and wait for the APK workflow to pass.
6. Create/push the matching `v*` tag only after validation and APK build pass.
7. Verify the GitHub Release exists, includes the APK, `RubJai.sha256`, and `APP_UPDATE_NOTES_TH.md`.
8. Confirm the in-app updater can see the new release: latest GitHub Release version must be greater than the installed app version, otherwise the update popup will not show.
