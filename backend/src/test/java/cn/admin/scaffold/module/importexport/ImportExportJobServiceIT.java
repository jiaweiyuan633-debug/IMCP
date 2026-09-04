package cn.admin.scaffold.module.importexport;

import cn.admin.scaffold.AbstractIntegrationTest;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.importexport.dto.JobCreateRequest;
import cn.admin.scaffold.module.importexport.dto.JobQuery;
import cn.admin.scaffold.module.importexport.dto.TemplateQuery;
import cn.admin.scaffold.module.importexport.dto.TemplateSaveRequest;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import cn.admin.scaffold.module.importexport.mapper.ImportExportJobMapper;
import cn.admin.scaffold.module.importexport.vo.JobVo;
import cn.admin.scaffold.module.importexport.vo.TemplateVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导入导出任务集成测试：模板 CRUD 往返 + 导入任务状态机 PENDING→PROCESSING。
 * 拉长轮询间隔避免定时处理器在用例执行期间抢占任务（状态机经 Mapper CAS 直接验证）。
 */
@TestPropertySource(properties = "importexport.job.poll-millis=3600000")
class ImportExportJobServiceIT extends AbstractIntegrationTest {

    private static final String CONFIG = "{\"sheetName\":\"字典数据\",\"columns\":["
            + "{\"key\":\"dictLabel\",\"header\":\"字典标签\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictValue\",\"header\":\"字典键值\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictType\",\"header\":\"字典类型\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictSort\",\"header\":\"显示排序\",\"required\":true,\"dataType\":\"int\"}]}";

    @Autowired
    private ImportExportTemplateService templateService;
    @Autowired
    private ImportExportJobService jobService;
    @Autowired
    private ImportExportJobMapper jobMapper;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TemplateSaveRequest templateRequest(String code, String type) {
        TemplateSaveRequest request = new TemplateSaveRequest();
        request.setCode(code);
        request.setName("IT模板-" + type);
        request.setType(type);
        request.setEntityKey("dict-data");
        request.setConfigJson(CONFIG);
        request.setStatus(1);
        return request;
    }

    @Test
    void templateCrudRoundTrip() {
        Long id = templateService.create(templateRequest("IT-TPL-001", "import"));

        assertThat(id).isNotNull();
        TemplateQuery query = new TemplateQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setCode("IT-TPL-001");
        PageResult<TemplateVo> page = templateService.page(query);
        assertThat(page.getRecords()).extracting(TemplateVo::getCode).contains("IT-TPL-001");

        TemplateSaveRequest update = templateRequest("IT-TPL-001", "import");
        update.setId(id);
        update.setName("IT模板-改");
        templateService.update(update);

        templateService.delete(id);
        assertThat(templateService.page(query).getRecords()).isEmpty();
    }

    @Test
    void importJobPendingToProcessing() {
        Long templateId = templateService.create(templateRequest("IT-TPL-002", "import"));

        JobCreateRequest request = new JobCreateRequest();
        request.setBizNo("BIZ-002");
        request.setTemplateCode("IT-TPL-002");
        request.setFileId(1L);
        Long jobId = jobService.createImport(request);

        ImportExportJobDO pending = jobMapper.selectById(jobId);
        assertThat(pending).isNotNull();
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getType()).isEqualTo("import");
        assertThat(pending.getTemplateId()).isEqualTo(templateId);

        JobQuery query = new JobQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setStatus("PENDING");
        PageResult<JobVo> page = jobService.page(query);
        assertThat(page.getRecords()).extracting(JobVo::getId).contains(jobId);

        int updated = jobMapper.casStatus(jobId, "PENDING", "PROCESSING");
        assertThat(updated).isEqualTo(1);

        ImportExportJobDO processing = jobMapper.selectById(jobId);
        assertThat(processing.getStatus()).isEqualTo("PROCESSING");
    }
}
