package com.example.admin.module.common;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.admin.common.BusinessException;
import com.example.admin.common.FileAccessService;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageManager {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final FileStorage fileStorage;
    private final SysFileMapper fileMapper;
    private final StorageQuotaService storageQuotaService;
    private final FileAccessService fileAccessService;
    private final FileVirusScanner fileVirusScanner;
    private final FileUploadProperties uploadProperties;

    public UploadResponse store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        long maxSize = uploadProperties.getMaxSizeMb() * BYTES_PER_MB;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "文件大小不能超过 " + uploadProperties.getMaxSizeMb() + "MB");
        }
        String originalName = normalizeName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!allowedExtensionSet().contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的文件类型");
        }
        byte[] content = readContent(file);
        String contentType = resolveContentType(file, originalName);
        String resolvedCategory = resolveCategory(category, extension, contentType);
        if (!FileMagicValidator.isAllowedContent(content, extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件内容与扩展名不匹配");
        }
        String sha256 = DigestUtil.sha256Hex(content);
        FileVirusScanner.ScanResult scanResult = fileVirusScanner.scan(content, originalName, extension);
        if (!scanResult.clean()) {
            throw new BusinessException(ResultCode.FILE_SCAN_BLOCKED.getCode(), scanResult.message());
        }
        storageQuotaService.check(content.length);
        StoredObject stored = storeObject(content, originalName, contentType, extension, resolvedCategory);
        SysFileDO entity = buildEntity(stored, originalName, content, contentType, resolvedCategory, sha256, scanResult);
        try {
            fileMapper.insert(entity);
        } catch (RuntimeException exception) {
            deleteQuietly(stored.objectKey());
            throw exception;
        }
        String contentUrl = "/files/" + entity.getId();
        entity.setUrl(contentUrl);
        fileMapper.updateById(entity);
        return UploadResponse.builder()
                .id(entity.getId())
                .url(contentUrl)
                .name(originalName)
                .size((long) content.length)
                .contentType(contentType)
                .category(resolvedCategory)
                .sha256(sha256)
                .scanStatus(entity.getScanStatus())
                .contentUrl(contentUrl)
                .accessToken(fileAccessService.issue(contentUrl))
                .build();
    }

    public SysFileDO getOwnedOrThrow(Long id) {
        SysFileDO file = fileMapper.selectById(id);
        if (file == null || !TenantContext.getTenantId().equals(file.getTenantId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return file;
    }

    public SysFileDO getById(Long id) {
        SysFileDO file = fileMapper.selectById(id);
        if (file == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return file;
    }

    public InputStream open(SysFileDO file) {
        try {
            return fileStorage.open(resolveObjectKey(file));
        } catch (Exception exception) {
            log.error("打开文件失败, fileId={}", file.getId(), exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "文件读取失败");
        }
    }

    public void delete(SysFileDO file) {
        deleteQuietly(resolveObjectKey(file));
    }

    public String resolveObjectKey(SysFileDO file) {
        if (StringUtils.hasText(file.getObjectKey())) {
            return file.getObjectKey();
        }
        if (file.getUrl() != null && file.getUrl().startsWith("/uploads/")) {
            return file.getUrl().substring("/uploads/".length());
        }
        if (file.getUrl() != null && file.getUrl().startsWith("/files/")) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "文件对象信息缺失");
        }
        if (file.getUrl() == null || file.getUrl().isBlank()) {
            return null;
        }
        return file.getUrl().substring(file.getUrl().lastIndexOf('/') + 1);
    }

    private SysFileDO buildEntity(StoredObject stored, String originalName, byte[] content, String contentType,
                                  String category, String sha256, FileVirusScanner.ScanResult scanResult) {
        SysFileDO entity = new SysFileDO();
        entity.setTenantId(TenantContext.getTenantId());
        entity.setFileName(stored.objectKey().substring(stored.objectKey().lastIndexOf('/') + 1));
        entity.setOriginalName(originalName);
        entity.setUrl(stored.url());
        entity.setObjectKey(stored.objectKey());
        entity.setSize((long) content.length);
        entity.setStorageType(stored.storageType());
        entity.setContentType(contentType);
        entity.setCategory(category);
        entity.setSha256(sha256);
        entity.setScanStatus(scanResult.message() == null ? "SCANNED" : "SCANNED_WARN");
        entity.setScanMessage(scanResult.message());
        entity.setCreatedBy(SecurityUtils.tryGetUserId());
        return entity;
    }

    private StoredObject storeObject(byte[] content, String originalName, String contentType,
                                     String extension, String category) {
        try {
            return fileStorage.store(content, originalName, contentType, extension, category);
        } catch (Exception exception) {
            log.error("文件对象存储失败", exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "文件存储失败");
        }
    }

    private void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            fileStorage.delete(objectKey);
        } catch (Exception exception) {
            log.warn("删除文件对象失败, objectKey={}", objectKey, exception);
        }
    }

    private String resolveCategory(String category, String extension, String contentType) {
        if (StringUtils.hasText(category) && FileCategoryUtils.isKnown(category)) {
            return category.toLowerCase(Locale.ROOT);
        }
        return FileCategoryUtils.detect(extension, contentType);
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "读取上传文件失败");
        }
    }

    private String resolveContentType(MultipartFile file, String originalName) {
        Optional<String> detected = MediaTypeFactory.getMediaType(originalName)
                .map(mediaType -> mediaType.toString());
        if (detected.isPresent() && !detected.get().equals("application/octet-stream")) {
            return detected.get();
        }
        return StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
    }

    private String normalizeName(String originalName) {
        if (originalName == null) {
            return "file";
        }
        String name = originalName.replace('\\', '/');
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name.isBlank() ? "file" : name;
    }

    private String extensionOf(String originalName) {
        return originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private Set<String> allowedExtensionSet() {
        return Arrays.stream(uploadProperties.getAllowedExtensions().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
