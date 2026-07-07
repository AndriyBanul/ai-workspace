package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.config.LocalStorageProperties;
import com.aiworkspace.workspaces.models.FileStorageRequest;
import com.aiworkspace.workspaces.models.StoredFile;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final FileStorageValidator fileStorageValidator;

    public LocalFileStorage(LocalStorageProperties properties) {
        this(properties, new FileStorageValidator());
    }

    @Autowired
    public LocalFileStorage(LocalStorageProperties properties, FileStorageValidator fileStorageValidator) {
        this.root = properties.root().toAbsolutePath().normalize();
        this.fileStorageValidator = fileStorageValidator;
    }

    @Override
    public StoredFile store(FileStorageRequest request) throws IOException {
        fileStorageValidator.validateRequest(request);
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

    private Path resolve(String storageKey) {
        fileStorageValidator.validateStorageKey(storageKey);

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
