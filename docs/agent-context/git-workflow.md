# Git Workflow

- Continue regular development on `develop`.
- Keep `main` stable.
- Merge `develop` into `main` only when explicitly requested.
- Do not force-push unless explicitly requested.
- Use clear commit messages that describe intent, not just files changed.
- Check `git status` before editing, committing, pushing, or merging.
- Do not revert unrelated user changes.
- Do not rewrite history unless explicitly requested.

When asked to commit:

- Review the diff before committing.
- Keep the commit focused on the requested change.
- Report the commit hash and branch after committing.
