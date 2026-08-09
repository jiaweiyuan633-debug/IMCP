package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.common.FileAccessService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class SystemFileService {

    private final SysFileMapper fileMapper;
    private final FileAccessService fileAccessService;

    @Value("${app.upload-path:uploads}")
    private String uploadPath;

    @Value("${app.storage.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${app.storage.minio.access-key:minioadmin}")
    private String minioAccessKey;

    @Value("${app.storage.minio.secret-key:minioadmin}")
    private String minioSecretKey;

    @Value("${app.storage.minio.bucket:admin}")
    private String minioBucket;

    public PageResult<SysFileDO> page(long pageNum, long pageSize, String fileName, String originalName, String storageType) {
        Page<SysFileDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysFileDO> wrapper = new LambdaQueryWrapper<SysFileDO>()
                .eq(SysFileDO::getTenantId, TenantContext.getTenantId())
                .like(StringUtils.hasText(fileName), SysFileDO::getFileName, fileName)
                .like(StringUtils.hasText(originalName), SysFileDO::getOriginalName, originalName)
                .eq(StringUtils.hasText(storageType), SysFileDO::getStorageType, storageType)
                .orderByDesc(SysFileDO::getId);
        IPage<SysFileDO> result = fileMapper.selectPage(page, wrapper);
        result.getRecords().forEach(file -> file.setAccessToken(fileAccessService.issue(file.getUrl())));
        return PageResult.of(result, result.getRecords());
    }

    public void delete(Long id) {
        SysFileDO file = fileMapper.selectById(id);
        if (file == null || !TenantContext.getTenantId().equals(file.getTenantId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        deleteObject(file);
        fileMapper.deleteById(id);
    }

    private void deleteObject(SysFileDO file) {
        try {
            if ("minio".equalsIgnoreCase(file.getStorageType())) {
                String object = file.getObjectKey();
                if (object == null || object.isBlank()) {
                    object = file.getUrl().substring(file.getUrl().lastIndexOf('/') + 1);
                }
                MinioClient client = MinioClient.builder()
                        .endpoint(minioEndpoint)
                        .credentials(minioAccessKey, minioSecretKey)
                        .build();
                client.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(object)
                        .build());
                return;
            }
            if (file.getUrl() != null && file.getUrl().startsWith("/uploads/")) {
                String relative = file.getUrl().substring("/uploads/".length());
                Path target = Paths.get(uploadPath, relative).normalize().toAbsolutePath();
                if (target.startsWith(Paths.get(uploadPath).toAbsolutePath().normalize())) {
                    Files.deleteIfExists(target);
                }
            }
        } catch (IOException | MinioException | InvalidKeyException | NoSuchAlgorithmException
                | RuntimeException exception) {
            // 文件对象删除失败时不阻断元数据删除，避免孤儿记录无法清理
        }
    }
}
