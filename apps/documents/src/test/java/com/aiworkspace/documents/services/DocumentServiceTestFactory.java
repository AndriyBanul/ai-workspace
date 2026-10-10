package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.config.DocumentExtractionProperties;

final class DocumentServiceTestFactory {

    private DocumentServiceTestFactory() {
    }

    static DocumentService create(GenericRestClient client) {
        return create(client, new DocumentExtractionProperties(null, null, null, null, null));
    }

    static DocumentService create(GenericRestClient client, DocumentExtractionProperties properties) {
        return new DocumentService(client, new DocumentValidator(), properties, new DocxStructureExtractor(),
                new WebPageContentExtractor(), new DocumentChunker(), null);
    }
}
