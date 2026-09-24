# API mapping and implementation boundaries

All paths below have prefix `/api/v1`. Verify current controller/OpenAPI contracts
before implementation. Reuse the existing authenticated API helper and ownership
behavior. UI changes do not authorize weakening backend validation.

| Workflow | Existing API | Design behavior |
| --- | --- | --- |
| Register | `POST /auth/register` | Email, password, displayName; show validation errors |
| Sign in / current account | `GET /auth/me` with Basic authorization | Use returned identity; current auth is memory-only |
| Workspaces | `GET /workspaces`, `POST /workspaces`, `GET /workspaces/{workspaceId}` | Switcher and creation modal |
| Delete workspace | `DELETE /workspaces/{workspaceId}` | Confirm named workspace and consequences |
| Source list | `GET /workspaces/{workspaceId}/sources` | Source rows/counts; local filters until server pagination exists |
| Source metadata | `GET /workspaces/{workspaceId}/sources/{sourceId}` | Details panel |
| Source deletion | `DELETE /workspaces/{workspaceId}/sources/{sourceId}` | Confirm; refresh only after response |
| Reprocess | `POST /workspaces/{workspaceId}/sources/{sourceId}/reprocess` | Track returned job; stable source ID |
| Recovery | `GET /workspaces/{workspaceId}/sources/{sourceId}/recovery` | Human-readable retry state and expandable diagnostics |
| Background upload | `POST /orchestrator/ingestions` multipart | workspaceId and optional document/audio/image/video parts; one per type |
| Job lookup | `GET /orchestrator/jobs/{jobId}` | Recent browser IDs and direct lookup; poll until terminal |
| Document ingestion | `POST /documents/text` multipart | workspaceId + file; preserve extraction metadata |
| Web page ingestion | `POST /documents/web-page` JSON | workspaceId + url; public HTML/text |
| Audio transcription | `POST /audio/transcriptions` multipart | workspaceId + file; text, language, segments |
| Image understanding | `POST /images/descriptions` multipart | workspaceId + file; description |
| Video understanding | `POST /videos/descriptions` multipart | workspaceId + file; visual summary and transcript |
| YouTube | `POST /videos/youtube` JSON | workspaceId + url; canonical source and analysis |
| Image generation | `POST /images/generations` JSON `{description}` | Binary image preview/download |
| Video generation | `POST /videos/generations` JSON `{description}` | Binary video preview/download; long running |
| Speech | `POST /audio/speech` JSON `{text}` | WAV playback/download |
| Ask | `POST /knowledge/workspaces/{workspaceId}/answers` JSON `{question}` | Answer Markdown + sources + source files |
| Raw knowledge | `GET /knowledge/workspaces/{workspaceId}` | Documents/audio/images/video aggregates; secondary inspection UI |

The `/files` and `/sources` lifecycle paths are aliases. Prefer `/sources` for new
UI code because URL inputs have no stored original file. Keep compatibility paths.

## State translation

| Backend | User label | Required explanation/action |
| --- | --- | --- |
| Source `UPLOADED` | Waiting to process | Source saved; processing has not started |
| Source `PROCESSING` | Processing | Disable conflicting reprocess/delete; link Activity |
| Source `PROCESSED` | Ready | Available for questions |
| Source `FAILED` | Needs attention | Show safe error and recovery information |
| Recovery `SCHEDULED` | Retry scheduled | Display nextAttemptAt if provided |
| Recovery `RUNNING` | Retrying | Display actual attempt count if provided |
| Recovery `COMPLETED` | Up to date | Technical panel may show next reconciliation check |
| Recovery `DEAD_LETTER` | Needs manual attention | Explain source/config issue and available reprocess action |
| Job `PARTIALLY_FAILED` | Partially completed | Show successful and failed steps independently |
| Job `FAILED` | Failed | Show step errors and source-specific next actions |
| Step `SKIPPED` | Not submitted | Do not show as an error |

Confirm enum names directly in source before wiring. Backend codes remain stable;
friendly labels are presentation only. Missing data remains absent, never fabricated.

## Explicit extensions and fallbacks

| Proposed interaction | Current limitation | First implementation |
| --- | --- | --- |
| Persisted transcript/document preview in source details | Metadata endpoint does not return full extracted content | Metadata/recovery panel plus available recent ingestion result; otherwise clear unavailable state |
| Play original audio/video at a citation | No authorized original-content streaming route exposed | Show timestamp text and available safe remote source link |
| Open PDF at a cited page | No file delivery/preview endpoint | Show filename, page, passage; add authorized content API in a separately agreed backend change |
| Claim-level inline reference buttons | Answer is text with a separate evidence list | Render only explicitly identifiable references; otherwise show evidence list |
| Saved question/answer history | No persistent conversation/history API | Session-only history, accurately described |
| Cross-device job list | No workspace job-list endpoint | Existing local job IDs plus lookup by ID, labelled "This browser" |
| Exact progress / cancellation | No percentages or cancellation API | Indeterminate status and honest stage labels |
| Arbitrary multi-file batch | One file per modality in the multipart ingestion contract | Constrained multimodal inputs; do not silently drop extra files |
| Generated output saved into Library | Binary generation response is not indexed | Download and upload, or explicitly invoke existing ingestion as a new user action |
| Source-filtered questions | Question endpoint accepts workspace and question only | Ask across workspace; don't claim source-only scope |

Do not silently expand implementation scope to add these backend features. Deliver
the supported UI and document any remaining proposed extension with its contract.

## Failure handling

- 400/415/422: retain inputs and show appropriate validation/format/extraction error.
- 401: clear inaccessible workspace state and offer sign-in again.
- 404 on owned resources: do not disclose another owner's resource existence.
- 409: explain ongoing operation and refresh current status.
- 429: honor `Retry-After`; avoid an immediate automatic retry loop.
- 502/network failure: preserve input, provide retry where safe, and avoid duplicate
  source submission when the original result is uncertain.
- A missing recovery record must not prevent displaying available source metadata.
- Source provider errors may contain technical details; prefer safe, bounded,
  user-facing text, with diagnostics in a separate section.

## Existing frontend migration targets

Keep `apps/web/src/App.jsx` as authentication routing. Apply the new shell to
WorkspaceApp/WorkspaceSidebar; preserve workspace-scoped remounting. Migrate
Library's existing workflow components rather than copying all prototype handlers.
Keep safeUrl and authenticated request handling. Migrate Studio's GenerateTool,
AnalyzeMedia, and AnalysisResult to Mantine. Move YouTubeImport into the shared
AddSource flow. Keep Activity's request cancellation and terminal polling behavior.
Use explicit adapters between API responses and visual props, with focused tests.
