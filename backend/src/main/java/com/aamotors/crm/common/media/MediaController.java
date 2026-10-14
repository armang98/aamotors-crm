package com.aamotors.crm.common.media;

import com.aamotors.crm.common.error.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@Tag(name = "Media")
public class MediaController {

    public static final String IMAGES = "images";
    private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ADVERTISING_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final MediaStorageService storage;
    private final MediaAssetRepository assets;
    private final Clock clock;

    public MediaController(MediaStorageService storage, MediaAssetRepository assets, Clock clock) {
        this.storage = storage;
        this.assets = assets;
        this.clock = clock;
    }

    @PostMapping(value = "/api/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        if (file.getContentType() == null || !ADVERTISING_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("upload.invalidType", "Advertising images must be PNG, JPEG or WebP");
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "upload.tooLargeImage", "Advertising images must be 5 MB or smaller");
        }
        var stored = storage.storeImage(IMAGES, file, IMAGE_MAX_BYTES);
        MediaAsset asset = assets.save(new MediaAsset(IMAGES, stored.fileName(), stored.contentType(), stored.size(), Instant.now(clock)));
        return Map.of("id", asset.getId(), "url", asset.publicUrl(), "contentType", asset.getContentType(), "size", asset.getSizeBytes());
    }

    @GetMapping("/api/public/media/images/{fileName:[A-Za-z0-9\\-]+\\.[a-z]{3,4}}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        Path path = storage.resolve(IMAGES, fileName).orElseThrow(() -> ApiException.notFound("Image not found"));
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
        MediaType type = switch (ext) { case "png" -> MediaType.IMAGE_PNG; case "jpg" -> MediaType.IMAGE_JPEG; case "webp" -> MediaType.parseMediaType("image/webp"); default -> MediaType.APPLICATION_OCTET_STREAM; };
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }
}
