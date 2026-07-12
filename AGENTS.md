# RubJai Working Agreement

- RubJai is a separate Android project; do not mix its package, Firebase project, signing key, or release tags with ThaiGuard.
- Read every Markdown file in this project before editing.
- Use Kotlin and Jetpack Compose, keep slip/chat imports opt-in, and never use Accessibility to scrape LINE.
- Do not run unit or instrumentation tests. A Gradle assemble build is allowed.
- Update `CHANGELOG.md` after changes. Before a tag, update `RELEASE_NOTES.md`.
- GitHub Actions must build a signed APK on every push and attach APK plus SHA-256 to `v*` releases.
- Never commit `google-services.json`, signing keys, passwords, private messages, or slip images.
- Once the user has authorized the RubJai delivery workflow, continue in-scope commit, push, GitHub Actions build checks, version tags, releases, APK/checksum verification, and artifact download without asking the same conversational approval again. System-required permission prompts may still appear.
- Keep progress updates and final reports concise and token-efficient while preserving blockers, failures, release links, and required Firebase steps.
- Never hardcode admin usernames or passwords in the Android app. Admin-only UI must depend on a Firebase ID token custom claim such as `admin: true`; passwords remain exclusively in Firebase Authentication.
