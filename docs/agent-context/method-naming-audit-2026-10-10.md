# Java method naming audit

Date: 2026-10-10.

Reviewed handwritten Java method declarations under `apps/*/src/main/java` and `apps/*/src/test/java`, including package-private helpers and interfaces. Generated code and build output are excluded.

This report lists the noun-based and adjective-based behavior method names identified during the audit and their applied verb-based replacements. The refactor is complete across production code, test helpers, call sites, method references, and JUnit method-source strings. No tests were run.

## Refactor verification

- Renamed all 242 listed declarations across 90 Java files, including overloads.
- Applied 927 symbol edits, including the JUnit `@MethodSource` reference.
- Verified each changed Java file contains exactly the planned naming edits.
- Production, test, and test-fixture compilation passed, together with `verifyModuleBoundaries`.
- `git diff --check` passed.
- Preserved conventional accessors, framework callbacks, Spring bean identities, HTTP paths, and response models.
- Used `WorkspaceFile.hasSourceUrl()` instead of the initially suggested `isUrlBacked()` to avoid introducing a Jackson boolean-getter property into API responses.

## Counts

- Production: 175 class-and-name entries covering 179 declarations.
- Related predicate naming: 6 entries.
- Test helpers: 54 class-and-name entries covering 57 declarations.
- Overloads appear in one row, with a source link for each declaration.

## Priority and interpretation

Public service renames include: `VideoService.knowledgeText`, `AudioService.chunksForKnowledge`, `AudioService.knowledgeSourceMetadata`, `KnowledgeCitationService.answerContext`, `UserAccountService.currentUser`, and `UserAccountService.currentUserId`.

`OrchestratorService.singleSourceJob` creates and persists a job. `createSingleSourceJob` communicates that side effect.

`VideoService.knowledgeText` formats a visual summary as well as spoken transcript content. `formatVideoForKnowledge` describes its entire output.

Use `build` or `create` for construction, `format` for presentation, `parse` or `read` for interpreting responses, `resolve` for fallback selection, and `compute` for calculations.

The refactor used resolved Java method symbols rather than global text replacement. Familiar static factories such as `disabled()` and `noop()` were also renamed to the audited `create...` forms.

## Production candidates

| Module | Class | Previous method | Applied method | Source |
| --- | --- | --- | --- | --- |
| api | GlobalExceptionHandler | `error` | `buildErrorResponse` | [L113](../../apps/api/src/main/java/com/aiworkspace/config/GlobalExceptionHandler.java#L113), [L117](../../apps/api/src/main/java/com/aiworkspace/config/GlobalExceptionHandler.java#L117) |
| api | GlobalExceptionHandler | `detail` | `resolveErrorDetail` | [L133](../../apps/api/src/main/java/com/aiworkspace/config/GlobalExceptionHandler.java#L133), [L142](../../apps/api/src/main/java/com/aiworkspace/config/GlobalExceptionHandler.java#L142) |
| api | RestClientConfig | `restClient` | `buildRestClient` | [L27](../../apps/api/src/main/java/com/aiworkspace/config/RestClientConfig.java#L27) |
| api | AuthController | `me` | `getCurrentUser` | [L34](../../apps/api/src/main/java/com/aiworkspace/controllers/AuthController.java#L34) |
| api | OrchestratorController | `contentFrom` | `toOrchestrationContent` | [L70](../../apps/api/src/main/java/com/aiworkspace/controllers/OrchestratorController.java#L70) |
| api | ApiRateLimitFilter | `clientFingerprint` | `computeClientFingerprint` | [L52](../../apps/api/src/main/java/com/aiworkspace/security/ApiRateLimitFilter.java#L52) |
| api | SecurityAuditService | `actorFingerprint` | `computeActorFingerprint` | [L25](../../apps/api/src/main/java/com/aiworkspace/security/SecurityAuditService.java#L25) |
| api | SecurityAuditService | `safeToken` | `sanitizeToken` | [L36](../../apps/api/src/main/java/com/aiworkspace/security/SecurityAuditService.java#L36) |
| api | SecurityAuditService | `safePath` | `sanitizePath` | [L43](../../apps/api/src/main/java/com/aiworkspace/security/SecurityAuditService.java#L43) |
| api | UserQuotaFilter | `actionFor` | `resolveAction` | [L53](../../apps/api/src/main/java/com/aiworkspace/security/UserQuotaFilter.java#L53) |
| audio | PiperClient | `piperFailureMessage` | `resolvePiperFailureMessage` | [L74](../../apps/audio/src/main/java/com/aiworkspace/audio/client/PiperClient.java#L74) |
| audio | PiperClient | `wav` | `encodeWav` | [L130](../../apps/audio/src/main/java/com/aiworkspace/audio/client/PiperClient.java#L130) |
| audio | WhisperClient | `transcriptionUri` | `buildTranscriptionUri` | [L77](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L77) |
| audio | WhisperClient | `multipartBody` | `buildMultipartBody` | [L81](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L81) |
| audio | WhisperClient | `safeFilename` | `escapeMultipartFilename` | [L96](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L96) |
| audio | WhisperClient | `textValue` | `readTextValue` | [L104](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L104) |
| audio | WhisperClient | `transcriptionFrom` | `parseTranscription` | [L109](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L109) |
| audio | WhisperClient | `segmentsFrom` | `parseSegments` | [L117](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L117) |
| audio | WhisperClient | `timestampMilliseconds` | `readTimestampMilliseconds` | [L136](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L136) |
| audio | WhisperClient | `firstText` | `readFirstNonBlankText` | [L163](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L163) |
| audio | WhisperClient | `normalizedOptionalText` | `readNormalizedOptionalText` | [L168](../../apps/audio/src/main/java/com/aiworkspace/audio/client/WhisperClient.java#L168) |
| audio | AudioService | `chunksForKnowledge` | `createKnowledgeChunks` | [L74](../../apps/audio/src/main/java/com/aiworkspace/audio/services/AudioService.java#L74) |
| audio | AudioService | `knowledgeSourceMetadata` | `createKnowledgeSourceMetadata` | [L109](../../apps/audio/src/main/java/com/aiworkspace/audio/services/AudioService.java#L109) |
| audio | AudioService | `normalizedSpeaker` | `normalizeSpeaker` | [L113](../../apps/audio/src/main/java/com/aiworkspace/audio/services/AudioService.java#L113) |
| audio | AudioService | `timestamp` | `formatTimestamp` | [L117](../../apps/audio/src/main/java/com/aiworkspace/audio/services/AudioService.java#L117) |
| audio | AudioValidator | `extension` | `extractExtension` | [L41](../../apps/audio/src/main/java/com/aiworkspace/audio/services/AudioValidator.java#L41) |
| documents | GenericRestClient | `redirectUri` | `resolveRedirectUri` | [L118](../../apps/documents/src/main/java/com/aiworkspace/documents/client/GenericRestClient.java#L118) |
| documents | GenericRestClient | `maxResponseSizeMessage` | `formatMaxResponseSizeMessage` | [L226](../../apps/documents/src/main/java/com/aiworkspace/documents/client/GenericRestClient.java#L226) |
| documents | DocumentChunker | `sentences` | `splitIntoSentences` | [L77](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentChunker.java#L77) |
| documents | DocumentChunker | `headingLevel` | `detectHeadingLevel` | [L93](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentChunker.java#L93) |
| documents | DocumentService | `textExtractionContext` | `createTextExtractionContext` | [L116](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentService.java#L116) |
| documents | DocumentService | `documentFailure` | `mapDocumentFailure` | [L132](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentService.java#L132) |
| documents | DocumentService | `normalizedOptionalValue` | `normalizeOptionalValue` | [L182](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentService.java#L182) |
| documents | DocumentService | `parserVersion` | `buildParserVersion` | [L189](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentService.java#L189) |
| documents | DocumentStructureContentHandler | `detectedContentType` | `setDetectedContentType` | [L35](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L35) |
| documents | DocumentStructureContentHandler | `blocks` | `collectBlocks` | [L97](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L97) |
| documents | DocumentStructureContentHandler | `structuredText` | `renderStructuredText` | [L107](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L107) |
| documents | DocumentStructureContentHandler | `blockType` | `resolveBlockType` | [L137](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L137) |
| documents | DocumentStructureContentHandler | `elementName` | `normalizeElementName` | [L159](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L159) |
| documents | DocumentStructureContentHandler | `normalizedAttribute` | `readNormalizedAttribute` | [L168](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L168) |
| documents | DocumentStructureContentHandler | `attribute` | `readAttribute` | [L173](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L173) |
| documents | DocumentStructureContentHandler | `firstNonBlank` | `resolveFirstNonBlank` | [L195](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentStructureContentHandler.java#L195) |
| documents | DocxStructureExtractor | `paragraphType` | `classifyParagraph` | [L91](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocxStructureExtractor.java#L91) |
| documents | WebPageContentExtractor | `charsetFromBom` | `detectCharsetFromBom` | [L81](../../apps/documents/src/main/java/com/aiworkspace/documents/services/WebPageContentExtractor.java#L81) |
| documents | WebPageContentExtractor | `bomLength` | `detectBomLength` | [L91](../../apps/documents/src/main/java/com/aiworkspace/documents/services/WebPageContentExtractor.java#L91) |
| documents | WebPageContentExtractor | `mainContentRoot` | `findMainContentRoot` | [L143](../../apps/documents/src/main/java/com/aiworkspace/documents/services/WebPageContentExtractor.java#L143) |
| documents | WebPageContentExtractor | `htmlBlockText` | `extractHtmlBlockText` | [L157](../../apps/documents/src/main/java/com/aiworkspace/documents/services/WebPageContentExtractor.java#L157) |
| documents | WebPageContentExtractor | `parserVersion` | `buildParserVersion` | [L182](../../apps/documents/src/main/java/com/aiworkspace/documents/services/WebPageContentExtractor.java#L182) |
| images | FluxImageClient | `generationUri` | `buildGenerationUri` | [L83](../../apps/images/src/main/java/com/aiworkspace/images/client/FluxImageClient.java#L83) |
| images | FluxImageClient | `requestBody` | `buildRequestBody` | [L87](../../apps/images/src/main/java/com/aiworkspace/images/client/FluxImageClient.java#L87) |
| images | FluxImageClient | `filename` | `resolveImageFilename` | [L97](../../apps/images/src/main/java/com/aiworkspace/images/client/FluxImageClient.java#L97) |
| images | GeminiImageClient | `generationUri` | `buildGenerationUri` | [L76](../../apps/images/src/main/java/com/aiworkspace/images/client/GeminiImageClient.java#L76) |
| images | GeminiImageClient | `requestBody` | `buildRequestBody` | [L81](../../apps/images/src/main/java/com/aiworkspace/images/client/GeminiImageClient.java#L81) |
| images | GeminiImageClient | `descriptionFrom` | `parseDescription` | [L98](../../apps/images/src/main/java/com/aiworkspace/images/client/GeminiImageClient.java#L98) |
| images | GeminiImageClient | `responseJson` | `parseResponseJson` | [L126](../../apps/images/src/main/java/com/aiworkspace/images/client/GeminiImageClient.java#L126) |
| images | OcrResult | `text` | `joinRegionText` | [L10](../../apps/images/src/main/java/com/aiworkspace/images/models/OcrResult.java#L10) |
| images | ImageValidator | `mimeType` | `resolveMimeType` | [L53](../../apps/images/src/main/java/com/aiworkspace/images/services/ImageValidator.java#L53) |
| images | ImageValidator | `extension` | `extractExtension` | [L62](../../apps/images/src/main/java/com/aiworkspace/images/services/ImageValidator.java#L62) |
| knowledge | GeminiKnowledgeAnswerClient | `generationUri` | `buildGenerationUri` | [L118](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/GeminiKnowledgeAnswerClient.java#L118) |
| knowledge | GeminiKnowledgeAnswerClient | `requestBody` | `buildRequestBody` | [L123](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/GeminiKnowledgeAnswerClient.java#L123) |
| knowledge | GeminiKnowledgeAnswerClient | `queryExpansionRequestBody` | `buildQueryExpansionRequestBody` | [L134](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/GeminiKnowledgeAnswerClient.java#L134) |
| knowledge | GeminiKnowledgeAnswerClient | `prompt` | `buildAnswerPrompt` | [L173](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/GeminiKnowledgeAnswerClient.java#L173) |
| knowledge | GeminiKnowledgeAnswerClient | `answerFrom` | `parseAnswer` | [L191](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/GeminiKnowledgeAnswerClient.java#L191) |
| knowledge | GeminiKnowledgeAnswerClient | `responseJson` | `parseResponseJson` | [L219](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/GeminiKnowledgeAnswerClient.java#L219) |
| knowledge | OnnxTextReranker | `arrays` | `extractEncodingArrays` | [L103](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OnnxTextReranker.java#L103) |
| knowledge | OnnxTextReranker | `scores` | `extractScores` | [L111](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OnnxTextReranker.java#L111) |
| knowledge | OnnxTextReranker | `runtime` | `getOrLoadRuntime` | [L136](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OnnxTextReranker.java#L136) |
| knowledge | OnnxTextReranker | `passage` | `buildPassage` | [L174](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OnnxTextReranker.java#L174) |
| knowledge | OpenSearchKnowledgeClient | `sourceTypeFrom` | `mapSourceType` | [L135](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeClient.java#L135) |
| knowledge | OpenSearchKnowledgeClient | `normalizedBaseUri` | `normalizeBaseUri` | [L144](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeClient.java#L144) |
| knowledge | OpenSearchKnowledgeIndexWriter | `sourceGenerationQuery` | `buildSourceGenerationQuery` | [L120](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeIndexWriter.java#L120) |
| knowledge | OpenSearchKnowledgeIndexWriter | `document` | `toIndexDocument` | [L155](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeIndexWriter.java#L155) |
| knowledge | OpenSearchKnowledgeSearchReader | `hybridSearch` | `searchHybrid` | [L68](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReader.java#L68) |
| knowledge | OpenSearchKnowledgeSearchReader | `vectorSearch` | `searchByVector` | [L78](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReader.java#L78) |
| knowledge | OpenSearchKnowledgeSearchReader | `vectorCandidates` | `findVectorCandidates` | [L126](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReader.java#L126) |
| knowledge | OpenSearchKnowledgeSearchReader | `neighborKey` | `buildNeighborKey` | [L143](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReader.java#L143) |
| knowledge | OpenSearchKnowledgeSearchReader | `sourceFilters` | `buildSourceFilters` | [L147](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReader.java#L147) |
| knowledge | OpenSearchKnowledgeSearchReader | `joinedContent` | `joinContent` | [L164](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReader.java#L164) |
| knowledge | OpenSearchKnowledgeStore | `searchUri` | `buildSearchUri` | [L84](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L84) |
| knowledge | OpenSearchKnowledgeStore | `bulkUri` | `buildBulkUri` | [L88](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L88) |
| knowledge | OpenSearchKnowledgeStore | `countUri` | `buildCountUri` | [L92](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L92) |
| knowledge | OpenSearchKnowledgeStore | `deleteByQueryUri` | `buildDeleteByQueryUri` | [L96](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L96) |
| knowledge | OpenSearchKnowledgeStore | `responseException` | `createResponseException` | [L100](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L100) |
| knowledge | OpenSearchKnowledgeStore | `clientException` | `createClientException` | [L105](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L105) |
| knowledge | OpenSearchKnowledgeStore | `invalidResponse` | `createInvalidResponseException` | [L109](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L109) |
| knowledge | OpenSearchKnowledgeStore | `indexUri` | `buildIndexUri` | [L116](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L116) |
| knowledge | OpenSearchKnowledgeStore | `mappingUri` | `buildMappingUri` | [L120](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L120) |
| knowledge | OpenSearchKnowledgeStore | `properties` | `buildMappingProperties` | [L164](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L164) |
| knowledge | OpenSearchKnowledgeStore | `searchHits` | `parseSearchHits` | [L197](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L197) |
| knowledge | OpenSearchKnowledgeStore | `itemFrom` | `toKnowledgeItem` | [L208](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L208) |
| knowledge | OpenSearchKnowledgeStore | `sourceType` | `resolveSourceType` | [L244](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L244) |
| knowledge | OpenSearchKnowledgeStore | `text` | `readText` | [L249](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L249) |
| knowledge | OpenSearchKnowledgeStore | `instant` | `readInstant` | [L254](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L254) |
| knowledge | OpenSearchKnowledgeStore | `integer` | `readInteger` | [L259](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L259) |
| knowledge | OpenSearchKnowledgeStore | `longValue` | `readLong` | [L264](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L264) |
| knowledge | OpenSearchKnowledgeStore | `floats` | `readFloats` | [L269](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeStore.java#L269) |
| knowledge | SpringAiTextEmbeddingClient | `validatedNormalizedVector` | `validateAndNormalizeVector` | [L111](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/client/SpringAiTextEmbeddingClient.java#L111) |
| knowledge | KnowledgeEmbeddingProperties | `positive` | `resolvePositiveValue` | [L26](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeEmbeddingProperties.java#L26) |
| knowledge | KnowledgeQueryExpansionProperties | `disabled` | `createDisabled` | [L16](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeQueryExpansionProperties.java#L16) |
| knowledge | KnowledgeRerankingProperties | `disabled` | `createDisabled` | [L42](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeRerankingProperties.java#L42) |
| knowledge | KnowledgeRerankingProperties | `defaultValue` | `resolveDefaultValue` | [L46](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeRerankingProperties.java#L46) |
| knowledge | KnowledgeRerankingProperties | `requiredWhenEnabled` | `resolveRequiredValue` | [L50](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeRerankingProperties.java#L50) |
| knowledge | KnowledgeRerankingProperties | `positive` | `resolvePositiveValue` | [L60](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeRerankingProperties.java#L60) |
| knowledge | WorkspaceKnowledge | `textOrDefault` | `readTextOrDefault` | [L27](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/models/WorkspaceKnowledge.java#L27) |
| knowledge | WorkspaceKnowledge | `textOrNull` | `readTextOrNull` | [L35](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/models/WorkspaceKnowledge.java#L35) |
| knowledge | SearchTelemetry | `fallback` | `recordFallback` | [L38](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/observability/SearchTelemetry.java#L38) |
| knowledge | KnowledgeCitationService | `answerContext` | `buildAnswerContext` | [L20](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L20) |
| knowledge | KnowledgeCitationService | `sources` | `mapSources` | [L63](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L63) |
| knowledge | KnowledgeCitationService | `sourceFiles` | `collectSourceFiles` | [L67](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L67) |
| knowledge | KnowledgeCitationService | `source` | `toSource` | [L89](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L89) |
| knowledge | KnowledgeCitationService | `sourceFileKey` | `buildSourceFileKey` | [L98](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L98) |
| knowledge | KnowledgeCitationService | `sourceDisplayName` | `resolveSourceDisplayName` | [L105](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L105) |
| knowledge | KnowledgeCitationService | `location` | `formatLocation` | [L111](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L111) |
| knowledge | KnowledgeCitationService | `timestamp` | `formatTimestamp` | [L128](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L128) |
| knowledge | KnowledgeCitationService | `snippet` | `createSnippet` | [L137](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L137) |
| knowledge | KnowledgeCitationService | `valueOrEmpty` | `formatValueOrEmpty` | [L144](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L144) |
| knowledge | KnowledgeCitationService | `nullToEmpty` | `replaceNullWithEmpty` | [L148](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeCitationService.java#L148) |
| knowledge | KnowledgeIndexingService | `embeddingInput` | `buildEmbeddingInput` | [L178](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeIndexingService.java#L178) |
| knowledge | KnowledgeIndexingService | `sha256` | `computeSha256` | [L189](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeIndexingService.java#L189) |
| knowledge | KnowledgeIndexingService | `normalized` | `normalizeOptionalValue` | [L198](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeIndexingService.java#L198) |
| knowledge | KnowledgeIndexingService | `source` | `buildSource` | [L202](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeIndexingService.java#L202) |
| knowledge | KnowledgeRetrievalService | `searchQueries` | `buildSearchQueries` | [L92](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeRetrievalService.java#L92) |
| knowledge | KnowledgeRetrievalService | `retrievalCandidateLimit` | `calculateRetrievalCandidateLimit` | [L136](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeRetrievalService.java#L136) |
| knowledge | KnowledgeService | `sourceTypeFrom` | `mapSourceType` | [L154](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeService.java#L154) |
| orchestrator | IngestionJobService | `stepStatus` | `determineStepStatus` | [L185](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/IngestionJobService.java#L185) |
| orchestrator | IngestionJobService | `details` | `toDetails` | [L201](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/IngestionJobService.java#L201) |
| orchestrator | IngestionJobService | `errorMessage` | `formatErrorMessage` | [L221](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/IngestionJobService.java#L221) |
| orchestrator | OrchestrationSubmissionService | `fingerprint` | `computeFingerprint` | [L109](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestrationSubmissionService.java#L109), [L127](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestrationSubmissionService.java#L127) |
| orchestrator | OrchestrationSubmissionService | `sha256` | `createSha256Digest` | [L134](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestrationSubmissionService.java#L134) |
| orchestrator | OrchestratorService | `pendingJob` | `findPendingJob` | [L248](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestratorService.java#L248) |
| orchestrator | OrchestratorService | `singleSourceJob` | `createSingleSourceJob` | [L304](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestratorService.java#L304) |
| orchestrator | OrchestratorService | `afterCommit` | `runAfterCommit` | [L347](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestratorService.java#L347) |
| orchestrator | OrchestratorService | `contentType` | `mapContentType` | [L415](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/OrchestratorService.java#L415) |
| orchestrator | SourceRecoveryTaskService | `newTask` | `createTask` | [L195](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/SourceRecoveryTaskService.java#L195) |
| orchestrator | SourceRecoveryTaskService | `errorCode` | `resolveErrorCode` | [L224](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/SourceRecoveryTaskService.java#L224) |
| orchestrator | SourceRecoveryTaskService | `errorMessage` | `formatErrorMessage` | [L231](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/SourceRecoveryTaskService.java#L231) |
| orchestrator | SourceRecoveryTracker | `noop` | `createNoop` | [L25](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/SourceRecoveryTracker.java#L25) |
| users | UserAccountService | `currentUser` | `getCurrentUser` | [L67](../../apps/users/src/main/java/com/aiworkspace/users/services/UserAccountService.java#L67) |
| users | UserAccountService | `currentUserId` | `getCurrentUserId` | [L73](../../apps/users/src/main/java/com/aiworkspace/users/services/UserAccountService.java#L73) |
| users | UserAccountService | `displayName` | `resolveDisplayName` | [L100](../../apps/users/src/main/java/com/aiworkspace/users/services/UserAccountService.java#L100) |
| videos | GeminiVideoClient | `generationUri` | `buildGenerationUri` | [L90](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L90) |
| videos | GeminiVideoClient | `requestBody` | `buildRequestBody` | [L95](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L95), [L112](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L112) |
| videos | GeminiVideoClient | `youtubeRequestBody` | `buildYouTubeRequestBody` | [L105](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L105) |
| videos | GeminiVideoClient | `structuredPrompt` | `buildStructuredPrompt` | [L127](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L127) |
| videos | GeminiVideoClient | `analysisFrom` | `parseAnalysis` | [L134](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L134) |
| videos | GeminiVideoClient | `responseSchema` | `buildResponseSchema` | [L171](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L171) |
| videos | GeminiVideoClient | `segmentsFrom` | `parseSegments` | [L194](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L194) |
| videos | GeminiVideoClient | `textValue` | `readTextValue` | [L211](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L211) |
| videos | GeminiVideoClient | `normalizedOptionalText` | `readNormalizedOptionalText` | [L216](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L216) |
| videos | GeminiVideoClient | `responseJson` | `parseResponseJson` | [L221](../../apps/videos/src/main/java/com/aiworkspace/videos/client/GeminiVideoClient.java#L221) |
| videos | VeoVideoClient | `generationUri` | `buildGenerationUri` | [L184](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L184) |
| videos | VeoVideoClient | `operationUri` | `buildOperationUri` | [L189](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L189) |
| videos | VeoVideoClient | `downloadUri` | `buildDownloadUri` | [L197](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L197) |
| videos | VeoVideoClient | `generationRequestBody` | `buildGenerationRequestBody` | [L211](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L211) |
| videos | VeoVideoClient | `inlineVideo` | `decodeInlineVideo` | [L222](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L222) |
| videos | VeoVideoClient | `videoUri` | `extractVideoUri` | [L245](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L245) |
| videos | VeoVideoClient | `firstVideo` | `findFirstVideo` | [L259](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L259) |
| videos | VeoVideoClient | `firstPresent` | `findFirstPresentField` | [L278](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L278) |
| videos | VeoVideoClient | `textValue` | `readTextValue` | [L289](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L289) |
| videos | VeoVideoClient | `responseJson` | `parseResponseJson` | [L294](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L294) |
| videos | VeoVideoClient | `filename` | `resolveVideoFilename` | [L302](../../apps/videos/src/main/java/com/aiworkspace/videos/client/VeoVideoClient.java#L302) |
| videos | VideoService | `canonicalYouTubeUrl` | `canonicalizeYouTubeUrl` | [L73](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoService.java#L73) |
| videos | VideoService | `youtubeVideoId` | `extractYouTubeVideoId` | [L77](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoService.java#L77) |
| videos | VideoService | `knowledgeText` | `formatVideoForKnowledge` | [L81](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoService.java#L81) |
| videos | VideoService | `timestamp` | `formatTimestamp` | [L104](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoService.java#L104) |
| videos | VideoValidator | `canonicalYouTubeUrl` | `canonicalizeYouTubeUrl` | [L81](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L81) |
| videos | VideoValidator | `youtubeVideoId` | `extractYouTubeVideoId` | [L85](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L85) |
| videos | VideoValidator | `videoIdFromYouTubePath` | `extractVideoIdFromYouTubePath` | [L117](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L117) |
| videos | VideoValidator | `queryParameter` | `readQueryParameter` | [L130](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L130) |
| videos | VideoValidator | `firstPathSegment` | `extractFirstPathSegment` | [L143](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L143) |
| videos | VideoValidator | `invalidYouTubeUrl` | `createInvalidYouTubeUrlException` | [L150](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L150) |
| videos | VideoValidator | `mimeType` | `resolveMimeType` | [L154](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L154) |
| videos | VideoValidator | `extension` | `extractExtension` | [L163](../../apps/videos/src/main/java/com/aiworkspace/videos/services/VideoValidator.java#L163) |
| workspaces | LocalFileStorage | `sha256` | `createSha256Digest` | [L80](../../apps/workspaces/src/main/java/com/aiworkspace/workspaces/services/LocalFileStorage.java#L80) |
| workspaces | WorkspaceFileService | `normalizedOptionalValue` | `normalizeOptionalValue` | [L184](../../apps/workspaces/src/main/java/com/aiworkspace/workspaces/services/WorkspaceFileService.java#L184) |

## Related predicate names

These return booleans. They are not noun-based operations, but adding `is` or `has` aligns them with the documented predicate convention.

| Module | Class | Previous method | Applied method | Source |
| --- | --- | --- | --- | --- |
| documents | DocumentChunker | `sameLocation` | `hasSameLocation` | [L120](../../apps/documents/src/main/java/com/aiworkspace/documents/services/DocumentChunker.java#L120) |
| documents | WebPageContentExtractor | `inside` | `isInside` | [L153](../../apps/documents/src/main/java/com/aiworkspace/documents/services/WebPageContentExtractor.java#L153) |
| knowledge | KnowledgeSearchProperties | `vectorOnly` | `isVectorOnly` | [L19](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/config/KnowledgeSearchProperties.java#L19) |
| knowledge | KnowledgeRetrievalService | `rerankingEnabled` | `isRerankingEnabled` | [L157](../../apps/knowledge/src/main/java/com/aiworkspace/knowledge/services/KnowledgeRetrievalService.java#L157) |
| orchestrator | SourceRetryPolicy | `retryable` | `isRetryable` | [L34](../../apps/orchestrator/src/main/java/com/aiworkspace/orchestrator/services/SourceRetryPolicy.java#L34) |
| workspaces | WorkspaceFile | `urlBacked` | `hasSourceUrl` | [L23](../../apps/workspaces/src/main/java/com/aiworkspace/workspaces/models/WorkspaceFile.java#L23) |

## Test helper candidates

Behavioral test names already describe what they verify and are not listed. The following are fixture factories, lookup helpers, formatting helpers, and request helpers.

| Module | Class | Previous method | Applied method | Source |
| --- | --- | --- | --- | --- |
| api | ApiIntegrationTest | `invalidDocuments` | `provideInvalidDocuments` | [L436](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L436) |
| api | ApiIntegrationTest | `uri` | `buildUri` | [L768](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L768) |
| api | ApiIntegrationTest | `workspaceFiles` | `fetchWorkspaceFiles` | [L814](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L814) |
| api | ApiIntegrationTest | `unsupportedImage` | `createUnsupportedImage` | [L823](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L823) |
| api | ApiIntegrationTest | `stepStatus` | `readStepStatus` | [L849](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L849) |
| api | ApiIntegrationTest | `step` | `findStep` | [L853](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L853) |
| api | ApiIntegrationTest | `multipartBody` | `buildMultipartBody` | [L863](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L863), [L876](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L876) |
| api | ApiIntegrationTest | `testEmbedding` | `createTestEmbedding` | [L949](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L949) |
| api | ApiIntegrationTest | `workspaceSources` | `fetchWorkspaceSources` | [L976](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L976) |
| api | ApiIntegrationTest | `joinedContent` | `joinContent` | [L1090](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L1090) |
| api | ApiIntegrationTest | `sourceTypeFrom` | `mapSourceType` | [L1098](../../apps/api/src/test/java/com/aiworkspace/ApiIntegrationTest.java#L1098) |
| api | RetrievalEvaluationTest | `defaultValue` | `extractDefaultValue` | [L148](../../apps/api/src/test/java/com/aiworkspace/knowledge/client/RetrievalEvaluationTest.java#L148) |
| api | RetrievalEvaluationTest | `client` | `createClient` | [L188](../../apps/api/src/test/java/com/aiworkspace/knowledge/client/RetrievalEvaluationTest.java#L188) |
| api | RetrievalEvaluationTest | `hit` | `calculateHitRate` | [L217](../../apps/api/src/test/java/com/aiworkspace/knowledge/client/RetrievalEvaluationTest.java#L217) |
| api | ApiTestUser | `basicAuthHeader` | `buildBasicAuthHeader` | [L8](../../apps/api/src/test/java/com/aiworkspace/models/ApiTestUser.java#L8) |
| api | RateLimitIntegrationTest | `request` | `sendRequest` | [L50](../../apps/api/src/test/java/com/aiworkspace/security/RateLimitIntegrationTest.java#L50) |
| documents | DocumentServiceTest | `html` | `createHtmlClient` | [L589](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L589) |
| documents | DocumentServiceTest | `defaultPage` | `createDefaultPage` | [L603](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L603) |
| documents | DocumentServiceTest | `pdfWithText` | `createPdfWithText` | [L621](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L621) |
| documents | DocumentServiceTest | `pdfWithPages` | `createPdfWithPages` | [L625](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L625) |
| documents | DocumentServiceTest | `docxWithText` | `createDocxWithText` | [L643](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L643) |
| documents | DocumentServiceTest | `docxWithParagraphs` | `createDocxWithParagraphs` | [L647](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L647) |
| documents | DocumentServiceTest | `docxWithHeading` | `createDocxWithHeading` | [L657](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L657) |
| documents | DocumentServiceTest | `xlsxWithText` | `createXlsxWithText` | [L669](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L669) |
| documents | DocumentServiceTest | `docxWithTable` | `createDocxWithTable` | [L679](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L679) |
| documents | DocumentServiceTest | `xlsxWithSheets` | `createXlsxWithSheets` | [L689](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L689) |
| documents | DocumentServiceTest | `pptxWithText` | `createPptxWithText` | [L699](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L699) |
| documents | DocumentServiceTest | `pptxWithSlides` | `createPptxWithSlides` | [L703](../../apps/documents/src/test/java/com/aiworkspace/documents/services/DocumentServiceTest.java#L703) |
| documents | PdfOcrFallbackTest | `pdf` | `createPdf` | [L68](../../apps/documents/src/test/java/com/aiworkspace/documents/services/PdfOcrFallbackTest.java#L68) |
| knowledge | OpenSearchIntegrationTest | `versionedItem` | `createVersionedItem` | [L181](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/client/OpenSearchIntegrationTest.java#L181) |
| knowledge | OpenSearchIntegrationTest | `sectionItem` | `createSectionItem` | [L192](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/client/OpenSearchIntegrationTest.java#L192) |
| knowledge | OpenSearchIntegrationTest | `item` | `createItem` | [L201](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/client/OpenSearchIntegrationTest.java#L201) |
| knowledge | OpenSearchKnowledgeClientTest | `searchResponse` | `buildSearchResponse` | [L308](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeClientTest.java#L308) |
| knowledge | OpenSearchKnowledgeSearchReaderTest | `context` | `createContext` | [L51](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/client/OpenSearchKnowledgeSearchReaderTest.java#L51) |
| knowledge | KnowledgeServiceTest | `candidatesWithEvidence` | `createCandidatesWithEvidence` | [L585](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/services/KnowledgeServiceTest.java#L585) |
| knowledge | KnowledgeServiceTest | `item` | `createItem` | [L594](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/services/KnowledgeServiceTest.java#L594) |
| knowledge | KnowledgeServiceTest | `rerankingProperties` | `createRerankingProperties` | [L604](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/services/KnowledgeServiceTest.java#L604) |
| knowledge | KnowledgeServiceTest | `embeddingService` | `createEmbeddingService` | [L618](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/services/KnowledgeServiceTest.java#L618) |
| knowledge | ReciprocalRankFusionTest | `item` | `createItem` | [L71](../../apps/knowledge/src/test/java/com/aiworkspace/knowledge/services/ReciprocalRankFusionTest.java#L71) |
| orchestrator | IngestionJobServiceTest | `step` | `createStep` | [L250](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/IngestionJobServiceTest.java#L250) |
| orchestrator | IngestionJobServiceTest | `newService` | `createService` | [L257](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/IngestionJobServiceTest.java#L257) |
| orchestrator | SourceLifecycleCoordinatorTest | `job` | `createJob` | [L120](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceLifecycleCoordinatorTest.java#L120) |
| orchestrator | SourceLifecycleCoordinatorTest | `source` | `createSource` | [L126](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceLifecycleCoordinatorTest.java#L126) |
| orchestrator | SourceRecoverySchedulerTest | `context` | `createContext` | [L78](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoverySchedulerTest.java#L78), [L82](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoverySchedulerTest.java#L82) |
| orchestrator | SourceRecoverySchedulerTest | `task` | `createCompletedTask` | [L97](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoverySchedulerTest.java#L97) |
| orchestrator | SourceRecoverySchedulerTest | `scheduledTask` | `createScheduledTask` | [L104](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoverySchedulerTest.java#L104) |
| orchestrator | SourceRecoverySchedulerTest | `source` | `createSource` | [L111](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoverySchedulerTest.java#L111) |
| orchestrator | SourceRecoveryTaskServiceTest | `context` | `createContext` | [L161](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoveryTaskServiceTest.java#L161) |
| orchestrator | SourceRecoveryTaskServiceTest | `source` | `createSource` | [L178](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRecoveryTaskServiceTest.java#L178) |
| orchestrator | SourceRetryPolicyTest | `properties` | `createProperties` | [L51](../../apps/orchestrator/src/test/java/com/aiworkspace/orchestrator/services/SourceRetryPolicyTest.java#L51) |
| users | UserAccountServiceTest | `newService` | `createService` | [L139](../../apps/users/src/test/java/com/aiworkspace/users/services/UserAccountServiceTest.java#L139) |
| workspaces | LocalFileStorageTest | `newStorage` | `createStorage` | [L81](../../apps/workspaces/src/test/java/com/aiworkspace/workspaces/services/LocalFileStorageTest.java#L81) |
| workspaces | WorkspaceFileServiceTest | `fileEntity` | `createFileEntity` | [L142](../../apps/workspaces/src/test/java/com/aiworkspace/workspaces/services/WorkspaceFileServiceTest.java#L142), [L146](../../apps/workspaces/src/test/java/com/aiworkspace/workspaces/services/WorkspaceFileServiceTest.java#L146) |
| workspaces | WorkspaceServiceTest | `newService` | `createService` | [L110](../../apps/workspaces/src/test/java/com/aiworkspace/workspaces/services/WorkspaceServiceTest.java#L110) |

## Names to preserve

Record component accessors, generated getters, simple value accessors, and methods required by framework contracts should retain their conventional names. Examples include `KnowledgeItem.sourceType()`, `TextEmbeddingProvider.model()` / `dimensions()`, exception `code()` / `serviceName()`, enum `apiName()` / `fieldName()`, `SourceProcessingResult.stagedIndex()`, and the SAX and Spring callback overrides.

`OcrResult.text()` is listed because it joins region text rather than returning a record component. Simple getters inside `DocumentStructureContentHandler.ActiveBlock`, and `DocxStructureExtractor.parserVersion()` returning a constant, are treated as value accessors.

Conventional `from(...)` / `fromSource(...)` factories and `with...` transformations are not flagged. `chunk`, `score`, `answer`, `queue`, and `deadLetter` can function as verbs and were not flagged solely because they can also be nouns.

## Spring bean factory names

These are noun-based factory methods, but their names also define Spring bean identities. I recommend preserving them during a behavior-method naming refactor. If renamed later, retain each existing bean name explicitly with `@Bean(name = "...")` and check qualifier references.

| Class | Bean factory methods |
| --- | --- |
| EmbeddingWindowConfig | `embeddingWindowTokenizer`, `embeddingTextSplitter` |
| ObjectMapperConfig | `objectMapper` |
| RestClientConfig | `restClient (public @Bean)`, `webPageRestClient` |
| SecurityConfig | `securityFilterChain`, `passwordEncoder`, `rateLimitFilterRegistration`, `auditFilterRegistration`, `quotaFilterRegistration`, `correlationFilterRegistration` |
| TimeConfig | `applicationClock` |
| OrchestratorConfig | `orchestratorExecutor`, `orchestratorTaskExecutor`, `sourceLeaseScheduler` |
| ApiIntegrationTest | `knowledgeRepository`, `knowledgeAnswerProvider`, `textEmbeddingProvider`, `youTubeVideoUnderstandingProvider` |

The private `RestClientConfig.restClient(..., redirectPolicy)` helper is included in the production list as `buildRestClient`; it is separate from the public bean factory.
