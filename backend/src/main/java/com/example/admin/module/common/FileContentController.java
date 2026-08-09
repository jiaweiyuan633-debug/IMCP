package com.example.admin.module.common;

import com.example.admin.module.system.entity.SysFileDO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class FileContentController {

    private final FileStorageManager fileStorageManager;

    @GetMapping("/files/{id}")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long id) {
        SysFileDO file = fileStorageManager.getById(id);
        return stream(file, true);
    }

    @GetMapping("/api/system/file/{id}/download")
    @PreAuthorize("hasAuthority('system:file:list')")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        SysFileDO file = fileStorageManager.getOwnedOrThrow(id);
        return stream(file, false);
    }

    private ResponseEntity<InputStreamResource> stream(SysFileDO file, boolean inline) {
        InputStream inputStream = fileStorageManager.open(file);
        MediaType mediaType = resolveMediaType(file);
        ContentDisposition disposition = inline
                ? ContentDisposition.inline()
                .filename(file.getOriginalName() == null ? "file" : file.getOriginalName(), StandardCharsets.UTF_8)
                .build()
                : ContentDisposition.attachment()
                .filename(file.getOriginalName() == null ? "file" : file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
        if (file.getSha256() != null && !file.getSha256().isBlank()) {
            builder.eTag("\"" + file.getSha256() + "\"");
        }
        return builder.body(new InputStreamResource(inputStream));
    }

    private MediaType resolveMediaType(SysFileDO file) {
        Optional<MediaType> detected = MediaTypeFactory.getMediaType(
                file.getOriginalName() == null ? "file" : file.getOriginalName());
        if (detected.isPresent()) {
            return detected.get();
        }
        try {
            return MediaType.parseMediaType(file.getContentType());
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
