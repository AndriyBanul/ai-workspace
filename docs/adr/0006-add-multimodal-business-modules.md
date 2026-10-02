# ADR 0006: Add Multimodal Business Modules

## Status

Accepted

## Context

The platform is intended to process multiple content types: documents, images, videos, and audio.

The project already uses an apps-level Gradle multi-module structure with:

- `api` as the executable Spring Boot HTTP entrypoint.
- `shared` as the shared data model library.

Business logic should not accumulate inside `api`. Each major content type has its own lifecycle and processing pipeline, so separate modules make the boundaries explicit in IntelliJ IDEA and Gradle.

## Decision

Add these Java library Gradle modules:

```text
apps/documents
apps/images
apps/videos
apps/audio
```

Register them in `apps/settings.gradle`:

```gradle
include 'documents'
include 'images'
include 'videos'
include 'audio'
```

Use these Java package names:

```text
com.aiworkspace.documents
com.aiworkspace.images
com.aiworkspace.videos
com.aiworkspace.audio
```

Each business module depends on `shared`.

The `api` module depends on all business modules and remains the HTTP entrypoint.

## Module Responsibilities

### `documents`

Owns document-related business logic:

- document ingestion
- document metadata
- text extraction
- document lifecycle
- document processing status

### `images`

Owns image-related business logic:

- image ingestion
- OCR
- image analysis
- image metadata
- vision processing entry points

### `videos`

Owns video-related business logic:

- video ingestion
- frame extraction
- scene analysis
- video metadata
- video understanding workflows

### `audio`

Owns audio-related business logic:

- audio ingestion
- speech-to-text
- text-to-speech
- diarization
- audio metadata

Use `audio`, not `voice`, because audio is broader and can include voice-specific use cases later.

## Boundary Rules

- Keep HTTP controllers in `api`.
- Keep business logic in the relevant business module.
- Keep shared cross-module data models in `shared`.
- Business modules may depend on `shared`.
- Business modules should not depend on `api`.
- Avoid direct coupling between business modules until a real workflow requires it.

## Alternatives Considered

### Single `media` Module

This would reduce the number of modules, but documents, images, videos, and audio have different processing lifecycles and will likely grow independently.

### Keep All Business Logic In `api`

This is faster initially, but it would make the executable HTTP application a catch-all module and blur domain boundaries.

### Use `voice` Instead Of `audio`

`voice` is too narrow. `audio` covers speech-to-text, text-to-speech, voice commands, calls, meetings, podcasts, and future audio workflows.

## Consequences

Positive consequences:

- Clear module boundaries for multimodal business logic.
- IntelliJ IDEA shows each content type as a separate module.
- `api` remains focused on HTTP entry points.
- Future processing pipelines can evolve independently.

Trade-offs:

- More Gradle modules from the start.
- Cross-module dependencies must be managed carefully.
- Empty modules should stay minimal until real implementation begins.
