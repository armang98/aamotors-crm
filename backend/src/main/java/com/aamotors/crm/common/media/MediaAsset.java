package com.aamotors.crm.common.media;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32) private String folder;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "content_type", nullable = false, length = 64) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MediaAsset() {}

    public MediaAsset(String folder, String fileName, String contentType, long sizeBytes, Instant createdAt) {
        this.folder = folder;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getFolder() { return folder; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }

    public String publicUrl() { return "/api/public/media/" + folder + "/" + fileName; }
}
