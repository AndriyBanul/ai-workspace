package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.FileStorageRequest;
import com.aiworkspace.workspaces.models.StoredFile;
import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {

    StoredFile store(FileStorageRequest request) throws IOException;

    InputStream read(String storageKey) throws IOException;

    boolean exists(String storageKey);

    void delete(String storageKey) throws IOException;
}
