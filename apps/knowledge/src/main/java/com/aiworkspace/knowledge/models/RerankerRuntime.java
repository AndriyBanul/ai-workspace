package com.aiworkspace.knowledge.models;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtSession;

public record RerankerRuntime(HuggingFaceTokenizer tokenizer, OrtSession session) {
}
