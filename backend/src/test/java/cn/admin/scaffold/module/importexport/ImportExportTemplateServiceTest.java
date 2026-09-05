package cn.admin.scaffold.module.importexport;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.importexport.dto.TemplateSaveRequest;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import cn.admin.scaffold.module.importexport.entity.ImportExportTemplateDO;
import cn.admin.scaffold.module.importexport.handler.ImportExportHandlerRegistry;
import cn.admin.scaffold.module.importexport.mapper.ImportExportJobMapper;
import cn.admin.scaffold.module.importexport.mapper.ImportExportTemplateMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板服务单测：code 唯一（IMPORT_TEMPLATE_CODE_EXISTS）、type 取值校验（PARAM_ERROR）、
 * 编辑乐观锁冲突、删除待处理任务引用校验。
 */
class ImportExportTemplateServiceTest {

    // checkCodeUnique 内部构造 LambdaQueryWrapper，需初始化实体 TableInfo
    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ImportExportTemplateDO.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ImportExportJobDO.class);
    }

    private static final String VALID_CONFIG = "{\"sheetName\":\"字典数据\",\"columns\":["
            + "{\"key\":\"dictLabel\",\"header\":\"字典标签\",\"required\":true,\"dataType\":\"string\"}]}";

    private final ImportExportTemplateMapper templateMapper = mock(ImportExportTemplateMapper.class);
    private final ImportExportHandlerRegistry handlerRegistry = mock(ImportExportHandlerRegistry.class);
    private final ImportExportJobMapper jobMapper = mock(ImportExportJobMapper.class);
    private final ImportExportTemplateService service =
            new ImportExportTemplateService(templateMapper, handlerRegistry, jobMapper);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TemplateSaveRequest request(String code) {
        TemplateSaveRequest request = new TemplateSaveRequest();
        request.setCode(code);
        request.setName("字典数据模板");
        request.setType("import");
        request.setEntityKey("dict-data");
        request.setConfigJson(VALID_CONFIG);
        request.setStatus(1);
        return request;
    }

    @Test
    void createPersistsTemplate() {
        when(handlerRegistry.supports("dict-data")).thenReturn(true);
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(templateMapper.insert(any(ImportExportTemplateDO.class))).thenAnswer(invocation -> {
            ImportExportTemplateDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        Long id = service.create(request("TPL-001"));

        assertNotNull(id);
    }

    @Test
    void duplicateCodeRejected() {
        when(handlerRegistry.supports(anyString())).thenReturn(true);
        ImportExportTemplateDO exists = new ImportExportTemplateDO();
        exists.setId(10L);
        exists.setCode("TPL-002");
        when(templateMapper.selectOne(any())).thenReturn(exists);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(request("TPL-002")));

        assertEquals(ResultCode.IMPORT_TEMPLATE_CODE_EXISTS.getCode(), exception.getCode());
    }

    @Test
    void invalidTypeRejected() {
        TemplateSaveRequest request = request("TPL-003");
        request.setType("invalid");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void invalidConfigRejected() {
        when(handlerRegistry.supports("dict-data")).thenReturn(true);
        TemplateSaveRequest request = request("TPL-004");
        request.setConfigJson("not-a-json");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void createDuplicateKeyRejectedWithBusinessCode() {
        // 并发同码创建：预检通过但 insert 命中唯一键 → 转精确业务码而非泛化 500
        when(handlerRegistry.supports("dict-data")).thenReturn(true);
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(templateMapper.insert(any(ImportExportTemplateDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(request("TPL-RACE")));

        assertEquals(ResultCode.IMPORT_TEMPLATE_CODE_EXISTS.getCode(), exception.getCode());
    }

    @Test
    void updatePassesThroughVersion() {
        when(templateMapper.selectById(10L)).thenReturn(existingTemplate(10L));
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(handlerRegistry.supports("dict-data")).thenReturn(true);
        when(templateMapper.updateById(any(ImportExportTemplateDO.class))).thenReturn(1);

        TemplateSaveRequest request = request("TPL-005");
        request.setId(10L);
        request.setVersion(2);
        service.update(request);

        ArgumentCaptor<ImportExportTemplateDO> captor = ArgumentCaptor.forClass(ImportExportTemplateDO.class);
        verify(templateMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void updateConflictOnStaleVersion() {
        when(templateMapper.selectById(10L)).thenReturn(existingTemplate(10L));
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(handlerRegistry.supports("dict-data")).thenReturn(true);
        when(templateMapper.updateById(any(ImportExportTemplateDO.class))).thenReturn(0);

        TemplateSaveRequest request = request("TPL-006");
        request.setId(10L);
        request.setVersion(1);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertThat(exception.getMessage()).contains("刷新");
    }

    @Test
    void deleteRejectedWhenPendingJobsReference() {
        when(jobMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(10L));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertThat(exception.getMessage()).contains("待处理任务");
    }

    @Test
    void deleteAllowedWhenNoPendingJobs() {
        when(jobMapper.selectCount(any())).thenReturn(0L);
        when(templateMapper.selectById(10L)).thenReturn(existingTemplate(10L));

        service.delete(10L);

        verify(templateMapper).deleteById(10L);
    }

    private ImportExportTemplateDO existingTemplate(Long id) {
        ImportExportTemplateDO template = new ImportExportTemplateDO();
        template.setId(id);
        template.setCode("TPL-EXIST");
        return template;
    }
}
