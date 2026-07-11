# RubJai v1.1.0

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
