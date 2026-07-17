# RubJai v3.0.4

- Changed slip auto-sync to QR-only so noisy OCR text is never saved as a recipient or merchant name.
- Added K PLUS-style QR reference date/time extraction so synced slips land on their own transaction date.
- Uses QR payload amount, merchant when available, reference, and fingerprint for duplicate protection.
- Falls back to a safe QR reference title when the QR payload does not include a recipient name, instead of guessing from OCR.
- Hides previously saved noisy OCR-style names from timeline and slip cards so broken text does not remain visible.
- Added a sticky month summary header so the month tab, spending total, and summary action remain available while scrolling the timeline.
- Tightened the transaction editor proportions so amount, title, category, and note controls fit better on phone screens.
- Updated validation to block OCR-based auto-sync regressions before APK build and release.
