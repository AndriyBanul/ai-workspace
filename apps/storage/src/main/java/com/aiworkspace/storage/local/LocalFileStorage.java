package com.aiworkspace.storage.local;

import com.aiworkspace.storage.FileStorage;
import com.aiworkspace.storage.config.LocalStorageProperties;
import com.aiworkspace.storage.models.FileStorageRequest;
import com.aiworkspace.storage.models.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(LocalStorageProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(FileStorageRequest request) throws IOException {
        validateRequest(request);
        String storageKey = request.workspaceId().trim() + "/" + request.fileId().trim();
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());

        MessageDigest digest = sha256();
        Path temporaryFile = Files.createTempFile(target.getParent(), request.fileId().trim(), ".tmp");
        long sizeBytes;

        try (InputStream input = request.content();
                OutputStream output = new DigestOutputStream(Files.newOutputStream(temporaryFile), digest)) {
            sizeBytes = input.transferTo(output);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }

        moveIntoPlace(temporaryFile, target);
        return new StoredFile(storageKey, sizeBytes, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream read(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private void validateRequest(FileStorageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("File storage request must not be null");
        }

        validateIdentifier(request.workspaceId(), "Workspace ID");
        validateIdentifier(request.fileId(), "File ID");

        if (request.originalFilename() == null || request.originalFilename().isBlank()) {
            throw new IllegalArgumentException("Original filename must not be blank");
        }

        if (request.content() == null) {
            throw new IllegalArgumentException("File content must not be null");
        }
    }

    private void validateIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        String trimmed = value.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || ".".equals(trimmed) || "..".equals(trimmed)) {
            throw new IllegalArgumentException(fieldName + " must be a storage-safe identifier");
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }

        Path path = root.resolve(storageKey.trim()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Storage key must stay inside storage root");
        }

        return path;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
