package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.FileStorageManager;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.security.SecurityUtils;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.common.FileAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SystemFileService {

    private final SysFileMapper fileMapper;
    private final FileAccessService fileAccessService;
    private final FileStorageManager fileStorageManager;

    public PageResult<SysFileDO> page(long pageNum, long pageSize, String fileName, String originalName,
                                      String category, String storageType) {
        Page<SysFileDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysFileDO> wrapper = new LambdaQueryWrapper<SysFileDO>()
                .eq(SysFileDO::getTenantId, TenantContext.getTenantId())
                .like(StringUtils.hasText(fileName), SysFileDO::getFileName, fileName)
                .like(StringUtils.hasText(originalName), SysFileDO::getOriginalName, originalName)
                .eq(StringUtils.hasText(category), SysFileDO::getCategory, category)
                .eq(StringUtils.hasText(storageType), SysFileDO::getStorageType, storageType)
                .orderByDesc(SysFileDO::getId);
        IPage<SysFileDO> result = fileMapper.selectPage(page, wrapper);
        result.getRecords().forEach(file -> {
            String contentUrl = "/files/" + file.getId();
            file.setContentUrl(contentUrl);
            file.setAccessToken(fileAccessService.issue(contentUrl, SecurityUtils.tryGetUserId()));
        });
        return PageResult.of(result, result.getRecords());
    }

    public void delete(Long id) {
        SysFileDO file = fileStorageManager.getOwnedOrThrow(id);
        fileStorageManager.delete(file);
        fileMapper.deleteById(id);
    }
}
