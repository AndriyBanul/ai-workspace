package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.config.LocalStorageProperties;
import com.aiworkspace.workspaces.models.FileStorageRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageTest {

    @TempDir
    private Path storageRoot;

    @Test
    void storesAndReadsFileByGeneratedStorageKey() throws Exception {
        LocalFileStorage storage = newStorage();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        var storedFile = storage.store(FileStorageRequest.builder()
                .workspaceId("workspace-1")
                .fileId("file-1")
                .originalFilename("notes.txt")
                .contentType("text/plain")
                .content(new ByteArrayInputStream(content))
                .build());

        assertEquals("workspace-1/file-1", storedFile.storageKey());
        assertEquals(content.length, storedFile.sizeBytes());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", storedFile.checksumSha256());
        assertTrue(storage.exists(storedFile.storageKey()));
        assertTrue(Files.exists(storageRoot.resolve("workspace-1/file-1")));

        try (var input = storage.read(storedFile.storageKey())) {
            assertArrayEquals(content, input.readAllBytes());
        }
    }

    @Test
    void deletesStoredFile() throws Exception {
        LocalFileStorage storage = newStorage();
        var storedFile = storage.store(FileStorageRequest.builder()
                .workspaceId("workspace-1")
                .fileId("file-1")
                .originalFilename("notes.txt")
                .content(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))
                .build());

        storage.delete(storedFile.storageKey());

        assertFalse(storage.exists(storedFile.storageKey()));
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        LocalFileStorage storage = newStorage();

        assertThrows(IllegalArgumentException.class, () -> storage.store(FileStorageRequest.builder()
                .workspaceId("../workspace")
                .fileId("file-1")
                .originalFilename("notes.txt")
                .content(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))
                .build()));
    }

    @Test
    void rejectsStorageKeysOutsideRoot() {
        LocalFileStorage storage = newStorage();

        assertThrows(IllegalArgumentException.class, () -> storage.exists("../outside"));
    }

    private LocalFileStorage newStorage() {
        return new LocalFileStorage(new LocalStorageProperties(storageRoot));
    }
}
