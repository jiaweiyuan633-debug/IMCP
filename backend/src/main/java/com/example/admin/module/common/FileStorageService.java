package com.example.admin.module.common;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.common.vo.UploadResponse;
import com.example.admin.module.system.entity.SysFile;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.security.SecurityUtils;
import com.example.admin.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class FileStorageService implements FileStorage {

    private final SysFileMapper fileMapper;

    @Value("${app.upload-path:uploads}")
    private String uploadPath;

    public UploadResponse store(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
        String fileName = IdUtil.fastSimpleUUID() + "." + extension;
        Path dir = Paths.get(uploadPath, datePath);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        file.transferTo(target.toAbsolutePath().toFile());
        String url = "/uploads/" + datePath + "/" + fileName;

        SysFile sysFile = new SysFile();
        sysFile.setTenantId(TenantContext.getTenantId());
        sysFile.setFileName(fileName);
        sysFile.setOriginalName(originalName);
        sysFile.setUrl(url);
        sysFile.setSize(file.getSize());
        sysFile.setStorageType("local");
        sysFile.setCreatedBy(tryGetUserId());
        fileMapper.insert(sysFile);

        return UploadResponse.builder()
                .url(url)
                .name(originalName)
                .size(file.getSize())
                .build();
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception exception) {
            return null;
        }
    }
}

