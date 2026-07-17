# RubJai v3.0.2

- Fixed the home slip-sync band so pending-slip text no longer collapses into vertical Thai characters on narrow phones.
- Added a no-admin desktop layout check that renders a 360dp SVG preview and fails when the home sync text column is too narrow.
- Improved slip recipient validation so noisy OCR strings such as Latin accented garbage are not accepted as recipient names.
- Expanded slip sync to scan up to the last 31 days while keeping real-time MediaStore sync for newly added images while the app is open.
- Bumped the auto-slip parser key so today's images can be rescanned with the safer recipient rules.
- Added the no-admin layout check to GitHub Actions before the APK build.
- Added a GitHub Actions Android emulator smoke test that installs the APK, enters trial mode, opens home, add transaction, and category picker, then uploads screenshots.
- Expanded emulator smoke coverage to account, profile, and debt-planner pages.
- Replaced the RubJai mark with a more minimal wallet logo.
- Added a privacy gate so real slip images, screenshots, videos, local Downloads paths, and real slip references are not committed to GitHub.
- Added a Windows local device smoke script for no-admin UI checks when a phone/ADB is available, and tuned the CI emulator to a lighter Android image.
