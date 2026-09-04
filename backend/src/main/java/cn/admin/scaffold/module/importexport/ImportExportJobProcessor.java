package cn.admin.scaffold.module.importexport;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.ScheduledTaskLock;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.common.FileCategoryUtils;
import cn.admin.scaffold.module.common.FileStorageManager;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import cn.admin.scaffold.module.importexport.ImportExportTemplateService.ColumnConfig;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import cn.admin.scaffold.module.importexport.entity.ImportExportTemplateDO;
import cn.admin.scaffold.module.importexport.handler.ImportExportHandler;
import cn.admin.scaffold.module.importexport.handler.ImportExportHandlerRegistry;
import cn.admin.scaffold.module.importexport.mapper.ImportExportJobMapper;
import cn.admin.scaffold.module.importexport.mapper.ImportExportTemplateMapper;
import cn.admin.scaffold.module.system.entity.SysFileDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入导出任务处理器：@Scheduled 轮询 + ScheduledTaskLock 跨副本互斥（项目无 @Async）。
 *
 * <p>每次轮询先回收卡死的 PROCESSING 任务（updated_at 超过 processing-timeout 的重置为 PENDING 重新排队），
 * 再取一条 PENDING 任务，CAS 置 PROCESSING 后按 type 处理：
 * <ul>
 *   <li>import：FileStorageManager 读 fileId 文件流 → EasyExcel 读表（doReadSync）→ 按模板
 *       config_json.columns 校验列头（缺失列 PARAM_ERROR）→ handler.importRows 落库计数。
 *       importRows 在独立事务中执行，任一行失败整批回滚，杜绝部分导入；</li>
 *   <li>export：按任务 query_json 反序列化筛选参数透传 handler.export 取数（超 export-max-rows
 *       行上限拒绝）→ EasyExcel 写流 → FileStorageManager 存文件 → 回写 resultFileId。</li>
 * </ul>
 * 业务异常消息落库展示，非业务异常（SQL/驱动/IO）完整堆栈仅入日志、落库统一脱敏；消息截断 500。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportExportJobProcessor {

    private static final Duration POLL_LOCK_TTL = Duration.ofSeconds(20);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_IMPORT = "import";
    private static final String TYPE_EXPORT = "export";
    private static final int ERROR_MESSAGE_MAX = 500;
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** 非业务异常（SQL/驱动/IO）消息可能含内部细节，不落库暴露，统一为通用提示。 */
    private static final String GENERIC_FAILURE_MESSAGE = "处理失败，请稍后重试";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ImportExportJobMapper jobMapper;
    private final ImportExportTemplateMapper templateMapper;
    private final ImportExportHandlerRegistry handlerRegistry;
    private final FileStorageManager fileStorageManager;
    private final ScheduledTaskLock taskLock;
    private final PlatformTransactionManager transactionManager;

    /** PROCESSING 超过该时长无进展即视为卡死，轮询时回收为 PENDING 重新排队。 */
    @Value("${importexport.job.processing-timeout-millis:600000}")
    private long processingTimeoutMillis;

    /** 单次导出最大行数，超限拒绝生成文件，防全量拉爆内存与超大文件。 */
    @Value("${importexport.job.export-max-rows:100000}")
    private int exportMaxRows;

    @Scheduled(fixedDelayString = "${importexport.job.poll-millis:5000}")
    public void poll() {
        if (!taskLock.tryLock("import-export-job-poll", POLL_LOCK_TTL)) {
            return;
        }
        try {
            // 先回收卡死的 PROCESSING 任务（进程崩溃/超时遗留），重置为 PENDING 重新排队，
            // 保证状态机不会因处理方中断而永久卡死
            jobMapper.recycleStaleProcessing(
                    LocalDateTime.now().minus(Duration.ofMillis(processingTimeoutMillis)));
            ImportExportJobDO job = jobMapper.selectOnePendingIgnoreTenant();
            if (job == null) {
                return;
            }
            process(job);
        } finally {
            taskLock.unlock("import-export-job-poll");
        }
    }

    private void process(ImportExportJobDO job) {
        if (jobMapper.casStatus(job.getId(), STATUS_PENDING, STATUS_PROCESSING) == 0) {
            return;
        }
        TenantContext.setTenantId(job.getTenantId());
        try {
            ImportExportTemplateDO template = templateMapper.selectById(job.getTemplateId());
            if (template == null) {
                fail(job, "模板不存在");
                return;
            }
            ImportExportHandler handler = handlerRegistry.get(template.getEntityKey());
            if (TYPE_IMPORT.equals(job.getType())) {
                processImport(job, template, handler);
            } else if (TYPE_EXPORT.equals(job.getType())) {
                processExport(job, template, handler);
            } else {
                fail(job, "不支持的任务类型: " + job.getType());
            }
        } catch (BusinessException exception) {
            // 业务异常消息为面向用户的诊断信息（如"缺少列: xxx"），可落库供任务页展示
            log.warn("导入导出任务处理失败, jobId={}, message={}", job.getId(), exception.getMessage());
            fail(job, exception.getMessage());
        } catch (Exception exception) {
            // 非业务异常消息可能含 SQL/文件路径/驱动内部细节，完整堆栈仅入日志，落库统一脱敏
            log.error("导入导出任务处理失败, jobId={}", job.getId(), exception);
            fail(job, GENERIC_FAILURE_MESSAGE);
        } finally {
            TenantContext.clear();
        }
    }

    private void processImport(ImportExportJobDO job, ImportExportTemplateDO template, ImportExportHandler handler) {
        if (job.getFileId() == null) {
            fail(job, "导入文件缺失");
            return;
        }
        SysFileDO file = fileStorageManager.getById(job.getFileId());
        List<Map<String, Object>> rows = readRows(file, template.getConfigJson());
        // 落库原子性：任一行校验/插入失败整批回滚，避免"部分导入"后任务却报失败
        //（文件读取在事务外完成，避免慢 IO 长时间持有数据库连接）
        int success = inTransaction(status -> handler.importRows(rows, template.getConfigJson()));
        succeed(job, rows.size(), success, rows.size() - success, null);
    }

    private void processExport(ImportExportJobDO job, ImportExportTemplateDO template, ImportExportHandler handler) {
        List<ColumnConfig> columns = ImportExportTemplateService.parseColumns(template.getConfigJson());
        if (columns.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板未配置列");
        }
        Map<String, Object> query = parseQuery(job.getQueryJson());
        List<Map<String, Object>> rows = handler.export(query, template.getConfigJson());
        if (rows.size() > exportMaxRows) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "导出数据超过 " + exportMaxRows + " 行上限，请缩小筛选范围");
        }
        List<List<String>> headers = columns.stream().map(column -> List.of(column.header())).toList();
        byte[] content;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            EasyExcel.write(baos)
                    .excelType(ExcelTypeEnum.XLSX)
                    .head(headers)
                    .sheet()
                    .doWrite(rows);
            content = baos.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "生成导出文件失败");
        }
        String fileName = template.getCode() + "_" + job.getId() + ".xlsx";
        UploadResponse response = fileStorageManager.storeBytes(
                content, fileName, XLSX_CONTENT_TYPE, FileCategoryUtils.OFFICE, "xlsx");
        succeed(job, rows.size(), rows.size(), 0, response.getId());
    }

    /**
     * EasyExcel 读表：headRowNumber(0) 使首行（表头）也返回为数据行，随后按 config columns 校验列头并转命名行。
     */
    private List<Map<String, Object>> readRows(SysFileDO file, String configJson) {
        try (InputStream inputStream = fileStorageManager.open(file)) {
            List<Map<Integer, String>> sheetRows = EasyExcel.read(inputStream)
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();
            return toNamedRows(sheetRows, configJson);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "读取导入文件失败");
        }
    }

    private List<Map<String, Object>> toNamedRows(List<Map<Integer, String>> sheetRows, String configJson) {
        List<ColumnConfig> columns = ImportExportTemplateService.parseColumns(configJson);
        if (columns.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板未配置列");
        }
        if (sheetRows == null || sheetRows.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导入文件为空");
        }
        Map<Integer, String> headerRow = sheetRows.get(0);
        for (int i = 0; i < columns.size(); i++) {
            ColumnConfig column = columns.get(i);
            String actual = headerRow.get(i);
            if (actual == null || !actual.trim().equals(column.header())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "缺少列: " + column.header());
            }
        }
        List<Map<String, Object>> namedRows = new ArrayList<>();
        for (int i = 1; i < sheetRows.size(); i++) {
            Map<Integer, String> raw = sheetRows.get(i);
            Map<String, Object> named = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                named.put(columns.get(c).key(), raw.get(c));
            }
            namedRows.add(named);
        }
        return namedRows;
    }

    private void succeed(ImportExportJobDO job, int total, int success, int failed, Long resultFileId) {
        ImportExportJobDO update = new ImportExportJobDO();
        update.setId(job.getId());
        update.setStatus(STATUS_SUCCEEDED);
        update.setTotal(total);
        update.setSuccess(success);
        update.setFailed(failed);
        update.setResultFileId(resultFileId);
        jobMapper.updateById(update);
        log.info("导入导出任务处理成功: jobId={}, total={}, success={}, failed={}",
                job.getId(), total, success, failed);
    }

    private void fail(ImportExportJobDO job, String message) {
        ImportExportJobDO update = new ImportExportJobDO();
        update.setId(job.getId());
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(truncate(message));
        jobMapper.updateById(update);
        log.warn("导入导出任务处理失败: jobId={}, message={}", job.getId(), message);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MESSAGE_MAX ? message.substring(0, ERROR_MESSAGE_MAX) : message;
    }

    /** 数据落库事务边界：importRows 内任一步失败整批回滚，异常原样向上抛出由 process 统一置 FAILED。 */
    private <T> T inTransaction(TransactionCallback<T> callback) {
        return new TransactionTemplate(transactionManager).execute(callback);
    }

    /** 反序列化导出筛选参数；null/空串按空参导出全量（处理器自行决定过滤语义）。 */
    private Map<String, Object> parseQuery(String queryJson) {
        if (queryJson == null || queryJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> query = OBJECT_MAPPER.readValue(queryJson, new TypeReference<>() {
            });
            return query == null ? Map.of() : query;
        } catch (JsonProcessingException exception) {
            // 创建任务时已校验合法 JSON，此路径仅理论可达；保守按空参处理
            log.warn("导出任务筛选参数反序列化失败, queryJson={}", queryJson);
            return Map.of();
        }
    }
}
