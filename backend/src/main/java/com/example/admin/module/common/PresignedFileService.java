package com.example.admin.module.common;

import cn.hutool.core.util.IdUtil;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.dto.PresignConfirmRequest;
import com.example.admin.module.common.dto.PresignUploadRequest;
import com.example.admin.module.common.vo.PresignUploadResponse;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 预签名直传/直链（批次2c）。
 *
 * <p>上传：签发 PUT 预签名 URL 与对象键，前端直传对象存储绕过应用服务器；直传完成后
 * {@code confirm} 读回对象做病毒扫描/配额/魔数校验后入库（不重复上传），并删除签发期在
 * Redis 的占位记录——confirm 必须匹配签发对象且租户一致，防止任意 objectKey 登记。
 * 下载：为已入库文件签发短时 GET 直链，供分享/浏览器直链使用。
 */
@Slf4j
@Service
public class PresignedFileService {

    private static final String PENDING_KEY_PREFIX = "file:presign:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private final FileStorage fileStorage;
    private final FileStorageManager fileStorageManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PresignedFileService(FileStorage fileStorage, FileStorageManager fileStorageManager,
                                StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.fileStorage = fileStorage;
        this.fileStorageManager = fileStorageManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public PresignUploadResponse createUpload(PresignUploadRequest request) {
        String extension = extensionOf(request.getFileName());
        String objectKey = objectKey(request.getFileName(), extension);
        String url;
        try {
            url = fileStorage.presignedUpload(objectKey, request.getContentType(), request.getSize());
        } catch (Exception exception) {
            log.error("生成预签名上传地址失败, objectKey={}", objectKey, exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "生成预签名上传地址失败");
        }
        if (url == null) {
            // 存储后端不支持预签名直传，前端回退普通/分片上传
            return PresignUploadResponse.builder()
                    .objectKey(objectKey)
                    .storageType(fileStorage.type())
                    .supported(false)
                    .build();
        }
        PresignPending pending = new PresignPending(
                TenantContext.getTenantId(), SecurityUtils.tryGetUserId());
        redisTemplate.opsForValue().set(pendingKey(objectKey), toJson(pending), PENDING_TTL);
        return PresignUploadResponse.builder()
                .objectKey(objectKey)
                .uploadUrl(url)
                .storageType(fileStorage.type())
                .supported(true)
                .build();
    }

    public UploadResponse confirm(PresignConfirmRequest request) {
        String json = redisTemplate.opsForValue().get(pendingKey(request.getObjectKey()));
        if (json == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "直传任务不存在或已过期");
        }
        PresignPending pending = parse(json);
        if (!TenantContext.getTenantId().equals(pending.tenantId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "直传任务不存在或已过期");
        }
        try {
            return fileStorageManager.registerObject(request.getObjectKey(), request.getFileName(),
                    request.getContentType(), request.getCategory());
        } finally {
            redisTemplate.delete(pendingKey(request.getObjectKey()));
        }
    }

    public String createDownload(Long fileId) {
        SysFileDO file = fileStorageManager.getOwnedOrThrow(fileId);
        String objectKey = fileStorageManager.resolveObjectKey(file);
        try {
            return fileStorage.presignedDownload(objectKey);
        } catch (Exception exception) {
            log.error("生成预签名下载地址失败, fileId={}", fileId, exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "生成预签名下载地址失败");
        }
    }

    /** 与 MinioFileStorage 相同路径规则，保证直传与普通上传同构。 */
    private String objectKey(String fileName, String extension) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String name = IdUtil.fastSimpleUUID() + (extension.isBlank() ? "" : "." + extension);
        return TenantContext.getTenantId() + "/" + datePath + "/" + name;
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String pendingKey(String objectKey) {
        return PENDING_KEY_PREFIX + objectKey;
    }

    private String toJson(PresignPending pending) {
        try {
            return objectMapper.writeValueAsString(pending);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "直传占位序列化失败");
        }
    }

    private PresignPending parse(String json) {
        try {
            return objectMapper.readValue(json, PresignPending.class);
        } catch (JsonProcessingException exception) {
            log.error("直传占位解析失败", exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "直传占位数据异常");
        }
    }

    private record PresignPending(Long tenantId, Long userId) {
    }
}
