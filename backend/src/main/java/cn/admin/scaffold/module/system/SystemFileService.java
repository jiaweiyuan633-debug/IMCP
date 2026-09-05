package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.common.FileStorageManager;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import cn.admin.scaffold.module.system.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SystemFileService {

    private final SysFileMapper fileMapper;
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
        // 列表不再签发 accessToken——令牌统一由 /api/common/file-token 现取，列表缓存的
        // 令牌 TTL(1h) 后失效，页面停留超过 1h 后点击文件名链接必然 403
        result.getRecords().forEach(file -> file.setContentUrl("/files/" + file.getId()));
        return PageResult.of(result, result.getRecords());
    }

    public void delete(Long id) {
        SysFileDO file = fileStorageManager.getOwnedOrThrow(id);
        fileStorageManager.delete(file);
        fileMapper.deleteById(id);
    }
}
