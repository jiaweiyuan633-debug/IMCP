package cn.admin.scaffold.module.importexport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.FileAccessService;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.annotation.DataScope;
import cn.admin.scaffold.module.common.FileStorageManager;
import cn.admin.scaffold.module.importexport.dto.JobCreateRequest;
import cn.admin.scaffold.module.importexport.dto.JobQuery;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import cn.admin.scaffold.module.importexport.entity.ImportExportTemplateDO;
import cn.admin.scaffold.module.importexport.mapper.ImportExportJobMapper;
import cn.admin.scaffold.module.importexport.vo.DownloadVo;
import cn.admin.scaffold.module.importexport.vo.JobVo;
import cn.admin.scaffold.module.system.DataScopeHelper;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import cn.admin.scaffold.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 导入导出任务服务：建任务（导入/导出）进入 PENDING，由 ImportExportJobProcessor 轮询执行；
 * 提供任务分页、详情与导出结果下载。
 */
@Service
@RequiredArgsConstructor
public class ImportExportJobService {

    private static final String TYPE_IMPORT = "import";
    private static final String TYPE_EXPORT = "export";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ImportExportJobMapper jobMapper;
    private final ImportExportTemplateService templateService;
    private final FileStorageManager fileStorageManager;
    private final FileAccessService fileAccessService;
    private final DataScopeHelper dataScopeHelper;

    /**
     * 任务记录分页（行级数据权限）：非管理员仅可见自己创建的任务（created_by 命中
     * 当前用户可见集合），管理员经 DataScopeAspect.isAdmin 短路不受限；受控表映射已在
     * V61 迁移中注册到 sys_data_permission，后续按权限矩阵调整无需发版。
     */
    @DataScope(tables = {"import_export_job"})
    public PageResult<JobVo> page(JobQuery query) {
        Page<ImportExportJobDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ImportExportJobDO> wrapper = new LambdaQueryWrapper<ImportExportJobDO>()
                .like(StringUtils.hasText(query.getTemplateCode()),
                        ImportExportJobDO::getTemplateCode, query.getTemplateCode())
                .eq(StringUtils.hasText(query.getType()), ImportExportJobDO::getType, query.getType())
                .eq(StringUtils.hasText(query.getStatus()), ImportExportJobDO::getStatus, query.getStatus())
                .orderByDesc(ImportExportJobDO::getId);
        IPage<ImportExportJobDO> result = jobMapper.selectPage(page, wrapper);
        List<JobVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    /**
     * 创建导入任务：校验模板存在且 type=import、导入文件必填，落 PENDING 任务并返回 id。
     */
    public Long createImport(JobCreateRequest request) {
        ImportExportTemplateDO template = templateService.getByCodeRequired(request.getTemplateCode());
        if (!TYPE_IMPORT.equals(template.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板不是导入模板");
        }
        if (request.getFileId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导入文件不能为空");
        }
        return createJob(template, TYPE_IMPORT, request.getFileId(), request.getFileName(), null);
    }

    /**
     * 创建导出任务：校验模板存在且 type=export，筛选参数序列化落库（query_json），
     * 处理器取任务时反序列化透传到 handler.export，保证按用户筛选范围而非全量导出。
     */
    public Long createExport(JobCreateRequest request) {
        ImportExportTemplateDO template = templateService.getByCodeRequired(request.getTemplateCode());
        if (!TYPE_EXPORT.equals(template.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板不是导出模板");
        }
        return createJob(template, TYPE_EXPORT, null, null, serializeQuery(request.getQuery()));
    }

    /**
     * 任务详情（数据权限单条路径补漏）：page 已按 created_by 行级过滤，但 view/download
     * 原只校验租户，非管理员可遍历/猜测 id 读取他人任务详情甚至下载导出成果。统一收口到
     * {@link #getOwnedOrThrow} 的归属校验，保证"列表可见 = 单条可读/可下载"。
     */
    public JobVo view(Long id) {
        return toVo(getOwnedOrThrow(id));
    }

    /**
     * 导出结果下载：任务须为 type=export 且 SUCCEEDED，返回带 Token 的文件访问 URL（既有文件 Token 机制）。
     */
    public DownloadVo download(Long id) {
        ImportExportJobDO job = getOwnedOrThrow(id);
        if (!TYPE_EXPORT.equals(job.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "该任务不是导出任务");
        }
        if (!STATUS_SUCCEEDED.equals(job.getStatus()) || job.getResultFileId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导出任务尚未完成");
        }
        SysFileDO file = fileStorageManager.getOwnedOrThrow(job.getResultFileId());
        String url = "/files/" + file.getId();
        String token = fileAccessService.issue(url, SecurityUtils.tryGetUserId());
        return DownloadVo.builder()
                .url(url + "?token=" + token)
                .fileName(file.getOriginalName())
                .build();
    }

    private Long createJob(ImportExportTemplateDO template, String type, Long fileId, String fileName,
                           String queryJson) {
        ImportExportJobDO job = new ImportExportJobDO();
        job.setTenantId(TenantContext.getTenantId());
        // 创建人填充——数据权限按创建人过滤此前因实体未声明该列而落空
        // （created_by 全 NULL，IN 条件永不命中），导致非管理员连自己创建的任务都看不到。
        job.setCreatedBy(SecurityUtils.tryGetUserId());
        job.setTemplateId(template.getId());
        job.setTemplateCode(template.getCode());
        job.setType(type);
        job.setStatus(STATUS_PENDING);
        job.setFileId(fileId);
        job.setFileName(fileName);
        job.setQueryJson(queryJson);
        job.setTotal(0);
        job.setSuccess(0);
        job.setFailed(0);
        jobMapper.insert(job);
        return job.getId();
    }

    /** 导出筛选参数序列化：空参不落库（null），非法 JSON 抛 PARAM_ERROR。 */
    private String serializeQuery(Map<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(query);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导出筛选参数不是合法 JSON");
        }
    }

    /**
     * 单条任务归属校验：租户不匹配视为不存在；非管理员进一步校验创建人是否在当前
     * 用户可见集合（与 page 的 @DataScope 同一语义，admin 短路），越权抛 FORBIDDEN。
     * view/download 共用，杜绝"page 受控但按 id 直查绕过"。
     */
    private ImportExportJobDO getOwnedOrThrow(Long id) {
        ImportExportJobDO job = jobMapper.selectById(id);
        if (job == null || !TenantContext.getTenantId().equals(job.getTenantId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (!dataScopeHelper.isAdmin()) {
            List<Long> allowedUserIds = dataScopeHelper.allowedUserIds();
            if (allowedUserIds != null
                    && (job.getCreatedBy() == null || !allowedUserIds.contains(job.getCreatedBy()))) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }
        return job;
    }

    private JobVo toVo(ImportExportJobDO job) {
        return JobVo.builder()
                .id(job.getId())
                .templateId(job.getTemplateId())
                .templateCode(job.getTemplateCode())
                .type(job.getType())
                .status(job.getStatus())
                .fileId(job.getFileId())
                .fileName(job.getFileName())
                .resultFileId(job.getResultFileId())
                .total(job.getTotal())
                .success(job.getSuccess())
                .failed(job.getFailed())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
