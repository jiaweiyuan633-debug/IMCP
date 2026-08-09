package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.monitor.entity.SysSqlLogDO;
import com.example.admin.module.monitor.mapper.SysSqlLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MonitorSqlLogService {

    private final SysSqlLogMapper sqlLogMapper;

    public PageResult<SysSqlLogDO> page(long pageNum, long pageSize, String sqlText) {
        Page<SysSqlLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysSqlLogDO> wrapper = new LambdaQueryWrapper<SysSqlLogDO>()
                .like(StringUtils.hasText(sqlText), SysSqlLogDO::getSqlText, sqlText)
                .orderByDesc(SysSqlLogDO::getId);
        IPage<SysSqlLogDO> result = sqlLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }
}

