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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class FileStorageService implements FileStorage {

    private final SysFileMapper fileMapper;
    private final SysTenantMapper tenantMapper;
    private final FileAccessService fileAccessService;

    @Value("${app.upload-path:uploads}")
    private String uploadPath;

    public UploadResponse store(MultipartFile file) throws IOException {
        checkStorageQuota(file.getSize());
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = IdUtil.fastSimpleUUID() + "." + extension;
        Path dir = Paths.get(uploadPath, datePath);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        file.transferTo(target.toAbsolutePath().toFile());
        String url = "/uploads/" + datePath + "/" + fileName;

        SysFileDO sysFile = new SysFileDO();
        sysFile.setTenantId(TenantContext.getTenantId());
        sysFile.setFileName(fileName);
        sysFile.setOriginalName(originalName);
        sysFile.setUrl(url);
        sysFile.setSize(file.getSize());
        sysFile.setStorageType("local");
        sysFile.setCreatedBy(tryGetUserId());
        try {
            fileMapper.insert(sysFile);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }

        return UploadResponse.builder()
                .url(url)
                .name(originalName)
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

