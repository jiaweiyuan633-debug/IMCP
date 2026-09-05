package cn.admin.scaffold.module.form;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.UniqueKeyRelease;
import cn.admin.scaffold.module.form.dto.FormDefinitionQuery;
import cn.admin.scaffold.module.form.dto.FormDefinitionSaveRequest;
import cn.admin.scaffold.module.form.entity.FormDefinitionDO;
import cn.admin.scaffold.module.form.mapper.FormDefinitionMapper;
import cn.admin.scaffold.module.form.vo.FormDefinitionVo;
import cn.admin.scaffold.module.form.vo.FormField;
import cn.admin.scaffold.module.form.vo.FormSchemaVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 表单定义服务：分页查询、详情、渲染结构、新增、编辑、发布、删除。
 * 租户隔离由 TenantLineInnerInterceptor 自动注入，这里不手动操作 TenantContext。
 */
@Service
@RequiredArgsConstructor
public class FormDefinitionService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;

    private final FormDefinitionMapper formDefinitionMapper;
    private final FormSchemaValidator schemaValidator;

    public PageResult<FormDefinitionVo> page(FormDefinitionQuery query) {
        Page<FormDefinitionDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<FormDefinitionDO> wrapper = new LambdaQueryWrapper<FormDefinitionDO>()
                .like(StringUtils.hasText(query.getName()), FormDefinitionDO::getName, query.getName())
                .like(StringUtils.hasText(query.getCode()), FormDefinitionDO::getCode, query.getCode())
                .orderByDesc(FormDefinitionDO::getId);
        IPage<FormDefinitionDO> result = formDefinitionMapper.selectPage(page, wrapper);
        List<FormDefinitionVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public FormDefinitionVo getById(Long id) {
        return toVo(requireById(id));
    }

    /**
     * 获取已发布表单的渲染结构（fields + layout）；草稿或不存在返回数据不存在。
     */
    public FormSchemaVo getSchema(Long id) {
        FormDefinitionDO definition = requireById(id);
        if (definition.getStatus() == null || definition.getStatus() != STATUS_PUBLISHED) {
            // 草稿不可渲染，与「不存在」同语义：统一 DATA_NOT_FOUND，便于前端按错误码映射文案
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        List<FormField> fields = schemaValidator.validateSchema(definition.getSchemaJson());
        return FormSchemaVo.builder()
                .fields(fields)
                .layout(definition.getLayoutJson())
                .build();
    }

    public Long create(FormDefinitionSaveRequest request) {
        checkCodeUnique(request.getCode(), null);
        schemaValidator.validateSchema(request.getSchemaJson());
        FormDefinitionDO definition = toEntity(request);
        definition.setStatus(STATUS_DRAFT);
        try {
            formDefinitionMapper.insert(definition);
        } catch (DuplicateKeyException exception) {
            // 并发同码创建：预检通过但唯一键先被他人占用，转精确业务码而非泛化 500
            throw new BusinessException(ResultCode.FORM_CODE_EXISTS);
        }
        return definition.getId();
    }

    public void update(FormDefinitionSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "表单 ID 不能为空");
        }
        checkCodeUnique(request.getCode(), request.getId());
        schemaValidator.validateSchema(request.getSchemaJson());
        FormDefinitionDO definition = toEntity(request);
        // 乐观锁：携带 version 时 MyBatis-Plus 自动追加 version 条件并递增；冲突时影响行数为 0
        int rows = formDefinitionMapper.updateById(definition);
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "表单已被他人修改，请刷新后重试");
        }
    }

    /**
     * 发布表单：草稿 → 已发布。发布前再次校验 schema，version 由乐观锁递增；
     * 并发发布时后提交者携带过期 version 命中 0 行，拒绝避免重复发布。
     */
    public void publish(Long id) {
        FormDefinitionDO definition = requireById(id);
        schemaValidator.validateSchema(definition.getSchemaJson());
        definition.setStatus(STATUS_PUBLISHED);
        int rows = formDefinitionMapper.updateById(definition);
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "表单已被他人修改，请刷新后重试");
        }
    }

    public void delete(Long id) {
        // 逻辑删除前先释放 code 唯一键（(tenant_id, code)）：删除后同名编码可立即重建。
        // 已提交的 form_instance 自带 form_code 快照，不受定义 code 改名影响。
        FormDefinitionDO definition = requireById(id);
        definition.setCode(UniqueKeyRelease.releaseCode(definition.getCode()));
        formDefinitionMapper.updateById(definition);
        formDefinitionMapper.deleteById(id);
    }

    private FormDefinitionDO requireById(Long id) {
        FormDefinitionDO definition = formDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return definition;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        FormDefinitionDO exists = formDefinitionMapper.selectOne(new LambdaQueryWrapper<FormDefinitionDO>()
                .eq(FormDefinitionDO::getCode, code.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.FORM_CODE_EXISTS);
        }
    }

    private FormDefinitionDO toEntity(FormDefinitionSaveRequest request) {
        FormDefinitionDO definition = new FormDefinitionDO();
        definition.setId(request.getId());
        definition.setName(request.getName());
        definition.setCode(request.getCode().trim());
        definition.setDescription(request.getDescription());
        definition.setSchemaJson(request.getSchemaJson());
        definition.setLayoutJson(request.getLayoutJson());
        definition.setVersion(request.getVersion());
        return definition;
    }

    private FormDefinitionVo toVo(FormDefinitionDO definition) {
        return FormDefinitionVo.builder()
                .id(definition.getId())
                .name(definition.getName())
                .code(definition.getCode())
                .description(definition.getDescription())
                .status(definition.getStatus())
                .version(definition.getVersion())
                .schemaJson(definition.getSchemaJson())
                .layoutJson(definition.getLayoutJson())
                .createdAt(definition.getCreatedAt())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }
}
