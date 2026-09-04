package cn.admin.scaffold.module.monitor;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.module.monitor.dto.AlertRuleSaveRequest;
import cn.admin.scaffold.module.monitor.entity.SysAlertRuleDO;
import cn.admin.scaffold.module.monitor.mapper.SysAlertRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** R4-1.13：保存告警规则时对 Webhook 地址做 SSRF 静态校验。 */
@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private SysAlertRuleMapper ruleMapper;

    @InjectMocks
    private AlertRuleService ruleService;

    private AlertRuleSaveRequest request(String webhookUrl) {
        AlertRuleSaveRequest request = new AlertRuleSaveRequest();
        request.setRuleName("CPU 告警");
        request.setMetric("cpu_usage");
        request.setOperator(">");
        request.setThreshold(new BigDecimal("80"));
        request.setWebhookUrl(webhookUrl);
        return request;
    }

    @Test
    void createRejectsInternalWebhookUrl() {
        assertThrows(BusinessException.class,
                () -> ruleService.create(request("http://127.0.0.1:9000/hook")));
        verify(ruleMapper, never()).insert(any(SysAlertRuleDO.class));
    }

    @Test
    void createRejectsNonHttpWebhookUrl() {
        assertThrows(BusinessException.class,
                () -> ruleService.create(request("file:///etc/passwd")));
        verify(ruleMapper, never()).insert(any(SysAlertRuleDO.class));
    }

    @Test
    void createAcceptsPublicWebhookUrl() {
        doAnswer(invocation -> {
            SysAlertRuleDO rule = invocation.getArgument(0);
            rule.setId(3L);
            return 1;
        }).when(ruleMapper).insert(any(SysAlertRuleDO.class));

        Long id = ruleService.create(request("https://example.com/hook"));

        assertEquals(3L, id);
        verify(ruleMapper).insert(any(SysAlertRuleDO.class));
    }

    @Test
    void updateRejectsInternalWebhookUrl() {
        AlertRuleSaveRequest request = request("http://192.168.1.1/hook");
        request.setId(5L);

        assertThrows(BusinessException.class, () -> ruleService.update(request));
        verify(ruleMapper, never()).updateById(any(SysAlertRuleDO.class));
    }

    @Test
    void updateAllowsBlankWebhookUrl() {
        // 空视为"未配置 Webhook"，允许保存
        AlertRuleSaveRequest request = request("  ");
        request.setId(5L);

        ruleService.update(request);

        verify(ruleMapper).updateById(any(SysAlertRuleDO.class));
    }
}
