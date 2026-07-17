# RubJai v3.0.0

- Rebuilt the main home screen into the dark navy/yellow timeline style from the reference flow.
- Replaced the old active home cards with a month summary card, day rail, daily sections, category-icon rows, and a blue extended add button.
- Kept slip sync silent: no completion popup. Sync status, scanned count, and pending slips now live inside the home timeline.
- Updated transaction editing to use the same full-screen dark entry flow for existing records instead of the old cream detail screen.
- Reworked default expense categories to match the reference picker style and labels more closely.
- Improved slip recipient parsing so bank/account lines are not selected as the recipient name.
- Added a GitHub Actions UI-flow gate to catch old home UI calls, popup slip-sync regressions, and version mismatch before APK build.
- In-app update notes are provided in Thai via `APP_UPDATE_NOTES_TH.md`.
