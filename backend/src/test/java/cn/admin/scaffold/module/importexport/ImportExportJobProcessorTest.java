package cn.admin.scaffold.module.importexport;

import com.alibaba.excel.EasyExcel;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.ScheduledTaskLock;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.common.FileStorageManager;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import cn.admin.scaffold.module.importexport.entity.ImportExportTemplateDO;
import cn.admin.scaffold.module.importexport.handler.ImportExportHandler;
import cn.admin.scaffold.module.importexport.handler.ImportExportHandlerRegistry;
import cn.admin.scaffold.module.importexport.mapper.ImportExportJobMapper;
import cn.admin.scaffold.module.importexport.mapper.ImportExportTemplateMapper;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入导出任务处理器单元测试（修复 W3）：
 * 覆盖卡死任务回收、导入落库事务化、异常消息脱敏、导出筛选参数透传与行数上限。
 */
@ExtendWith(MockitoExtension.class)
class ImportExportJobProcessorTest {

    private static final String CONFIG = "{\"sheetName\":\"字典数据\",\"columns\":["
            + "{\"key\":\"dictLabel\",\"header\":\"字典标签\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictValue\",\"header\":\"字典键值\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictType\",\"header\":\"字典类型\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictSort\",\"header\":\"显示排序\",\"required\":true,\"dataType\":\"int\"}]}";

    @Mock
    private ImportExportJobMapper jobMapper;
    @Mock
    private ImportExportTemplateMapper templateMapper;
    @Mock
    private ImportExportHandlerRegistry handlerRegistry;
    @Mock
    private ImportExportHandler handler;
    @Mock
    private FileStorageManager fileStorageManager;
    @Mock
    private ScheduledTaskLock taskLock;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ImportExportJobProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ImportExportJobProcessor(
                jobMapper, templateMapper, handlerRegistry, fileStorageManager, taskLock, transactionManager);
        ReflectionTestUtils.setField(processor, "exportMaxRows", 100_000);
        ReflectionTestUtils.setField(processor, "processingTimeoutMillis", 600_000);
        TenantContext.setTenantId(1L);
        when(taskLock.tryLock(anyString(), any())).thenReturn(true);
        // 仅 import 路径走事务模板，export/回收用例不触发 → 声明为宽松避免 UnnecessaryStubbing
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ImportExportTemplateDO template(String type) {
        ImportExportTemplateDO template = new ImportExportTemplateDO();
        template.setId(10L);
        template.setEntityKey("dict-data");
        template.setType(type);
        template.setConfigJson(CONFIG);
        return template;
    }

    private ImportExportJobDO job(String type, Long fileId, String queryJson) {
        ImportExportJobDO job = new ImportExportJobDO();
        job.setId(1L);
        job.setTenantId(1L);
        job.setTemplateId(10L);
        job.setTemplateCode("TPL");
        job.setType(type);
        job.setStatus("PENDING");
        job.setFileId(fileId);
        job.setQueryJson(queryJson);
        return job;
    }

    /** 生成首行表头 + 一行数据的 xlsx 字节流（readRows 以 headRowNumber(0) 读回全部行）。 */
    private byte[] sampleXlsx() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<List<String>> head = List.of(
                List.of("字典标签"), List.of("字典键值"), List.of("字典类型"), List.of("显示排序"));
        List<List<Object>> data = List.of(List.of("测试标签", "test", "TYPE", 1));
        // autoCloseStream(false)：doWrite 后流需保持打开，才能 toByteArray 取出字节
        EasyExcel.write(baos).autoCloseStream(false).head(head).sheet().doWrite(data);
        return baos.toByteArray();
    }

    // ---------- 卡死回收 ----------

    @Test
    void recyclesStaleProcessingBeforePickingPending() {
        when(jobMapper.selectOnePendingIgnoreTenant()).thenReturn(null);

        processor.poll();

        verify(jobMapper).recycleStaleProcessing(any());
    }

    // ---------- 导入事务化 ----------

    @Test
    void importSuccessCommitsInTransaction() {
        when(jobMapper.selectOnePendingIgnoreTenant()).thenReturn(job("import", 100L, null));
        when(jobMapper.casStatus(1L, "PENDING", "PROCESSING")).thenReturn(1);
        when(templateMapper.selectById(10L)).thenReturn(template("import"));
        when(handlerRegistry.get("dict-data")).thenReturn(handler);
        when(handler.importRows(anyList(), anyString())).thenReturn(1);
        SysFileDO file = new SysFileDO();
        file.setId(100L);
        when(fileStorageManager.getById(100L)).thenReturn(file);
        when(fileStorageManager.open(file)).thenReturn(new ByteArrayInputStream(sampleXlsx()));

        processor.poll();

        ArgumentCaptor<ImportExportJobDO> captor = ArgumentCaptor.forClass(ImportExportJobDO.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(captor.getValue().getTotal()).isEqualTo(1);
        assertThat(captor.getValue().getSuccess()).isEqualTo(1);
        assertThat(captor.getValue().getFailed()).isEqualTo(0);
        verify(transactionManager).getTransaction(any());
    }

    @Test
    void importBusinessFailureFailsJobWithUserMessage() {
        when(jobMapper.selectOnePendingIgnoreTenant()).thenReturn(job("import", 100L, null));
        when(jobMapper.casStatus(1L, "PENDING", "PROCESSING")).thenReturn(1);
        when(templateMapper.selectById(10L)).thenReturn(template("import"));
        when(handlerRegistry.get("dict-data")).thenReturn(handler);
        when(handler.importRows(anyList(), anyString()))
                .thenThrow(new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字段「字典标签」缺失"));
        SysFileDO file = new SysFileDO();
        file.setId(100L);
        when(fileStorageManager.getById(100L)).thenReturn(file);
        when(fileStorageManager.open(file)).thenReturn(new ByteArrayInputStream(sampleXlsx()));

        processor.poll();

        ArgumentCaptor<ImportExportJobDO> captor = ArgumentCaptor.forClass(ImportExportJobDO.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("字段「字典标签」缺失");
    }

    @Test
    void importNonBusinessFailureFailsJobWithGenericMessage() {
        when(jobMapper.selectOnePendingIgnoreTenant()).thenReturn(job("import", 100L, null));
        when(jobMapper.casStatus(1L, "PENDING", "PROCESSING")).thenReturn(1);
        when(templateMapper.selectById(10L)).thenReturn(template("import"));
        when(handlerRegistry.get("dict-data")).thenReturn(handler);
        when(handler.importRows(anyList(), anyString()))
                .thenThrow(new RuntimeException("SQLSTATE 42000: Table 'admin.sys_dict_data' doesn't exist"));
        SysFileDO file = new SysFileDO();
        file.setId(100L);
        when(fileStorageManager.getById(100L)).thenReturn(file);
        when(fileStorageManager.open(file)).thenReturn(new ByteArrayInputStream(sampleXlsx()));

        processor.poll();

        ArgumentCaptor<ImportExportJobDO> captor = ArgumentCaptor.forClass(ImportExportJobDO.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        // 非业务异常消息不落库暴露内部细节，统一为通用提示
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("处理失败，请稍后重试");
    }

    // ---------- 导出：query 透传 + 行数上限 ----------

    @Test
    void exportPassesStoredQueryThroughToHandler() {
        when(jobMapper.selectOnePendingIgnoreTenant()).thenReturn(job("export", null, "{\"dictType\":\"TYPE_A\"}"));
        when(jobMapper.casStatus(1L, "PENDING", "PROCESSING")).thenReturn(1);
        when(templateMapper.selectById(10L)).thenReturn(template("export"));
        when(handlerRegistry.get("dict-data")).thenReturn(handler);
        when(handler.export(any(), anyString()))
                .thenReturn(List.of(Map.of("dictLabel", "A", "dictValue", "B", "dictType", "TYPE_A", "dictSort", 1)));
        UploadResponse response = UploadResponse.builder().id(99L).build();
        when(fileStorageManager.storeBytes(any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);

        processor.poll();

        // 创建任务时序列化的 query_json 被反序列化并透传到 handler.export，而非空参导出全量
        verify(handler).export(argThat(query ->
                "TYPE_A".equals(query.get("dictType"))), anyString());
        ArgumentCaptor<ImportExportJobDO> captor = ArgumentCaptor.forClass(ImportExportJobDO.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(captor.getValue().getResultFileId()).isEqualTo(99L);
    }

    @Test
    void exportOverMaxRowsRejects() {
        ReflectionTestUtils.setField(processor, "exportMaxRows", 2);
        when(jobMapper.selectOnePendingIgnoreTenant()).thenReturn(job("export", null, null));
        when(jobMapper.casStatus(1L, "PENDING", "PROCESSING")).thenReturn(1);
        when(templateMapper.selectById(10L)).thenReturn(template("export"));
        when(handlerRegistry.get("dict-data")).thenReturn(handler);
        when(handler.export(any(), anyString())).thenReturn(List.of(
                Map.of("dictLabel", "A"), Map.of("dictLabel", "B"), Map.of("dictLabel", "C")));

        processor.poll();

        ArgumentCaptor<ImportExportJobDO> captor = ArgumentCaptor.forClass(ImportExportJobDO.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("导出数据超过 2 行上限");
    }
}
