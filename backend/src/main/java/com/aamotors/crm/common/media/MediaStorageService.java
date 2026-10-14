package com.aamotors.crm.common.media;

import com.aamotors.crm.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded media on the local filesystem under {@code app.media.root}.
 * File names are server generated (UUID + validated extension) so user supplied
 * names never touch the filesystem. Content type is validated against an allow list.
 */
@Service
public class MediaStorageService {

    public static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final Path root;
    private final MediaProperties properties;

    public MediaStorageService(MediaProperties properties) throws IOException {
        this.properties = properties;
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public record StoredFile(String fileName, String contentType, long size) {}

    public StoredFile storeImage(String folder, MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("upload.empty", "No file uploaded");
        }
        String contentType = file.getContentType();
        if (contentType == null || !IMAGE_TYPES.contains(contentType)) {
            throw ApiException.badRequest("upload.invalidType", "Unsupported file type");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "upload.tooLarge", "File exceeds " + maxBytes + " bytes");
        }
        String fileName = UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        Path dir = resolveFolder(folder);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "upload.failed", "Could not store file");
        }
        return new StoredFile(fileName, contentType, file.getSize());
    }

    public Optional<Path> resolve(String folder, String fileName) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return Optional.empty();
        }
        Path path = resolveFolder(folder).resolve(fileName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    public void delete(String folder, String fileName) {
        resolve(folder, fileName).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // best effort
            }
        });
    }

    public long logoMaxBytes() {
        return properties.logoMaxBytes();
    }

    private Path resolveFolder(String folder) {
        if (folder == null || folder.contains("..") || folder.contains("/")) {
            throw new IllegalArgumentException("Invalid media folder");
        }
        return root.resolve(folder).normalize();
    }
}
