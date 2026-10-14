package com.aamotors.crm.settings;

import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.media.MediaStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Serves the business logo without authentication so the login page can show it. */
@RestController
@RequestMapping("/api/public/media")
public class PublicMediaController {

    private final MediaStorageService media;

    public PublicMediaController(MediaStorageService media) {
        this.media = media;
    }

    @GetMapping("/logo/{fileName:[A-Za-z0-9\\-]+\\.[a-z]{3,4}}")
    public ResponseEntity<Resource> logo(@PathVariable String fileName) throws IOException {
        Path path = media.resolve(SettingsService.LOGO_FOLDER, fileName)
                .orElseThrow(() -> ApiException.notFound("Logo not found"));
        String type = Files.probeContentType(path);
        MediaType mediaType = type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
