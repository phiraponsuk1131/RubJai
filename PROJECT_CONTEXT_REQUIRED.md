# Required Project Context For New Codex Threads

When starting a new Codex chat/thread for this RubJai project, read the project markdown files before answering, planning, editing, pushing, or releasing.

## Read First

1. `README.md`
   - app purpose
   - privacy model
   - Firebase setup
   - build and release behavior

2. `VALIDATION_CHECKLIST.md`
   - required reading before editing
   - required checks before commit
   - release rule

3. `UI_AND_SLIP_FLOW_NOTES.md`
   - latest reference-video UI direction
   - home timeline flow
   - transaction entry flow
   - QR-first slip sync rules

4. `RELEASE_NOTES.md`
   - current GitHub Release notes in English

5. `APP_UPDATE_NOTES_TH.md`
   - current in-app update popup notes in Thai

## Working Rule

Do not assume the old app behavior is still desired. The active direction for RubJai 3.x is:

- RubJai's own dark navy/yellow timeline style
- QR-first slip parsing
- OCR only as fallback/additional context
- no slip-sync completion popup
- transaction entry/edit uses the same dark full-screen editor
- category picker uses a white bottom sheet with circular category icons
- every change must be validated before push/release
- never push to GitHub before running the relevant checks for the change; for app/UI/slip changes, run the full validation checklist first
- after any UI edit, run the UI-flow check and perform a real device/emulator visual check when a device is available
- when no device/emulator is available, run `node scripts/check-ui-layout.js` and inspect `build/ui-preview/home-360.svg` before build/release
- saved transaction details must reopen the original slip image from the device through the local `slipUri` link; the image is not uploaded to Firebase

If a request conflicts with these files, ask for confirmation or update the relevant markdown in the same change.
