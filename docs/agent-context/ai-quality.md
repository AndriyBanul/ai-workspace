# AI Quality Rules

- Keep prompts versioned and reviewable.
- Do not hardcode provider-specific assumptions in business logic.
- Separate retrieval, prompting, model execution, and post-processing.
- Add evaluation cases for important AI workflows when possible.
- Treat AI output as untrusted until validated by application logic.
- Keep provider DTOs out of business modules.
- Make model selection and provider configuration explicit.
- Preserve room for multimodal workflows: text, documents, images, audio, and video.

AI features should be designed for repeatability, observability, and graceful failure.
