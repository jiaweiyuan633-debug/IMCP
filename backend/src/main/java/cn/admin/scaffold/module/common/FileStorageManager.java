package cn.admin.scaffold.module.common;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import cn.admin.scaffold.module.system.mapper.SysFileMapper;
import cn.admin.scaffold.security.SecurityUtils;
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

    private final FileStorage fileStorage;
    private final SysFileMapper fileMapper;
    private final StorageQuotaService storageQuotaService;
    private final FileVirusScanner fileVirusScanner;
    private final FileUploadProperties uploadProperties;

    public UploadResponse store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        String originalName = normalizeName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        byte[] content = readContent(file);
        String contentType = resolveContentType(file, originalName);
        String resolvedCategory = resolveCategory(category, extension, contentType);
        return storeBytes(content, originalName, contentType, resolvedCategory, extension);
    }

    /**
     * 完整上传管线（普通上传/分片合并/预签名确认共用）：大小、扩展名、内容魔数校验 -> 病毒扫描 ->
     * 配额 -> 对象存储（或登记已直传对象）-> 元数据入库。配额检查先于对象存储，避免超额仍占用存储。
     */
    public UploadResponse storeBytes(byte[] content, String originalName, String contentType, String category,
                                     String extension) {
        Validation validation = validate(content, originalName, extension);
        if (!validation.scanResult().clean()) {
            throw new BusinessException(ResultCode.FILE_SCAN_BLOCKED.getCode(), validation.scanResult().message());
        }
        storageQuotaService.check(content.length);
        StoredObject stored = storeObject(content, originalName, contentType, extension, category);
        return persist(content, originalName, contentType, category, extension, stored, validation);
    }

    /**
     * 登记预签名直传的对象：对象已由前端直传对象存储，这里读回做校验/扫描后入库，不重复上传。
     * 扫描不通过时顺带删除违规对象，避免中毒文件滞留存储。
     */
    public UploadResponse registerObject(String objectKey, String originalName, String contentType, String category) {
        byte[] content = readBackBounded(objectKey);
        String extension = extensionOf(originalName);
        String resolvedCategory = resolveCategory(category, extension, contentType);
        Validation validation = validate(content, originalName, extension);
        if (!validation.scanResult().clean()) {
            deleteQuietly(objectKey);
            throw new BusinessException(ResultCode.FILE_SCAN_BLOCKED.getCode(), validation.scanResult().message());
        }
        storageQuotaService.check(content.length);
        StoredObject stored = new StoredObject(objectKey, fileStorage.type(), null);
        return persist(content, originalName, contentType, resolvedCategory, extension, stored, validation);
    }

    /** 入库 + 组装响应；入库失败时回滚已写入的对象，避免孤儿对象残留。 */
    private UploadResponse persist(byte[] content, String originalName, String contentType, String category,
                                   String extension, StoredObject stored, Validation validation) {
        SysFileDO entity = buildEntity(stored, originalName, content, contentType, category,
                validation.sha256(), validation.scanResult());
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
                .category(category)
                .sha256(validation.sha256())
                .scanStatus(entity.getScanStatus())
                .contentUrl(contentUrl)
                // R4-1.43：上传响应不再签发 accessToken——令牌生命周期收敛为「需要时经
                // /api/common/file-token 现取」，列表/上传缓存的令牌 TTL 过期后必 403
                .build();
    }

    /** 大小/扩展名/内容魔数/病毒校验，通过后返回摘要与扫描结果。 */
    private Validation validate(byte[] content, String originalName, String extension) {
        long maxSize = uploadProperties.getMaxSizeBytes();
        if (content.length > maxSize) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "文件大小不能超过 " + uploadProperties.getMaxSizeMb() + "MB");
        }
        if (!allowedExtensionSet().contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的文件类型");
        }
        if (!FileMagicValidator.isAllowedContent(content, extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件内容与扩展名不匹配");
        }
        return new Validation(DigestUtil.sha256Hex(content),
                fileVirusScanner.scan(content, originalName, extension));
    }

    private record Validation(String sha256, FileVirusScanner.ScanResult scanResult) {
    }

    public SysFileDO getOwnedOrThrow(Long id) {
        SysFileDO file = fileMapper.selectById(id);
        if (file == null || !TenantContext.getTenantId().equals(file.getTenantId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return file;
    }

    /**
     * 按历史 {@code /uploads/{objectKey}} URL 校验文件归属并返回（供 file-token 签发前的跨租户校验）。
     * 存量文件 sys_file.url 即 {@code /uploads/{objectKey}}（与 {@link #resolveObjectKey} 的
     * 回退解析同源），精确匹配后校验租户，与 {@link #getOwnedOrThrow} 对 /files/{id} 的校验对齐——
     * 否则任何人登录即可为任意 objectKey 签发令牌，猜中对象键即可跨租户读历史文件（R4-1.43）。
     */
    public SysFileDO getOwnedByLegacyUrlOrThrow(String legacyUploadUrl) {
        SysFileDO file = fileMapper.selectOne(new LambdaQueryWrapper<SysFileDO>()
                .eq(SysFileDO::getUrl, legacyUploadUrl)
                .last("LIMIT 1"));
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

    /**
     * 有界读回预签名直传对象：最多读入 maxSize+1 字节即截断，超限直接判为过大并清理对象。
     * 整读（readAllBytes）会把绕过 multipart 20MB 上限、直传对象存储的超大文件一次性装入堆，
     * 构成 OOM 面；有界读使内存占用恒不大于 maxSize+1，且超限对象不再滞留存储（R4-1.15）。
     */
    private byte[] readBackBounded(String objectKey) {
        long maxSize = uploadProperties.getMaxSizeBytes();
        byte[] content;
        try (InputStream inputStream = fileStorage.open(objectKey)) {
            content = inputStream.readNBytes((int) (maxSize + 1));
        } catch (Exception exception) {
            log.error("读取预签名直传对象失败, objectKey={}", objectKey, exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "直传对象读取失败");
        }
        if (content.length > maxSize) {
            deleteQuietly(objectKey);
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "文件大小不能超过 " + uploadProperties.getMaxSizeMb() + "MB");
        }
        return content;
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
