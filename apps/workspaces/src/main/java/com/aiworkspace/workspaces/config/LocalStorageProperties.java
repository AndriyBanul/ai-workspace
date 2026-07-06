package com.aiworkspace.workspaces.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.storage.local")
public record LocalStorageProperties(Path root) {

    public LocalStorageProperties {
        if (root == null) {
            root = Path.of("data/files");
        }
    }
}
