package cn.admin.scaffold.module.form;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.annotation.DataScope;
import cn.admin.scaffold.module.form.dto.FormInstanceQuery;
import cn.admin.scaffold.module.form.dto.FormInstanceSubmitRequest;
import cn.admin.scaffold.module.form.entity.FormDefinitionDO;
import cn.admin.scaffold.module.form.entity.FormInstanceDO;
import cn.admin.scaffold.module.form.mapper.FormDefinitionMapper;
import cn.admin.scaffold.module.form.mapper.FormInstanceMapper;
import cn.admin.scaffold.module.form.vo.FormField;
import cn.admin.scaffold.module.form.vo.FormInstanceVo;
import cn.admin.scaffold.module.system.DataScopeHelper;
import cn.admin.scaffold.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表单提交服务：提交（查已发布定义 → 校验数据 → 落库）、分页、详情、审批流转。
 * 租户隔离由 TenantLineInnerInterceptor 自动注入，这里不手动操作 TenantContext。
 */
@Service
@RequiredArgsConstructor
public class FormInstanceService {

    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final int STATUS_PUBLISHED = 1;

    private final FormInstanceMapper formInstanceMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final FormSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final DataScopeHelper dataScopeHelper;

    public Long submit(FormInstanceSubmitRequest request) {
        FormDefinitionDO definition = formDefinitionMapper.selectOne(new LambdaQueryWrapper<FormDefinitionDO>()
                .eq(FormDefinitionDO::getCode, request.getFormCode().trim()));
        if (definition == null || definition.getStatus() == null || definition.getStatus() != STATUS_PUBLISHED) {
            // 未发布表单不可提交，与「不存在」同语义：统一 DATA_NOT_FOUND，便于前端按错误码映射文案
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        List<FormField> fields = schemaValidator.validateSchema(definition.getSchemaJson());
        schemaValidator.validateData(fields, request.getData());

        FormInstanceDO instance = new FormInstanceDO();
        instance.setFormId(definition.getId());
        instance.setFormCode(definition.getCode());
        instance.setDataJson(writeJson(request.getData()));
        instance.setStatus(STATUS_SUBMITTED);
        instance.setSubmitterId(SecurityUtils.tryGetUserId());
        instance.setSubmittedAt(LocalDateTime.now());
        formInstanceMapper.insert(instance);
        return instance.getId();
    }

    /**
     * 提交记录分页（R4-1.37 行级数据权限）：非管理员仅可见自己提交的记录（submitter_id 命中
     * 当前用户可见集合），管理员经 DataScopeAspect.isAdmin 短路不受限；受控表映射已在
     * V61 迁移中注册到 sys_data_permission，后续按权限矩阵调整无需发版。
     */
    @DataScope(tables = {"form_instance"})
    public PageResult<FormInstanceVo> page(FormInstanceQuery query) {
        Page<FormInstanceDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<FormInstanceDO> wrapper = new LambdaQueryWrapper<FormInstanceDO>()
                .eq(StringUtils.hasText(query.getFormCode()), FormInstanceDO::getFormCode, query.getFormCode())
                .orderByDesc(FormInstanceDO::getId);
        IPage<FormInstanceDO> result = formInstanceMapper.selectPage(page, wrapper);
        List<FormInstanceVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    /**
     * 提交记录详情（R4-1.38 数据权限单条路径补漏）：page 已按 submitter_id 行级过滤，但 getById
     * 原无归属校验，非管理员可遍历/猜测 id 读取任意人的表单提交内容。此处补齐同一可见性，
     * 保证"列表可见 = 单条可读"。
     */
    public FormInstanceVo getById(Long id) {
        FormInstanceDO instance = formInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "提交记录不存在");
        }
        checkDataScope(instance);
        return toVo(instance);
    }

    /**
     * 审批流转：SUBMITTED → APPROVED/REJECTED。
     * R4-1.38 先做归属校验（非管理员仅可流转自己可见的提交），再 CAS 条件更新保证原子性：
     * 并发审批时仅一个请求能把 SUBMITTED 流转成功，其余请求命中 0 行被拒绝，避免"先查后改"
     * 窗口内重复/覆盖审批。
     */
    public void approve(Long id, String status) {
        if (!STATUS_APPROVED.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "非法审批状态，仅支持 APPROVED/REJECTED");
        }
        FormInstanceDO instance = formInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "仅待审批状态的记录可流转，该记录不存在或已被处理");
        }
        checkDataScope(instance);
        int rows = formInstanceMapper.casStatus(id, status);
        if (rows == 0) {
            // 0 行 = 记录不存在 或 已被并发流转（SUBMITTED → 其它状态）；统一拒绝
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "仅待审批状态的记录可流转，该记录不存在或已被处理");
        }
    }

    /**
     * 单条归属校验（R4-1.38）：非管理员要求 submitter_id 命中当前用户可见集合（与 page 的
     * {@code @DataScope} 同一语义，admin 短路），越权抛 FORBIDDEN。
     */
    private void checkDataScope(FormInstanceDO instance) {
        if (dataScopeHelper.isAdmin()) {
            return;
        }
        List<Long> allowedUserIds = dataScopeHelper.allowedUserIds();
        if (allowedUserIds == null) {
            return;
        }
        if (instance.getSubmitterId() == null || !allowedUserIds.contains(instance.getSubmitterId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "提交数据格式非法");
        }
    }

    private FormInstanceVo toVo(FormInstanceDO instance) {
        return FormInstanceVo.builder()
                .id(instance.getId())
                .formId(instance.getFormId())
                .formCode(instance.getFormCode())
                .data(readJson(instance.getDataJson()))
                .status(instance.getStatus())
                .submitterId(instance.getSubmitterId())
                .submittedAt(instance.getSubmittedAt())
                .remark(instance.getRemark())
                .createdAt(instance.getCreatedAt())
                .build();
    }

    private Map<String, Object> readJson(String dataJson) {
        if (!StringUtils.hasText(dataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }
}
