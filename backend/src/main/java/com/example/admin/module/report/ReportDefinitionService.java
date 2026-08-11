package com.example.admin.module.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.report.dto.ReportDefinitionQuery;
import com.example.admin.module.report.dto.ReportDefinitionSaveRequest;
import com.example.admin.module.report.dto.ReportExecuteRequest;
import com.example.admin.module.report.entity.ReportDefinitionDO;
import com.example.admin.module.report.mapper.ReportDefinitionMapper;
import com.example.admin.module.report.vo.ReportDefinitionVo;
import com.example.admin.module.report.vo.ReportExecuteResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报表定义服务：分页查询、新增、编辑、逻辑删除、详情与只读查询执行。
 * 租户隔离与执行安全统一由 {@link ReportSqlGuard} 负责：保存时做结构性只读校验，
 * 执行时校验 + 注入 tenant_id 条件 + 收紧行数上限，杜绝跨租户读取与 DoS。
 */
@Service
@RequiredArgsConstructor
public class ReportDefinitionService {

    private static final int ENABLED = 1;

    /** dataSource 中的命名占位 :param。 */
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":(\\w+)");

    private final ReportDefinitionMapper reportDefinitionMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ReportSqlGuard reportSqlGuard;

    public PageResult<ReportDefinitionVo> page(ReportDefinitionQuery query) {
        Page<ReportDefinitionDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ReportDefinitionDO> wrapper = new LambdaQueryWrapper<ReportDefinitionDO>()
                .like(StringUtils.hasText(query.getName()), ReportDefinitionDO::getName, query.getName())
                .like(StringUtils.hasText(query.getCode()), ReportDefinitionDO::getCode, query.getCode())
                .like(StringUtils.hasText(query.getCategory()), ReportDefinitionDO::getCategory, query.getCategory())
                .orderByAsc(ReportDefinitionDO::getId);
        IPage<ReportDefinitionDO> result = reportDefinitionMapper.selectPage(page, wrapper);
        List<ReportDefinitionVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public Long create(ReportDefinitionSaveRequest request) {
        checkCodeUnique(request.getCode(), null);
        reportSqlGuard.validate(request.getDataSource());
        ReportDefinitionDO definition = toEntity(request);
        try {
            reportDefinitionMapper.insert(definition);
        } catch (DuplicateKeyException exception) {
            // 并发同码创建：预检通过但唯一键先被他人占用，转精确业务码而非泛化 500
            throw new BusinessException(ResultCode.REPORT_CODE_EXISTS);
        }
        return definition.getId();
    }

    public void update(ReportDefinitionSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "报表 ID 不能为空");
        }
        // 先确认记录存在，使 updateById 返回 0 只可能由乐观锁版本冲突引起
        getById(request.getId());
        checkCodeUnique(request.getCode(), request.getId());
        reportSqlGuard.validate(request.getDataSource());
        // 乐观锁：携带 version 时 MP 自动追加 version 条件并递增，冲突时影响行数为 0
        int rows = reportDefinitionMapper.updateById(toEntity(request));
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "报表已被他人修改，请刷新后重试");
        }
    }

    public void delete(Long id) {
        reportDefinitionMapper.deleteById(id);
    }

    public ReportDefinitionVo detail(Long id) {
        return toVo(getById(id));
    }

    /**
     * 执行报表只读查询：安全守卫校验并重写（租户注入 + 行数上限）→ :param 转 ? 并绑定参数 → queryForList。
     * 返回 {columns, rows}，结果仅作展示，不做任何写入。
     */
    public ReportExecuteResultVo execute(Long id, ReportExecuteRequest request) {
        ReportDefinitionDO definition = getById(id);
        // 停用报表不执行：status=0 应在执行路径生效，与表单对草稿/未发布的拒绝语义一致
        if (definition.getStatus() == null || definition.getStatus() != ENABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "报表已停用，无法执行");
        }
        String sql = definition.getDataSource();
        Map<String, Object> params = request.getParams() == null ? Map.of() : request.getParams();
        // 执行期统一走守卫：只读校验 + 注入 tenant_id（JdbcTemplate 直查绕过 MyBatis 租户拦截器，必须显式注入）
        String guardedSql = reportSqlGuard.guard(sql);
        NamedSql named = convertNamedParams(guardedSql, params);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(named.sql(), named.args());
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        return ReportExecuteResultVo.builder().columns(columns).rows(rows).build();
    }

    private ReportDefinitionDO getById(Long id) {
        ReportDefinitionDO definition = reportDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return definition;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        ReportDefinitionDO exists = reportDefinitionMapper.selectOne(new LambdaQueryWrapper<ReportDefinitionDO>()
                .eq(ReportDefinitionDO::getCode, code.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.REPORT_CODE_EXISTS);
        }
    }

    /** 将 :param 命名占位替换为 ?，缺失参数抛 PARAM_ERROR，按出现顺序收集绑定值。 */
    private NamedSql convertNamedParams(String sql, Map<String, Object> params) {
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        StringBuilder converted = new StringBuilder();
        List<Object> args = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!params.containsKey(name)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "缺少报表参数：" + name);
            }
            matcher.appendReplacement(converted, "?");
            args.add(params.get(name));
        }
        matcher.appendTail(converted);
        return new NamedSql(converted.toString(), args.toArray());
    }

    private ReportDefinitionDO toEntity(ReportDefinitionSaveRequest request) {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(request.getId());
        definition.setName(request.getName());
        definition.setCode(request.getCode().trim());
        definition.setCategory(request.getCategory());
        definition.setDataSource(request.getDataSource());
        definition.setChartType(request.getChartType());
        definition.setParamsJson(request.getParamsJson());
        definition.setRemark(request.getRemark());
        definition.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        definition.setVersion(request.getVersion());
        return definition;
    }

    private ReportDefinitionVo toVo(ReportDefinitionDO definition) {
        return ReportDefinitionVo.builder()
                .id(definition.getId())
                .name(definition.getName())
                .code(definition.getCode())
                .category(definition.getCategory())
                .dataSource(definition.getDataSource())
                .chartType(definition.getChartType())
                .paramsJson(definition.getParamsJson())
                .remark(definition.getRemark())
                .status(definition.getStatus())
                .version(definition.getVersion())
                .createdAt(definition.getCreatedAt())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }

    /** 转换后的 SQL 与按序参数。 */
    private record NamedSql(String sql, Object[] args) {
    }
}
