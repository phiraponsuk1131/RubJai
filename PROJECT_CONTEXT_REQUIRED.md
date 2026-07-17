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

## Speed Rule For Future Changes

Keep future change cycles short and direct:

- read the required markdown first, then edit without re-explaining the whole project
- change only the files needed for the user's current request
- test locally before any commit, push, tag, or release
- for markdown-only edits, run only the minimum relevant checks unless the edit changes release behavior
- for UI/slip/version/release edits, run the relevant local validation first, then push only after it passes
- summarize only the changed behavior, validation result, release/build link, and any real blocker
- do not repeat long design rationale unless the user asks for it

Do not assume the old app behavior is still desired. The active direction for RubJai 3.x is:

- RubJai's own dark navy/yellow timeline style
- QR-only slip parsing for auto-sync and selected slip images
- OCR must not create recipient/title data
- no slip-sync completion popup
- transaction entry/edit uses the same dark full-screen editor
- category picker uses a white bottom sheet with circular category icons
- every change must be validated before push/release
- never push to GitHub before running the relevant checks for the change; for app/UI/slip changes, run the full validation checklist first
- after any UI edit, run the UI-flow check and perform a real device/emulator visual check when a device is available
- when no device/emulator is available, run `node scripts/check-ui-layout.js` and inspect `build/ui-preview/home-360.svg` before build/release
- saved transaction details must reopen the original slip image from the device through the local `slipUri` link; the image is not uploaded to Firebase
- every new version must complete the full update path in `VALIDATION_CHECKLIST.md`, including Thai update-popup notes and GitHub Release verification

If a request conflicts with these files, ask for confirmation or update the relevant markdown in the same change.
