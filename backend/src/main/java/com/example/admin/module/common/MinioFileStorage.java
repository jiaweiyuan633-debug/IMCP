package com.example.admin.module.common;

import cn.hutool.core.util.IdUtil;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.module.system.mapper.SysTenantMapper;
import com.example.admin.security.SecurityUtils;
import com.example.admin.common.FileAccessService;
import com.example.admin.common.TenantContext;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    private final SysFileMapper fileMapper;
    private final SysTenantMapper tenantMapper;
    private final FileAccessService fileAccessService;

    @Value("${app.storage.minio.endpoint}")
    private String endpoint;

    @Value("${app.storage.minio.access-key}")
    private String accessKey;

    @Value("${app.storage.minio.secret-key}")
    private String secretKey;

    @Value("${app.storage.minio.bucket}")
    private String bucket;

    @Override
    public UploadResponse store(MultipartFile file) throws Exception {
        checkStorageQuota(file.getSize());
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String fileName = IdUtil.fastSimpleUUID() + "-" + (file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
        String url = endpoint + "/" + bucket + "/" + fileName;
        SysFileDO sysFile = new SysFileDO();
        sysFile.setTenantId(TenantContext.getTenantId());
        sysFile.setFileName(fileName);
        sysFile.setOriginalName(file.getOriginalFilename());
        sysFile.setUrl(url);
        sysFile.setObjectKey(fileName);
        sysFile.setSize(file.getSize());
        sysFile.setStorageType("minio");
        sysFile.setCreatedBy(tryGetUserId());
        try {
            fileMapper.insert(sysFile);
        } catch (RuntimeException exception) {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileName)
                    .build());
            throw exception;
        }
        return UploadResponse.builder()
                .url(url)
                .name(file.getOriginalFilename())
                .size(file.getSize())
                .accessToken(fileAccessService.issue(url))
                .build();
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (BusinessException exception) {
            return null;
        }
    }

    private void checkStorageQuota(long size) {
        Long tenantId = TenantContext.getTenantId();
        SysTenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStorageLimitMb() == null) {
            return;
        }
        List<SysFileDO> files = fileMapper.selectList(new LambdaQueryWrapper<SysFileDO>()
                .eq(SysFileDO::getTenantId, tenantId));
        long used = files.stream().mapToLong(SysFileDO::getSize).sum();
        long limit = tenant.getStorageLimitMb() * 1024L * 1024L;
        if (used + size > limit) {
            throw new BusinessException(ResultCode.STORAGE_LIMIT_EXCEEDED);
        }
    }
}

