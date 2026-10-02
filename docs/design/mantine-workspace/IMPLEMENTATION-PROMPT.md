# Prompt for the implementing AI agent

Copy the following request into a new agent session and provide this entire
design directory (or the companion ZIP) together with the application repository.

---

Implement the AI Workspace frontend design supplied in
`docs/design/mantine-workspace` using Mantine. Treat this directory as the visual
reference and handoff, and `apps/web` as the application to change.

Read AGENTS.md, DEVELOPER_CODE_PREFERENCES.md, the project architecture guidance,
and the current handoff first. Then read this design package's README.md,
DESIGN-SPEC.md, and API-MAPPING.md. Inspect gallery.html and the desktop/mobile
screenshots. Run the standalone prototype if useful to understand interactions.

Use Mantine core/hooks 9.6.2 and Tabler icons as pinned in the reference. Reuse
the theme, CSS variable resolver, tokens, and CSS Modules. Preserve the visual
hierarchy, navy-green navigation, warm white panels, teal actions, source types,
and answer/evidence layout. Verify dependency compatibility before modifying
the application's package files; keep existing dependencies and build integration.

Implement Library, unified Add source (files/web/YouTube), source metadata and
recovery details, Ask and evidence inspection, raw knowledge inspection, Studio
image/video/speech generation and media analysis, Activity, login/registration,
workspace creation/deletion/switching, and accessible mobile navigation.

Use existing APIs and real data. Preserve the existing direct and background
multimodal ingestion paths, validation limits, security/ownership semantics,
error handling, source lifecycle, reprocessing/deletion, job status, binary
preview/download, timed transcripts, and optional speaker metadata.

The reference is an interactive prototype. Replace its sample data, simulated
notifications, hardcoded counts, identity, answers, and result artwork with real
application behavior. Remove design-preview labels and local state-picker
buttons from production. Never return a canned answer to a real question.

Pay particular attention to the "Explicit extensions and fallbacks" table in
API-MAPPING.md. Persisted source previews, content playback, durable answer
history, and server job listing need contracts that do not currently exist.
Implement the documented truthful fallback and record the gap. Do not invent
endpoints or claim unsupported capabilities. Render claim-level citations only
when reference identity is supplied explicitly and validated against evidence.

Keep the current workflow component boundaries, introduce small presentation
components where useful, and avoid replacing working business logic with demo
handlers. Use a single MantineProvider, scoped CSS Modules, clear input labels,
keyboard-accessible menus/dialogs, and focus restoration. Make sign-out available
on mobile. Preserve reduced-motion support, source links safety, clipboard
failure handling, and object URL cleanup. Prevent whole-page horizontal overflow.

Work autonomously through implementation and verification. Run frontend workflow
tests, production build, required Gradle tests and bootJar packaging. Perform
browser checks at 390, 768, 1024, and 1440 px, including empty/loading/error/partial
failure states. Compare populated screens with the supplied screenshots and
attach actual final screenshots. Use docs/end-to-end-test-specification.md for
applicable end-to-end checks; explicitly report anything blocked by infrastructure
or credentials. Do not report prototype checks as live application tests.

Finish with the implemented workflows, any documented gaps, verification results,
and changed-file links. Keep backend feature expansions separate and do not
commit, push, deploy, or spend on live AI generation unless separately authorized.

---
