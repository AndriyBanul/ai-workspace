# AI Layer

The AI layer should remain provider-independent.

The platform should support:

- OpenAI.
- Anthropic.
- Google Gemini.
- Ollama.
- Future providers.

Switching providers should require configuration changes rather than code changes.

## Design Rules

- Keep provider-specific code behind interfaces.
- Do not leak provider DTOs into business modules.
- Treat prompts, tools, embeddings, and model configuration as explicit application concerns.
- Keep room for future multimodal processing: text, documents, images, audio, and video.
