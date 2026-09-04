package cn.admin.scaffold.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.module.monitor.entity.SysSqlLogDO;
import cn.admin.scaffold.module.monitor.mapper.SysSqlLogMapper;
import cn.admin.scaffold.module.monitor.vo.SqlLogVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorSqlLogService {

    private final SysSqlLogMapper sqlLogMapper;

    public PageResult<SqlLogVo> page(long pageNum, long pageSize, String sqlText) {
        Page<SysSqlLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysSqlLogDO> wrapper = new LambdaQueryWrapper<SysSqlLogDO>()
                .like(StringUtils.hasText(sqlText), SysSqlLogDO::getSqlText, sqlText)
                .orderByDesc(SysSqlLogDO::getId);
        IPage<SysSqlLogDO> result = sqlLogMapper.selectPage(page, wrapper);
        List<SqlLogVo> records = result.getRecords().stream()
                .map(this::toSqlLogVo)
                .toList();
        return PageResult.of(result, records);
    }

    private SqlLogVo toSqlLogVo(SysSqlLogDO log) {
        return SqlLogVo.builder()
                .id(log.getId())
                .sqlText(log.getSqlText())
                .method(log.getMethod())
                .durationMs(log.getDurationMs())
                .success(log.getSuccess())
                .errorMsg(log.getErrorMsg())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

