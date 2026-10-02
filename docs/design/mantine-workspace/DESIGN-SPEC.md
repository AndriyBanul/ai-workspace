# Product design specification

## 1. Intent

AI Workspace is a personal research and creation environment. Users add source
material, wait for processing, ask questions, inspect evidence, and generate or
analyze media. The interface should make those actions easy to discover and
make system state understandable throughout long operations.

The design uses a dark navy-green navigation rail, warm neutral workspace,
white reading surfaces, restrained teal actions, and distinct but quiet source
type colours. Evidence gets the same visual attention as the generated answer.

The supplied React implementation is an interactive visual reference. Production
business logic, data fetching, validation, and ownership enforcement must be
connected to the existing application as described in `API-MAPPING.md`.

## 2. Visual system

| Token | Value / rule |
| --- | --- |
| Canvas | `#f5f6f3` |
| Surface | `#ffffff` |
| Main ink | `#20363c` |
| Muted text | `#65767a`; use for readable secondary text |
| Sidebar | `#152c32` |
| Primary action | `#146d60`; Mantine `workspace[7]` |
| Supporting green | `#eaf7f4` |
| Borders | `#e1e7e5` |
| Body | 14 px, system font, 1.55–1.85 line height according to reading density |
| Small text | 12–13 px; do not use tiny labels for necessary instructions |
| Page heading | 32 px desktop / 29 px mobile; weight 650 |
| Section heading | 18–23 px |
| Reading title | 25 px desktop / 23 px mobile |
| Page padding | 36 px desktop / 18 px mobile |
| Main spacing | 8, 12, 16, 24, 32 px |
| Controls | Mantine `md`, typically 42 px tall |
| Corner radius | 10 px controls, 14 px panels, 6 px small badges |
| Shadows | Limited to floating overlays, notifications, and decorative cards |

Import Mantine CSS first, global `base.css` second, and CSS Modules through
components. Use `theme` and `cssVariablesResolver` from `src/theme.js` in one
root MantineProvider. Do not retain the current unscoped legacy CSS beside the
new stylesheet: selectors such as `button`, `table`, and `.card` can override
Mantine unexpectedly. Migrate styles per screen, with explicit ownership.

Use Tabler icons at 16–21 px for actions and 20–26 px for source types. Pair
status colours with an icon and readable text. Do not imply confidence or
completion percentages without real data.

## 3. Shell and navigation

- Desktop: Mantine AppShell, `layout="alt"`, 244 px navbar, 72 px header.
- Desktop navigation: Library, Ask workspace, Creative studio, Activity.
- Workspace switcher is always near the top. Creation is in its menu.
- Account and Sign out remain reachable on every viewport.
- Header shows workspace context and a workspace-actions menu; put Delete
  workspace and API reference there rather than beside every page heading.
- Highlight active navigation with colour, a leading marker, and `aria-current`.
- Use a router/deep links in production. The prototype's hash navigation is a
  lightweight demonstration, not a required routing implementation.
- A workspace change resets or restores state only for that workspace. Abort
  obsolete requests; never show another workspace's answers or activity.
- In production, replace the sample identity and workspace menu entries with
  authenticated user/workspace API responses.

## 4. Library

Reference: `01-library-desktop.png`, `18-library-mobile.png`.

The top row contains the page title and one clear Add source action. The light
intro panel explains the product and links to Ask. On returning visits to an
established workspace, collapse this onboarding panel to a compact hint so the
source list remains the primary work surface. Persist that display preference
per user; it is unrelated to workspace data or authorization.

Summary cards show source counts: total, ready, processing, needs attention.
Derive every count from current data; the prototype's fixtures are not metrics.
Use the type, filename/title, friendly status, creation time, and overflow menu
for each source row. Retain file size/type and identify URL sources as remote.

Search filters by name, and the type filter covers Document, Audio, Image,
Video, Web page, and YouTube. Keep keyboard focus stable while filtering.
Search with zero results shows a Clear filters action. A truly empty library
shows Add your first source and supported input types. Loading uses skeleton
rows, not an empty-library message. Errors offer retry while preserving filters.

Row actions: view details, reprocess, delete. Confirm deletion with the source
name and consequence. Disable conflicting operations during PROCESSING and
handle a server 409 without losing context. After mutation, refresh source data
and activity; do not show success before the server accepts the operation.

## 5. Add source

Reference: `03-add-source-file.png`, `04-add-source-youtube.png`,
`05-add-source-web.png`.

Mantine Modal with three tabs: Upload file, Web page, YouTube. Include the
destination workspace, accepted inputs, visible validation, and an explicit
submit action. Close with Escape, trap focus, and restore focus to the opener.

The prototype illustrates the simple single-file flow. Preserve the existing
background multimodal upload capability in production with an expandable
"Add multiple media types" section. The current API accepts at most one file
per media type in a single multipart request. Do not design an arbitrary file
batch that the API cannot accept. A normal upload can also use the background
endpoint with a single file part. Keep the synchronous analysis flow in Studio.

Validate each type and the combined request size before upload. Explain 10 MB
images, 20 MB videos, and the 25 MB multipart ceiling, including overhead. Reuse
the actual supported extension sets; do not rely on a file extension alone for
server validation. PDF OCR is automatic when configured.

URL tabs require a public, supported URL and show what will be extracted.
Client validation improves feedback; server URL validation remains authoritative.
For web/YouTube synchronous ingestion, keep the modal in an honest working
state until the API returns; do not pretend a background job has been returned.

States: initial, input error, submitting, server/provider error, accepted/complete.
Avoid losing an entered URL or selected file after a recoverable failure. Do not
offer Cancel processing unless a cancellation API exists; closing a dialog must
not be described as cancellation of server work.

## 6. Ask workspace

Reference: `06-ask-desktop.png`, `07-ask-insufficient-evidence.png`,
`20-ask-mobile.png`.

Desktop: answer column approximately 62%, evidence column 38%, 24 px gap.
Question and answer are distinct blocks. Use readable Markdown, including
lists, tables, code, and links. The fixed sections in the sample answer are
illustrative content, not a required provider response format.

The evidence pane groups returned passages, displaying source name/type and
available page, slide, sheet, heading, timestamp range, and speaker. Clicking a
passage changes the quote panel. Inline reference buttons are permitted only
when the response explicitly identifies that reference. Never attach a passage
to a claim by guessing. With an unstructured answer, show the evidence list
without invented inline citations.

Use Copy answer with a success notification and clipboard failure fallback.
Remote source links must pass the existing HTTP/HTTPS safety check. Uploaded
files need a new authorized download/preview endpoint before offering Open file.
YouTube timestamps may become safe deep links when the source URL and timing
are present. Do not invent playback or embed capability.

The composer shows a visible label, character limit (4,000), busy state, and
clear error. Each current question searches independently; a follow-up-looking
interface must not imply conversational memory. Preserve that explanation.

Preserve the existing Load knowledge feature as a secondary "Browse extracted
knowledge" drawer/action using the aggregate knowledge API, grouped by modality.
It is not represented by an additional screenshot. Avoid rendering all raw text
permanently alongside answers; long extracts need scrolling or progressive reveal.

States: no sources, sources processing, no question yet, asking, answer with
evidence, answer without sufficient evidence, provider failure, clipboard failure.
The local buttons labelled Preview states are for design review only and must
not appear in the application.

Answer history is currently transient. Durable cross-device history is a
separate backend feature; do not present a Saved history affordance until that
contract exists. If keeping history within a session, key it by owner/workspace.

## 7. Source details and preview

Reference: `08-source-transcript.png`, `09-source-recovery.png`.

Top: back to Library, source name/type, creation time, readable status. Main
content region: document extract or media transcript. Sidebar: Ask workspace,
Reprocess, and basic source information. Put IDs, hashes, recovery operation,
attempt count, next retry, and last error in Technical details.

The source metadata and recovery APIs exist. A persisted transcript/document
preview API does not. The prototype's content viewer is therefore an explicitly
proposed extension. The first implementation may ship metadata and available
ingestion results while retaining this layout, showing a truthful unavailable
state when extracted content cannot be fetched. Never synthesize a full source
transcript from a few answer snippets.

When implementing media playback later, use native controls, accessible seeking,
an authorized content route, range requests where appropriate, and actual
transcript time ranges. Speaker names appear only when supplied by the provider.

## 8. Creative studio

Reference: screenshots `10`–`14` and `21-studio-mobile.png`.

Four tools: Image, Video, Speech, Analyze media. The first three generate media;
Analyze media adds a source through the existing understanding/transcription API.
YouTube ingestion is available through Library's unified import flow.

Desktop: input panel approximately 39%, result panel 61%. On mobile, stack input
above result. Prompt, primary action, and result status must remain obvious.

Image/video prompts accept up to 4,000 characters. Speech uses its existing text
contract; don't invent voice, speed, seed, aspect ratio, or model selectors when
the API does not support them. Display provider-not-configured errors in plain
language, preserving the prompt and offering a retry when meaningful.

Show actual `<img>`, `<video controls>`, or `<audio controls>` for response blobs;
use meaningful alternative text and revoke object URLs when replaced/unmounted.
Download the correct MIME type and filename. Generated media is not automatically
indexed: explain that it must be uploaded into Library. A future Add to library
shortcut must explicitly run that ingestion workflow.

No provider percentage is available, so use indeterminate status text. Preserve
the prompt on failure. Before navigating away from an in-flight synchronous
generation, explain that this screen holds the result; do not imply durable
background generation, recovery, or resumability.

Analysis results retain full text/description, detected language when present,
ordered timed segments, optional speakers, and source identity. Silent video
must still show its visual summary. Distinguish visual description from speech.

## 9. Activity and recovery

Reference: `15-activity-desktop.png`.

Select a recent job in the left panel; inspect its steps, timestamps, and status
in the right. Existing recent job IDs are browser-local, so retain "This browser"
until the API gains a workspace job-list endpoint. Keep lookup by job ID.

The illustrated three-stage timeline is a human-readable grouping, not three
new durable processing stages. Derive it conservatively from source/job data.
For multimodal jobs, render each actual job step with its own state. Support
COMPLETED, PARTIALLY_FAILED, FAILED, and skipped parts. Never collapse partial
failure into success or hide an individual failed media step.

Recovery: show what failed, whether a retry is scheduled, and an available next
action. Expose SCHEDULED/RUNNING/COMPLETED/DEAD_LETTER accurately in diagnostics.
Don't offer a Retry job button that lacks an endpoint: use source reprocessing
where allowed. A dead-letter source needs a clear manual next action.

Poll while pending/running, stop at terminal status, abort on navigation, and
handle 401/404/offline responses. Use actual timestamps; do not invent remaining
time. There is no cancellation endpoint or exact progress percentage today.

## 10. Login and registration

Reference: `16-login-desktop.png`, `17-registration-desktop.png`,
`22-login-mobile.png`.

Keep the split story/form composition on desktop and stack on mobile. Use
Mantine TextInput and PasswordInput with correct autocomplete attributes and
visible labels. Wire the existing registration and Basic sign-in mechanism.
Registration validates at least 12 characters and at most 72 UTF-8 bytes, not
simply 72 JavaScript characters. Show clear email/password errors.

Replace Open design preview with Sign in/Create account. Remove preview-only
copy. Keep a short, accurate explanation of sign-in lifetime where useful.
No password reset, SSO, persistent session, or remembered device controls are
included because those features are not implemented.

## 11. Responsive and accessibility requirements

- Below 992 px (`md`), use the navigation drawer. Sign out remains accessible.
- Below 768 px, content columns become one column; statistics become a 2 × 2 grid.
- Below 768 px, source rows become compact cards with visible status and an
  accessible action menu. At intermediate widths, any table scrolling stays
  inside Table.ScrollContainer, never on the whole page.
- At 390 px, headings wrap naturally, primary actions fit, and tool labels remain usable.
- Source reading/evidence flows remain in DOM reading order. For an inline
  citation on mobile, scroll/focus the selected evidence or open a focused drawer.
- Mantine overlays must retain focus trapping, Escape close, and focus restoration.
- Icon-only buttons need accessible names; navigation needs a current-page state.
- Add a skip-to-content link in production. Preserve visible focus styles.
- Meet WCAG AA text contrast, use 44 px touch targets where practical, and test
  keyboard navigation rather than assuming library defaults cover every layout.
- Announce job transitions and answer availability with a polite live region.
  Avoid announcing every poll. Associate field errors with their inputs.
- Respect reduced motion. Colour alone never communicates state.

## 12. Mantine component mapping

| UI area | Mantine primitives |
| --- | --- |
| Shell and mobile navigation | AppShell, Drawer, Burger, Menu, Avatar, UnstyledButton |
| Sources | Paper, Table.ScrollContainer, Table, TextInput, Select, Badge, Menu |
| Import | Modal, Tabs, FileInput, Select, Alert, Button; optional Dropzone only if implemented fully |
| Evidence | Paper, Stack, Group, Text, Badge; button semantics for selectable passages |
| Studio | SegmentedControl, Textarea, FileInput, Select, Alert, Button |
| Activity | Timeline, Accordion, Badge, TextInput |
| Account | TextInput, PasswordInput, Stack, Button, Alert |
| Feedback | Notification or Mantine notifications package, Skeleton, Loader |

Do not replace accessible Mantine controls with decorative divs. Keep layouts in
CSS Modules and theme-level defaults in `theme.js`; use inline styles for genuinely
dynamic values only. The reference pages are intentionally compact examples;
production extraction should follow the existing workflow component boundaries.

## 13. Implementation acceptance

1. All current application workflows still work, including direct and multimodal
   ingestion, web/YouTube, generation, raw knowledge inspection, source lifecycle,
   recovery, ownership errors, and job lookup.
2. Real API data replaces all names, counts, statuses, passages, and generated results.
3. All screenshot layouts are represented at desktop and mobile sizes. Component
   behavior matches this document; no screenshot text is used as a business rule.
4. Unsupported source previews or durable history are not presented as operational.
5. No page overflow at 390/768/1024/1440 px. Table overflow is local and usable.
6. Keyboard-only sign-in, navigation, import, source actions, questioning, evidence
   selection, generation, and sign-out are usable; dialogs restore focus.
7. Frontend workflow tests, production build, existing backend tests, and bootJar
   asset bundling pass. Use the repository's E2E specification for live testing.
8. Capture before/after screenshots with populated, empty, loading, and error data.
9. Remove demo labels, fixtures, simulated state controls, and placeholder artwork
   from production flows. Keep the design package as a separate reference.
