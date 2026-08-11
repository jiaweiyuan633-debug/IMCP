package com.example.admin.module.common;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.DistributedLock;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.dto.ChunkInitRequest;
import com.example.admin.module.common.vo.ChunkInitResponse;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 分片上传 + 秒传（批次2c）。
 *
 * <p>流程：init（建任务，sha256 命中已存文件则秒传直接返回）-> 逐片 uploadChunk（分片落临时目录，
 * Redis SET 记录已收片，重复上传幂等）-> complete（分布式锁内合并分片、校验 sha256 与总大小，
 * 走 FileStorageManager 完整管线入库，清理临时分片与任务）。
 *
 * <p>临时分片存本地磁盘（app.upload.chunk-dir，默认系统临时目录），任务元数据存 Redis（TTL 2h）。
 */
@Slf4j
@Service
public class ChunkFileService {

    private static final String REDIS_KEY_PREFIX = "file:chunk:";
    private static final Duration TASK_TTL = Duration.ofHours(2);

    private final SysFileMapper fileMapper;
    private final StringRedisTemplate redisTemplate;
    private final FileStorageManager fileStorageManager;
    private final DistributedLock distributedLock;
    private final ObjectMapper objectMapper;

    @Value("${app.upload.chunk-dir:}")
    private String chunkDirConfig;

    public ChunkFileService(SysFileMapper fileMapper, StringRedisTemplate redisTemplate,
                            FileStorageManager fileStorageManager, DistributedLock distributedLock,
                            ObjectMapper objectMapper) {
        this.fileMapper = fileMapper;
        this.redisTemplate = redisTemplate;
        this.fileStorageManager = fileStorageManager;
        this.distributedLock = distributedLock;
        this.objectMapper = objectMapper;
    }

    public ChunkInitResponse init(ChunkInitRequest request) {
        if (StringUtils.hasText(request.getSha256())) {
            SysFileDO existing = fileMapper.selectOne(new LambdaQueryWrapper<SysFileDO>()
                    .eq(SysFileDO::getTenantId, TenantContext.getTenantId())
                    .eq(SysFileDO::getSha256, request.getSha256())
                    .last("LIMIT 1"));
            if (existing != null) {
                // 秒传命中：同租户已存在完全相同的文件，直接复用
                return ChunkInitResponse.builder()
                        .exists(true)
                        .fileId(existing.getId())
                        .url("/files/" + existing.getId())
                        .build();
            }
        }
        String uploadId = IdUtil.fastSimpleUUID();
        ChunkTask task = new ChunkTask(uploadId, TenantContext.getTenantId(), request.getFileName(),
                request.getContentType(), request.getCategory(), request.getTotalChunks(),
                request.getChunkSize(), request.getTotalSize(), request.getSha256(),
                extensionOf(request.getFileName()));
        redisTemplate.opsForValue().set(key(uploadId), toJson(task), TASK_TTL);
        return ChunkInitResponse.builder()
                .uploadId(uploadId)
                .exists(false)
                .chunkSize(request.getChunkSize())
                .build();
    }

    public void uploadChunk(String uploadId, int index, MultipartFile chunk) {
        ChunkTask task = getTask(uploadId);
        if (index < 0 || index >= task.totalChunks()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分片序号非法");
        }
        byte[] bytes = readChunk(chunk);
        if (bytes.length > task.chunkSize()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分片大小超过约定值");
        }
        // 幂等：该片已收到则直接返回（断点续传场景前端可能重复提交）
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(partsKey(uploadId), String.valueOf(index)))) {
            return;
        }
        File partFile = chunkFile(uploadId, index);
        partFile.getParentFile().mkdirs();
        try {
            Files.write(partFile.toPath(), bytes);
        } catch (IOException exception) {
            log.error("写入分片失败, uploadId={}, index={}", uploadId, index, exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "分片写入失败");
        }
        redisTemplate.opsForSet().add(partsKey(uploadId), String.valueOf(index));
        redisTemplate.expire(partsKey(uploadId), TASK_TTL);
    }

    public UploadResponse complete(String uploadId) {
        // 合并与清理是临界区：避免与并发上传的最后一篇分片竞争读到不完整数据
        return distributedLock.execute("file-chunk-complete:" + uploadId, () -> doComplete(uploadId));
    }

    private UploadResponse doComplete(String uploadId) {
        ChunkTask task = getTask(uploadId);
        Long received = redisTemplate.opsForSet().size(partsKey(uploadId));
        if (received == null || received.intValue() != task.totalChunks()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分片未上传完整");
        }
        byte[] content = mergeChunks(task, uploadId);
        if (content.length != task.totalSize()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "合并文件大小与声明不一致");
        }
        if (StringUtils.hasText(task.sha256())
                && !DigestUtil.sha256Hex(content).equalsIgnoreCase(task.sha256())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件内容校验失败");
        }
        UploadResponse response = fileStorageManager.storeBytes(
                content, task.fileName(), task.contentType(), task.category(), task.extension());
        cleanup(uploadId);
        return response;
    }

    private byte[] mergeChunks(ChunkTask task, String uploadId) {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) task.totalSize());
        for (int index = 0; index < task.totalChunks(); index++) {
            Path path = chunkFile(uploadId, index).toPath();
            if (!Files.exists(path)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分片缺失: " + index);
            }
            try {
                output.write(Files.readAllBytes(path));
            } catch (IOException exception) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "合并分片失败");
            }
        }
        return output.toByteArray();
    }

    private byte[] readChunk(MultipartFile chunk) {
        try {
            return chunk.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "读取分片失败");
        }
    }

    private ChunkTask getTask(String uploadId) {
        String json = redisTemplate.opsForValue().get(key(uploadId));
        if (json == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "分片上传任务不存在或已过期");
        }
        try {
            return objectMapper.readValue(json, ChunkTask.class);
        } catch (JsonProcessingException exception) {
            log.error("分片任务元数据解析失败, uploadId={}", uploadId, exception);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "任务元数据异常");
        }
    }

    private void cleanup(String uploadId) {
        FileUtil.del(chunkDir(uploadId));
        redisTemplate.delete(List.of(key(uploadId), partsKey(uploadId)));
    }

    private String key(String uploadId) {
        return REDIS_KEY_PREFIX + uploadId;
    }

    private String partsKey(String uploadId) {
        return REDIS_KEY_PREFIX + uploadId + ":parts";
    }

    private File chunkDir(String uploadId) {
        return new File(baseChunkDir(), uploadId);
    }

    private File chunkFile(String uploadId, int index) {
        return new File(chunkDir(uploadId), String.valueOf(index));
    }

    private File baseChunkDir() {
        String dir = chunkDirConfig;
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("java.io.tmpdir") + File.separator + "file-chunk";
        }
        return new File(dir);
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String toJson(ChunkTask task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "任务元数据序列化失败");
        }
    }

    /** 上传任务元数据（Redis JSON 存储）。 */
    public record ChunkTask(String uploadId, Long tenantId, String fileName, String contentType, String category,
                            int totalChunks, int chunkSize, long totalSize, String sha256, String extension) {
    }
}
