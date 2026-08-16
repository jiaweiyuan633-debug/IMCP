package com.example.admin.module.importexport;

import com.example.admin.common.BusinessException;
import com.example.admin.common.FileAccessService;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.common.FileStorageManager;
import com.example.admin.module.importexport.dto.JobCreateRequest;
import com.example.admin.module.importexport.entity.ImportExportJobDO;
import com.example.admin.module.importexport.entity.ImportExportTemplateDO;
import com.example.admin.module.importexport.mapper.ImportExportJobMapper;
import com.example.admin.module.importexport.vo.DownloadVo;
import com.example.admin.module.importexport.vo.JobVo;
import com.example.admin.module.system.DataScopeHelper;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 导入导出任务服务单测（R4-1.38）：创建任务填充 created_by（批10 数据权限落空的根因修复）
 * + view/download 单条路径归属校验（page 受控但按 id 直查绕过）。
 */
class ImportExportJobServiceTest {

    private ImportExportJobMapper jobMapper;
    private ImportExportTemplateService templateService;
    private FileStorageManager fileStorageManager;
    private FileAccessService fileAccessService;
    private DataScopeHelper dataScopeHelper;
    private ImportExportJobService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(ImportExportJobMapper.class);
        templateService = mock(ImportExportTemplateService.class);
        fileStorageManager = mock(FileStorageManager.class);
        fileAccessService = mock(FileAccessService.class);
        dataScopeHelper = mock(DataScopeHelper.class);
        service = new ImportExportJobService(jobMapper, templateService, fileStorageManager,
                fileAccessService, dataScopeHelper);
        TenantContext.setTenantId(1L);
        setLoginUser(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void setLoginUser(Long userId) {
        LoginUser loginUser = LoginUser.builder().userId(userId).username("u" + userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private ImportExportJobDO job(Long id, Long createdBy) {
        ImportExportJobDO job = new ImportExportJobDO();
        job.setId(id);
        job.setTenantId(1L);
        job.setCreatedBy(createdBy);
        job.setType("export");
        job.setStatus("SUCCEEDED");
        return job;
    }

    private ImportExportTemplateDO template(String code, String type) {
        ImportExportTemplateDO template = new ImportExportTemplateDO();
        template.setId(1L);
        template.setCode(code);
        template.setType(type);
        return template;
    }

    private JobCreateRequest importRequest() {
        JobCreateRequest request = new JobCreateRequest();
        request.setBizNo("BIZ-001");
        request.setTemplateCode("TPL-IMPORT");
        request.setFileId(1L);
        return request;
    }

    // ---------- 创建：created_by 填充（批10 注册 created_by 数据权限后必须落值，否则过滤落空） ----------

    @Test
    void createImportFillsCreatedBy() {
        when(templateService.getByCodeRequired("TPL-IMPORT")).thenReturn(template("TPL-IMPORT", "import"));
        when(jobMapper.insert(any(ImportExportJobDO.class))).thenAnswer(invocation -> {
            ((ImportExportJobDO) invocation.getArgument(0)).setId(20L);
            return 1;
        });

        Long id = service.createImport(importRequest());

        assertThat(id).isEqualTo(20L);
        ArgumentCaptor<ImportExportJobDO> captor = ArgumentCaptor.forClass(ImportExportJobDO.class);
        verify(jobMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
    }

    // ---------- 单条路径归属校验（view/download，R4-1.38） ----------

    /** 非管理员查看自己的任务：放行。 */
    @Test
    void viewAllowsOwnJobForNonAdmin() {
        when(jobMapper.selectById(5L)).thenReturn(job(5L, 7L));
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));

        JobVo vo = service.view(5L);

        assertThat(vo.getId()).isEqualTo(5L);
    }

    /** 非管理员查看他人任务：越权 FORBIDDEN。 */
    @Test
    void viewRejectsOthersJobForNonAdmin() {
        when(jobMapper.selectById(6L)).thenReturn(job(6L, 8L));
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.view(6L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    /** 管理员短路：可查看任意任务。 */
    @Test
    void viewAllowsAnyJobForAdmin() {
        when(jobMapper.selectById(7L)).thenReturn(job(7L, 8L));
        when(dataScopeHelper.isAdmin()).thenReturn(true);

        JobVo vo = service.view(7L);

        assertThat(vo.getId()).isEqualTo(7L);
    }

    /** 非管理员下载他人导出成果：越权 FORBIDDEN，且不触达文件层。 */
    @Test
    void downloadRejectsOthersJobForNonAdmin() {
        when(jobMapper.selectById(8L)).thenReturn(job(8L, 9L));
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.download(8L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verifyNoInteractions(fileStorageManager);
    }

    /** 非管理员下载自己的导出成果：签发带 Token 的文件 URL。 */
    @Test
    void downloadReturnsSignedUrlForOwnJob() {
        ImportExportJobDO own = job(9L, 7L);
        own.setResultFileId(1L);
        when(jobMapper.selectById(9L)).thenReturn(own);
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));
        SysFileDO file = new SysFileDO();
        file.setId(1L);
        file.setOriginalName("data.xlsx");
        when(fileStorageManager.getOwnedOrThrow(1L)).thenReturn(file);
        when(fileAccessService.issue("/files/1", 7L)).thenReturn("tok123");

        DownloadVo vo = service.download(9L);

        assertThat(vo.getUrl()).isEqualTo("/files/1?token=tok123");
        assertThat(vo.getFileName()).isEqualTo("data.xlsx");
    }
}
