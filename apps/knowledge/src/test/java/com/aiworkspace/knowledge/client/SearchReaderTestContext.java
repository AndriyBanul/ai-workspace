package com.aiworkspace.knowledge.client;

import org.springframework.test.web.client.MockRestServiceServer;

public record SearchReaderTestContext(OpenSearchKnowledgeSearchReader reader, MockRestServiceServer server) {
}
