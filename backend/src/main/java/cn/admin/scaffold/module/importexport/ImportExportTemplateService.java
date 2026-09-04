package cn.admin.scaffold.module.importexport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.importexport.dto.TemplateQuery;
import cn.admin.scaffold.module.importexport.dto.TemplateSaveRequest;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import cn.admin.scaffold.module.importexport.entity.ImportExportTemplateDO;
import cn.admin.scaffold.module.importexport.handler.ImportExportHandlerRegistry;
import cn.admin.scaffold.module.importexport.mapper.ImportExportJobMapper;
import cn.admin.scaffold.module.importexport.mapper.ImportExportTemplateMapper;
import cn.admin.scaffold.module.importexport.vo.TemplateVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 导入导出模板服务：分页查询、新增、编辑、删除，以及模板列配置（config_json）解析。
 * type 取值 import/export 校验、entityKey 处理器路由校验、列配置 JSON 合法性校验均在此完成。
 */
@Service
@RequiredArgsConstructor
public class ImportExportTemplateService {

    private static final int ENABLED = 1;
    private static final Set<String> SUPPORTED_TYPES = Set.of("import", "export");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ImportExportTemplateMapper templateMapper;
    private final ImportExportHandlerRegistry handlerRegistry;
    private final ImportExportJobMapper jobMapper;

    public PageResult<TemplateVo> page(TemplateQuery query) {
        Page<ImportExportTemplateDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ImportExportTemplateDO> wrapper = new LambdaQueryWrapper<ImportExportTemplateDO>()
                .like(StringUtils.hasText(query.getName()), ImportExportTemplateDO::getName, query.getName())
                .like(StringUtils.hasText(query.getCode()), ImportExportTemplateDO::getCode, query.getCode())
                .eq(StringUtils.hasText(query.getType()), ImportExportTemplateDO::getType, query.getType())
                .orderByAsc(ImportExportTemplateDO::getId);
        IPage<ImportExportTemplateDO> result = templateMapper.selectPage(page, wrapper);
        List<TemplateVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public Long create(TemplateSaveRequest request) {
        checkCodeUnique(request.getCode(), null);
        ImportExportTemplateDO template = toEntity(request);
        try {
            templateMapper.insert(template);
        } catch (DuplicateKeyException exception) {
            // 并发同码创建：预检通过但唯一键先被他人占用，转精确业务码而非泛化 500
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_CODE_EXISTS);
        }
        return template.getId();
    }

    public void update(TemplateSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板 ID 不能为空");
        }
        // 先确认记录存在，使 updateById 返回 0 只可能由乐观锁版本冲突引起
        if (templateMapper.selectById(request.getId()) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        checkCodeUnique(request.getCode(), request.getId());
        // 乐观锁：携带 version 时 MP 自动追加 version 条件并递增，冲突时影响行数为 0
        int rows = templateMapper.updateById(toEntity(request));
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板已被他人修改，请刷新后重试");
        }
    }

    public void delete(Long id) {
        // 删除引用校验：仍有 PENDING/PROCESSING 任务引用该模板时禁止删除，
        // 否则轮询处理器拾取任务后模板缺失只能置 FAILED，数据留痕不完整
        Long pendingCount = jobMapper.selectCount(new LambdaQueryWrapper<ImportExportJobDO>()
                .eq(ImportExportJobDO::getTemplateId, id)
                .in(ImportExportJobDO::getStatus, "PENDING", "PROCESSING"));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "存在 " + pendingCount + " 个待处理任务引用该模板，请等待任务结束后删除");
        }
        templateMapper.deleteById(id);
    }

    /**
     * 按模板编码查询（租户内）；不存在抛 DATA_NOT_FOUND，供任务创建使用。
     */
    public ImportExportTemplateDO getByCodeRequired(String code) {
        ImportExportTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<ImportExportTemplateDO>()
                .eq(ImportExportTemplateDO::getTenantId, TenantContext.getTenantId())
                .eq(ImportExportTemplateDO::getCode, code));
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return template;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        ImportExportTemplateDO exists = templateMapper.selectOne(new LambdaQueryWrapper<ImportExportTemplateDO>()
                .eq(ImportExportTemplateDO::getTenantId, TenantContext.getTenantId())
                .eq(ImportExportTemplateDO::getCode, code.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_CODE_EXISTS);
        }
    }

    private ImportExportTemplateDO toEntity(TemplateSaveRequest request) {
        if (!SUPPORTED_TYPES.contains(request.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板类型仅支持 import/export");
        }
        if (!handlerRegistry.supports(request.getEntityKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的目标实体: " + request.getEntityKey());
        }
        if (parseColumns(request.getConfigJson()).isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板列配置不能为空");
        }
        ImportExportTemplateDO template = new ImportExportTemplateDO();
        template.setId(request.getId());
        template.setTenantId(TenantContext.getTenantId());
        template.setName(request.getName());
        template.setCode(request.getCode().trim());
        template.setType(request.getType());
        template.setEntityKey(request.getEntityKey());
        template.setConfigJson(request.getConfigJson());
        template.setRemark(request.getRemark());
        template.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        template.setVersion(request.getVersion());
        return template;
    }

    private TemplateVo toVo(ImportExportTemplateDO template) {
        return TemplateVo.builder()
                .id(template.getId())
                .name(template.getName())
                .code(template.getCode())
                .type(template.getType())
                .entityKey(template.getEntityKey())
                .configJson(template.getConfigJson())
                .remark(template.getRemark())
                .status(template.getStatus())
                .version(template.getVersion())
                .createdAt(template.getCreatedAt())
                .build();
    }

    /** 模板列配置：{columns:[{key,header,required,dataType}],sheetName}。 */
    public record ColumnConfig(String key, String header, boolean required, String dataType) {
    }

    /**
     * 解析模板列配置 JSON，返回有序列定义。key/header 缺失或非合法 JSON 抛 PARAM_ERROR；
     * 配置为空时返回空列表，由调用方决定是否拒绝。
     */
    public static List<ColumnConfig> parseColumns(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(configJson);
            JsonNode columnsNode = root.path("columns");
            if (!columnsNode.isArray()) {
                return List.of();
            }
            List<ColumnConfig> columns = new ArrayList<>();
            for (JsonNode node : columnsNode) {
                String key = node.path("key").asText(null);
                String header = node.path("header").asText(null);
                if (!StringUtils.hasText(key) || !StringUtils.hasText(header)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板列配置缺少 key 或 header");
                }
                columns.add(new ColumnConfig(
                        key,
                        header,
                        node.path("required").asBoolean(false),
                        node.path("dataType").asText("string")));
            }
            return columns;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板列配置不是合法 JSON");
        }
    }
}
