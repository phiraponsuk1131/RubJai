# RubJai v1.3.1

## Fixed in v1.3.1

- K PLUS personal transfers now read the recipient block after the transfer arrow instead of returning no merchant name.
- Garbled Thai month text such as `n.A.` is restored using the image date, producing a complete Thai slip date while preserving the slip time.
- Profile updates no longer send an unnecessary email field and stay within Firestore field limits.

## Improved

- Date recovery now applies to manually selected slips, debt payment slips, and daily automatic K PLUS scans.
- The expense-only parser was simplified by removing unused income-detection code.
- Daily automatic scanning now queries only images added during the current local calendar day; it no longer looks back across previous days.
- The daily attempt is scheduled near 23:30 so it can process the current day's slips before the date changes.
- The admin control now clears usage data without deleting the Authentication account, password, verified-email state, or admin custom claim.

---

## Previous release: RubJai v1.3.0

## Added in v1.3.0

- Optional daily K PLUS-only scanning after the user explicitly grants photo access.
- Results remain in an on-device approval queue and are not counted or uploaded until the user taps `confirm and keep`.
- Users can reject a result with `remove`; processed slips are remembered to prevent repeated prompts.
- Automatic work is scheduled near midnight with Android WorkManager and can be disabled at any time.

## Privacy and limitations

- OCR runs on-device. Pending scans stay local; only confirmed transactions are sent to the signed-in user's Firestore area.
- Android can delay periodic work for battery optimization, so midnight is approximate rather than exact.
- This release recognizes K PLUS only; no bank-specific automatic scanning was added for other slip formats.

---

## Previous release: RubJai v1.2.2

## Fixed in v1.2.2

- The email-verification waiting page now checks Firebase automatically and enters RubJai as soon as the verification link has been completed.
- Fixed the screen not updating when Firebase reused the same signed-in user object after a refresh.
- Fixed MR.D.I.Y. K PLUS slips showing an unrelated OCR line instead of the recipient name.
- Fixed K PLUS slips losing Thai-format dates while retaining only the time.

## Added in v1.2.2

- Scroll-safe transaction history with today, week, month, and all-time filters.
- A second filter for all entries, income only, or expenses only.
- Debt payment history showing merchant, amount, date, and time for every saved slip.
- Older debt payments automatically fall back to their saved timestamp when the slip time is unavailable.
- Multiple debts now appear in a compact selection list; tapping one opens its own payoff details and payment history.
- Added a category-based circular spending overview for today, this week, and this month, with screen-reader descriptions and a text legend.

## Security

- Regular accounts must still verify their email address before accessing app data.

## Note

- Return to RubJai after opening the verification link; detection may take up to about 2.5 seconds.

---

## Previous release: RubJai v1.2.1

## Added in v1.2.1

- Admin-only account and owned-data deletion control protected by Firebase custom claim `admin: true`.
- Deletes the signed-in account's transactions, debts, debt-payment slips, profile document, and Authentication account after confirmation.
- Admin credentials are never embedded in the APK.

---

## Previous release: RubJai v1.2.0

## Added and fixed in v1.2.0

- The `+` button now adds income directly as a number; income does not require a slip.
- Expense slips automatically map merchant name, positive amount, time, category, and remark.
- Scanned expense and debt-slip dialogs no longer ask the user to type an amount.
- Non-slip images and unreadable slips are rejected with a clear message.
- Main and debt headers respect the Android status bar.
- Uses the latest user-provided launcher image directly.

---

## Previous release: RubJai v1.1.1

## Fixed in v1.1.1

- Passwords are hidden by default and can be revealed with the eye button.
- Email verification status refreshes automatically; no manual confirmation button is required.
- Launcher artwork is cropped symmetrically from the original image and centered.

---

## Previous release: RubJai v1.1.0

## Added in v1.1.0

- Debt payoff plans with starting balance and optional annual interest.
- Monthly payment-slip import, duplicate-slip protection, and automatic remaining-balance reduction.
- Estimated payoff months from the latest payment and encouraging progress messages.
- Automatic slip category and Remark/Note/Memo summary plus a more useful dashboard.
- Firebase email-link verification gate and enlarged launcher icon crop.

## Update and safety

- Version `1.0.0` checks GitHub Releases and displays a scrollable `1.1.0` update popup with in-app download progress.
- Debt data and payment slips are owner-only under Firestore rules.

---

## Previous release: RubJai v1.0.0

## Added

- Income, expense, salary entry, balance summary, and Firestore synchronization.
- Thai OCR for slip images selected or shared by the user.
- Import of bank notification text explicitly shared from LINE.
- User-provided black-and-gold RubJai wallet, receipt, coins, and income/expense arrow launcher icon.
- Larger launcher artwork crop with the original design preserved.
- GitHub Release update popup with scrollable release notes and an in-app download progress bar.
- Email/password registration with Firebase email-link verification and an editable user profile.

## Fixed and safety

- Extracted values require user confirmation before saving.
- Firestore data is isolated by authenticated user ID.
- Private LINE chats are not scraped; Accessibility and automatic gallery scanning are not used.

## Notes

- Firebase Email/Password Authentication with email-link verification, Firestore rules, and a permanent release signing key are configured; App Check remains before public release.
- OCR accuracy depends on slip layout; always verify amount and income/expense type.
