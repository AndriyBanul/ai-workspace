# Architecture Diagrams

This file documents the current AI Workspace architecture and core workflows.
The diagrams use Mermaid so they can be rendered directly by GitHub and most
Markdown tooling.

## 1. System Context

This diagram shows AI Workspace as a product boundary and the external systems
it depends on.

```mermaid
flowchart LR
    user["User / Future UI"]
    api["AI Workspace API\nSpring Boot modular monolith"]
    llm["External LLM APIs\nGemini / future providers"]
    opensearch[("OpenSearch\nknowledge-items index")]
    postgres[("PostgreSQL\ningestion jobs + future metadata")]
    redis[("Redis\nfuture async/job/cache support")]
    localServices["Optional local AI services\nWhisper / Piper"]

    user -->|"HTTP API requests\nuploads, questions"| api
    api -->|"store and read\nworkspace knowledge"| opensearch
    api -->|"prompt + workspace context"| llm
    llm -->|"AI answer / descriptions"| api
    api -->|"ingestion job status\nfuture metadata persistence"| postgres
    api -.->|"future job state / queues / cache"| redis
    api -.->|"optional speech and voice workflows"| localServices
```

## 2. Container Architecture

This diagram shows the main modules inside the current modular monolith.

```mermaid
flowchart TB
    subgraph app["AI Workspace Spring Boot application"]
        api["apps/api\nHTTP controllers"]
        orchestrator["apps/orchestrator\nasync multimodal coordination"]
        documents["apps/documents\ndocument parsing and web extraction"]
        audio["apps/audio\nspeech-to-text and text-to-speech"]
        images["apps/images\nimage description and generation"]
        videos["apps/videos\nvideo description and generation"]
        knowledge["apps/knowledge\nworkspace knowledge and answers"]
        workspaces["apps/workspaces\nworkspace metadata"]
        shared["apps/shared\nshared stable models"]
    end

    opensearch[("OpenSearch\nknowledge-items index")]
    postgres[("PostgreSQL\ningestion job tables")]
    llm["Gemini / external AI providers"]
    whisper["Whisper\noptional local service"]
    piper["Piper\noptional local service"]

    api --> orchestrator
    api --> documents
    api --> audio
    api --> images
    api --> videos
    api --> knowledge
    api --> workspaces
    orchestrator --> documents
    orchestrator --> audio
    orchestrator --> images
    orchestrator --> videos
    orchestrator --> knowledge
    orchestrator --> workspaces
    documents --> knowledge
    audio --> knowledge
    images --> knowledge
    videos --> knowledge
    knowledge --> opensearch
    workspaces --> postgres
    orchestrator --> postgres
    knowledge --> llm
    audio -.-> whisper
    audio -.-> piper
    images --> llm
    videos --> llm
    api -.-> shared
    orchestrator -.-> shared
```

## 3. Component Diagram

This diagram shows the main components involved in the orchestrator and
knowledge flows.

```mermaid
flowchart TB
    subgraph api["apps/api"]
        orchestratorController["OrchestratorController"]
        knowledgeController["KnowledgeController"]
        documentController["DocumentController"]
        audioController["AudioController"]
        imageController["ImageController"]
        videoController["VideoController"]
    end

    subgraph orchestratorModule["apps/orchestrator"]
        orchestratorService["OrchestratorService"]
        taskExecutor["orchestratorTaskExecutor"]
    end

    subgraph contentModules["content modules"]
        documentService["DocumentService"]
        audioService["AudioService"]
        imageService["ImageService"]
        videoService["VideoService"]
    end

    subgraph knowledgeModule["apps/knowledge"]
        knowledgeService["KnowledgeService"]
        knowledgeRepository["KnowledgeRepository"]
        answerProvider["KnowledgeAnswerProvider"]
        geminiAnswerClient["GeminiKnowledgeAnswerClient"]
    end

    subgraph workspacesModule["apps/workspaces"]
        workspaceService["WorkspaceService"]
        workspaceRepository["WorkspaceRepository"]
    end

    opensearch[("OpenSearch")]
    postgres[("PostgreSQL")]
    gemini["Gemini API"]

    orchestratorController --> orchestratorService
    knowledgeController --> knowledgeService
    orchestratorService --> workspaceService
    documentController --> documentService
    audioController --> audioService
    imageController --> imageService
    videoController --> videoService

    orchestratorService --> taskExecutor
    taskExecutor --> documentService
    taskExecutor --> audioService
    taskExecutor --> imageService
    taskExecutor --> videoService
    taskExecutor --> knowledgeService

    documentController --> knowledgeService
    audioController --> knowledgeService
    imageController --> knowledgeService
    videoController --> knowledgeService

    knowledgeService --> knowledgeRepository
    knowledgeRepository --> opensearch
    workspaceService --> workspaceRepository
    workspaceRepository --> postgres
    knowledgeService --> answerProvider
    answerProvider --> geminiAnswerClient
    geminiAnswerClient --> gemini
```

## 4. Sequence Diagrams

### Multimodal Ingestion

This diagram shows the current orchestrator upload flow. The endpoint accepts
any combination of document, audio, image, and video files. Missing or empty
content types are skipped.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant API as OrchestratorController
    participant Orchestrator as OrchestratorService
    participant Executor as orchestratorTaskExecutor
    participant Documents as DocumentService
    participant Audio as AudioService
    participant Images as ImageService
    participant Videos as VideoService
    participant Knowledge as KnowledgeService
    participant OS as OpenSearch

    User->>API: POST /api/v1/orchestrator/ingestions with workspaceId
    API->>API: Read multipart files into bytes
    API->>Orchestrator: ingest(workspaceId, document, audio, image, video)
    Orchestrator->>Executor: submit task for each present content type
    API-->>User: 202 Accepted with submitted/skipped modules

    par Document present
        Executor->>Documents: extractDocumentText(file)
        Documents-->>Executor: extracted text
        Executor->>Knowledge: recordDocumentsInfo(text)
        Knowledge->>OS: add document knowledge item for workspaceId
    and Audio present
        Executor->>Audio: transcribe(file)
        Audio-->>Executor: transcript
        Executor->>Knowledge: recordAudioInfo(transcript)
        Knowledge->>OS: add audio knowledge item for workspaceId
    and Image present
        Executor->>Images: describe(file)
        Images-->>Executor: image description
        Executor->>Knowledge: recordImagesInfo(description)
        Knowledge->>OS: add image knowledge item for workspaceId
    and Video present
        Executor->>Videos: describe(file)
        Videos-->>Executor: video description
        Executor->>Knowledge: recordVideoInfo(description)
        Knowledge->>OS: add video knowledge item for workspaceId
    end
```

### Ask Workspace

This diagram shows how the workspace question endpoint retrieves workspace
knowledge, builds context, calls an LLM, and returns the answer.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant API as KnowledgeController
    participant Knowledge as KnowledgeService
    participant Repository as KnowledgeRepository
    participant OS as OpenSearch
    participant AnswerProvider as KnowledgeAnswerProvider
    participant Gemini as GeminiKnowledgeAnswerClient

    User->>API: POST /api/v1/knowledge/workspaces/{workspaceId}/answers
    API->>API: Validate request body and question
    API->>Knowledge: answerWorkspaceQuestion(workspaceId, question)
    Knowledge->>Repository: searchKnowledgeItems(workspaceId, question, topN)
    Repository->>OS: search top relevant knowledge items
    OS-->>Repository: KnowledgeItem hits
    Repository-->>Knowledge: relevant items
    Knowledge->>Knowledge: Build context from top snippets
    Knowledge->>AnswerProvider: answer(question, context)
    AnswerProvider->>Gemini: prompt with question + context
    Gemini-->>AnswerProvider: answer text
    AnswerProvider-->>Knowledge: answer text
    Knowledge-->>API: WorkspaceKnowledgeAnswer
    API-->>User: 200 OK with answer
```

## 5. Workflow Flowchart

This diagram shows the decision logic inside the multimodal ingestion workflow.

```mermaid
flowchart TD
    start([POST /api/v1/orchestrator/ingestions])
    readFiles["Read multipart files into byte arrays"]
    documentPresent{"document present\nand non-empty?"}
    audioPresent{"audio present\nand non-empty?"}
    imagePresent{"image present\nand non-empty?"}
    videoPresent{"video present\nand non-empty?"}

    skipDocument["Add documents to skipped"]
    skipAudio["Add audio to skipped"]
    skipImage["Add images to skipped"]
    skipVideo["Add videos to skipped"]

    submitDocument["Submit async document task"]
    submitAudio["Submit async audio task"]
    submitImage["Submit async image task"]
    submitVideo["Submit async video task"]

    documentFlow["Extract document text\nand add document knowledge item"]
    audioFlow["Transcribe audio\nand add audio knowledge item"]
    imageFlow["Describe image\nand add image knowledge item"]
    videoFlow["Describe video\nand add video knowledge item"]

    response["Return 202 Accepted\nsubmitted + skipped"]
    background["Async tasks continue in background"]
    done([Workspace knowledge updated])

    start --> readFiles
    readFiles --> documentPresent
    documentPresent -- yes --> submitDocument
    documentPresent -- no --> skipDocument
    submitDocument --> audioPresent
    submitDocument -.-> documentFlow
    skipDocument --> audioPresent

    audioPresent -- yes --> submitAudio
    audioPresent -- no --> skipAudio
    submitAudio --> imagePresent
    submitAudio -.-> audioFlow
    skipAudio --> imagePresent

    imagePresent -- yes --> submitImage
    imagePresent -- no --> skipImage
    submitImage --> videoPresent
    submitImage -.-> imageFlow
    skipImage --> videoPresent

    videoPresent -- yes --> submitVideo
    videoPresent -- no --> skipVideo
    submitVideo --> response
    submitVideo -.-> videoFlow
    skipVideo --> response

    response --> background
    documentFlow --> done
    audioFlow --> done
    imageFlow --> done
    videoFlow --> done
```

## 6. Data Flow

This diagram shows how messy multimodal input becomes workspace memory and then
LLM-ready context. Domain services validate input through module-local validator
beans, then call provider interfaces instead of concrete AI clients directly.

```mermaid
flowchart LR
    user["User / API client"]
    controllers["HTTP controllers\nthin request adapters"]
    ownership["Auth + workspace ownership\nSpring Security + services"]

    subgraph ingestion["Ingestion and processing"]
        orchestrator["OrchestratorService\nasync fan-out"]
        validators["Module validator beans\nrequest + file checks"]
        documentService["DocumentService\nextract text"]
        audioService["AudioService\ntranscribe / synthesize"]
        imageService["ImageService\ndescribe / generate"]
        videoService["VideoService\ndescribe / generate"]
    end

    subgraph providerPorts["Capability provider ports"]
        stt["SpeechToTextProvider"]
        tts["TextToSpeechProvider"]
        imageUnderstanding["ImageUnderstandingProvider"]
        imageGeneration["ImageGenerationProvider"]
        videoUnderstanding["VideoUnderstandingProvider"]
        videoGeneration["VideoGenerationProvider"]
        answerProvider["KnowledgeAnswerProvider"]
    end

    subgraph providerClients["Provider clients"]
        whisper["WhisperClient"]
        piper["PiperClient"]
        geminiImage["GeminiImageClient"]
        flux["FluxImageClient"]
        geminiVideo["GeminiVideoClient"]
        veo["VeoVideoClient"]
        geminiAnswer["GeminiKnowledgeAnswerClient"]
    end

    config["Shared Spring config beans\nRestClient + ObjectMapper"]
    localAI["Local AI services\nWhisper / Piper"]
    externalAI["External AI APIs\nGemini / FLUX / Veo"]

    normalized["Normalized text knowledge\nfacts, notes, descriptions"]
    knowledgeService["KnowledgeService\ncontext + answer flow"]
    opensearch[("OpenSearch knowledge-items\nappend-only workspace memory")]
    postgres[("PostgreSQL\nworkspaces + ingestion jobs")]
    prompt["LLM prompt\nquestion + context"]
    answer["Workspace answer\nreturned to API client"]

    user -->|"uploads / questions"| controllers
    controllers --> ownership
    ownership --> orchestrator
    ownership --> knowledgeService

    orchestrator --> postgres
    orchestrator --> validators
    validators --> documentService
    validators --> audioService
    validators --> imageService
    validators --> videoService

    documentService --> normalized
    audioService --> stt
    audioService --> tts
    imageService --> imageUnderstanding
    imageService --> imageGeneration
    videoService --> videoUnderstanding
    videoService --> videoGeneration

    stt --> whisper
    tts --> piper
    imageUnderstanding --> geminiImage
    imageGeneration --> flux
    videoUnderstanding --> geminiVideo
    videoGeneration --> veo

    whisper --> localAI
    piper --> localAI
    geminiImage --> externalAI
    flux --> externalAI
    geminiVideo --> externalAI
    veo --> externalAI

    audioService --> normalized
    imageService --> normalized
    videoService --> normalized

    normalized --> knowledgeService
    knowledgeService --> opensearch
    opensearch --> knowledgeService
    knowledgeService --> prompt
    prompt --> answerProvider
    answerProvider --> geminiAnswer
    geminiAnswer --> externalAI
    answerProvider --> knowledgeService
    knowledgeService --> answer
    answer --> controllers

    config -.-> whisper
    config -.-> piper
    config -.-> geminiImage
    config -.-> flux
    config -.-> geminiVideo
    config -.-> veo
    config -.-> geminiAnswer
```
