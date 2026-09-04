package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.FileAccessService;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class FileContentController {

    private final FileStorageManager fileStorageManager;
    private final FileAccessService fileAccessService;

    @GetMapping("/files/{id}")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long id,
                                                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        SysFileDO file = fileStorageManager.getById(id);
        return stream(file, true, rangeHeader);
    }

    @GetMapping("/api/system/file/{id}/download")
    @PreAuthorize("hasAuthority('system:file:list')")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id,
                                                        @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        SysFileDO file = fileStorageManager.getOwnedOrThrow(id);
        return stream(file, false, rangeHeader);
    }

    private ResponseEntity<InputStreamResource> stream(SysFileDO file, boolean inline, String rangeHeader) {
        long total = file.getSize() == null ? -1L : file.getSize();
        RangeRequestParser.ByteRange range = total > 0 ? RangeRequestParser.parse(rangeHeader, total) : null;
        if (RangeRequestParser.requested(rangeHeader) && range == null) {
            // 携带 Range 头但范围无效/越界 → 416，并声明当前资源大小
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                    .build();
        }
        if (range != null) {
            return partial(file, range, total);
        }
        return full(file, inline);
    }

    /** 206 部分内容：定位到 start 偏移，按 range.length() 限流输出。 */
    private ResponseEntity<InputStreamResource> partial(SysFileDO file, RangeRequestParser.ByteRange range, long total) {
        InputStream inputStream;
        try {
            inputStream = fileStorageManager.open(file);
            long skipped = inputStream.skip(range.start());
            if (skipped != range.start()) {
                // 底层流比声明的文件更短：视为范围无效
                inputStream.close();
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                        .build();
            }
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                    .build();
        }
        MediaType mediaType = resolveMediaType(file);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + total)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(range.length())
                .cacheControl(privateCacheControl())
                .body(new InputStreamResource(new LimitedInputStream(inputStream, range.length())));
    }

    private ResponseEntity<InputStreamResource> full(SysFileDO file, boolean inline) {
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
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(privateCacheControl());
        if (file.getSha256() != null && !file.getSha256().isBlank()) {
            builder.eTag("\"" + file.getSha256() + "\"");
        }
        return builder.body(new InputStreamResource(inputStream));
    }

    /**
     * R3-1.2：文件受访问令牌保护（URL 携带绑定用户的 token），是私有资源，
     * 只允许浏览器（含缓存复用）缓存，禁止共享代理/CDN 缓存——public 会让
     * 公共缓存保留含 token 的 URL，扩大令牌泄露面。max-age 与令牌有效期对齐，
     * 缓存命中时令牌必然仍有效。
     */
    private CacheControl privateCacheControl() {
        return CacheControl.maxAge(Duration.ofSeconds(fileAccessService.getTokenTtlSeconds())).cachePrivate();
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

    /** 按剩余字节数截断读取的输入流，配合 Content-Length 保证 206 响应体精确匹配 Range 区间。 */
    private static final class LimitedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        private LimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(length, remaining);
            int read = delegate.read(buffer, offset, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
