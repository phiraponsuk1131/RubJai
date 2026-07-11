# RubJai v1.0.0

## Added

- Income, expense, salary entry, balance summary, and Firestore synchronization.
- Thai OCR for slip images selected or shared by the user.
- Import of bank notification text explicitly shared from LINE.
- User-provided black-and-gold RubJai wallet, receipt, coins, and income/expense arrow launcher icon.
- GitHub Release update popup with scrollable release notes and an in-app download progress bar.

## Fixed and safety

- Extracted values require user confirmation before saving.
- Firestore data is isolated by authenticated user ID.
- Private LINE chats are not scraped; Accessibility and automatic gallery scanning are not used.

## Notes

- Firebase Anonymous Authentication, Firestore rules, App Check, GitHub secrets, and a permanent signing key must be configured by the repository owner.
- OCR accuracy depends on slip layout; always verify amount and income/expense type.
