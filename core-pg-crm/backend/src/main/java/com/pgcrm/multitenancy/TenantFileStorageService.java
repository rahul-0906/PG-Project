package com.pgcrm.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service providing per-tenant isolated filesystem storage directories.
 *
 * <p>Enforces Section 6 requirement: "File storage (uploads, exports) uses a per-tenant path prefix".</p>
 */
@Service
@Slf4j
public class TenantFileStorageService {

    private final Path rootStoragePath;

    public TenantFileStorageService(@Value("${app.storage.root-dir:./data/storage}") String rootDir) {
        this.rootStoragePath = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    /**
     * Resolves and creates the root directory for the active tenant.
     *
     * @return Path pointing to the active tenant's isolated directory
     */
    public Path getTenantStorageDirectory() {
        String tenantSlug = TenantContext.getCurrentTenantSlug();
        String safeFolder = (tenantSlug != null && !tenantSlug.isBlank()) ? tenantSlug : "default";

        Path tenantPath = rootStoragePath.resolve(safeFolder).normalize();

        // Path traversal guard
        if (!tenantPath.startsWith(rootStoragePath)) {
            throw new SecurityException("Illegal directory path traversal attempt for tenant: " + safeFolder);
        }

        try {
            if (!Files.exists(tenantPath)) {
                Files.createDirectories(tenantPath);
            }
        } catch (IOException e) {
            log.error("Failed to create tenant storage directory {}: {}", tenantPath, e.getMessage());
            throw new IllegalStateException("Failed to initialize storage directory", e);
        }

        return tenantPath;
    }

    /**
     * Resolves a subfolder (e.g. "uploads", "exports", "invoices") under the active tenant's directory.
     *
     * @param subfolder subfolder name
     * @return Path to the tenant subfolder
     */
    public Path resolveSubfolder(String subfolder) {
        Path base = getTenantStorageDirectory();
        Path subPath = base.resolve(subfolder).normalize();

        if (!subPath.startsWith(base)) {
            throw new SecurityException("Illegal subpath traversal attempt: " + subfolder);
        }

        try {
            if (!Files.exists(subPath)) {
                Files.createDirectories(subPath);
            }
        } catch (IOException e) {
            log.error("Failed to create subfolder {}: {}", subPath, e.getMessage());
            throw new IllegalStateException("Failed to create storage subfolder", e);
        }

        return subPath;
    }
}
