package cn.admin.scaffold.module.notice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.module.notice.channel.ChannelFactory;
import cn.admin.scaffold.module.notice.channel.MessageChannelSender;
import cn.admin.scaffold.module.notice.dto.ChannelConfigQuery;
import cn.admin.scaffold.module.notice.dto.ChannelConfigSaveRequest;
import cn.admin.scaffold.module.notice.dto.ChannelSendRequest;
import cn.admin.scaffold.module.notice.entity.SysChannelConfigDO;
import cn.admin.scaffold.module.notice.entity.SysChannelLogDO;
import cn.admin.scaffold.module.notice.mapper.SysChannelConfigMapper;
import cn.admin.scaffold.module.notice.mapper.SysChannelLogMapper;
import cn.admin.scaffold.module.notice.vo.ChannelConfigVo;
import cn.admin.scaffold.module.notice.vo.ChannelLogVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道配置脱敏与加密（批8d + 批10）：
 * 回显打码敏感配置 + 保存合并打码占位保留真实密钥 + 敏感字段落库前加密。
 */
class ChannelConfigServiceMaskingTest {

    private SysChannelConfigMapper configMapper;
    private SysChannelLogMapper logMapper;
    private ChannelConfigService service;
    private ChannelConfigCipher cipher;
    private SecretCipher secretCipher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        configMapper = mock(SysChannelConfigMapper.class);
        logMapper = mock(SysChannelLogMapper.class);
        ChannelFactory factory = mock(ChannelFactory.class);
        secretCipher = new SecretCipher("unit-test-encryption-key-not-for-prod", null);
        cipher = new ChannelConfigCipher(secretCipher, objectMapper);
        service = new ChannelConfigService(configMapper, logMapper, factory, objectMapper, cipher, secretCipher);
    }

    private SysChannelConfigDO dingtalkConfig() {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setId(1L);
        config.setChannelType("DINGTALK");
        config.setChannelName("钉钉机器人");
        config.setStatus(1);
        config.setConfigJson("{\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\","
                + "\"secret\":\"real-secret\"}");
        return config;
    }

    /** 回显：密钥打码，地址/账号字段保留，前端可正常编辑回填。 */
    @Test
    void pageMasksSensitiveConfigValuesButKeepsAddress() {
        Page<SysChannelConfigDO> result = new Page<>();
        result.setRecords(List.of(dingtalkConfig()));
        result.setTotal(1);
        when(configMapper.selectPage(any(Page.class), any())).thenReturn(result);

        PageResult<ChannelConfigVo> pageResult = service.page(new ChannelConfigQuery());

        String masked = pageResult.getRecords().get(0).getConfigJson();
        assertThat(masked)
                .contains("\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\"")
                .contains("\"secret\":\"******\"")
                .doesNotContain("real-secret");
    }

    /** 回显（批10）：库中已是 enc: 密文的敏感值同样被整体打码，不泄漏密文前缀，也无需先解密。 */
    @Test
    void pageMasksEncryptedSensitiveValues() {
        SysChannelConfigDO config = dingtalkConfig();
        config.setConfigJson("{\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\","
                + "\"secret\":\"" + secretCipher.encrypt("real-secret") + "\"}");
        Page<SysChannelConfigDO> result = new Page<>();
        result.setRecords(List.of(config));
        result.setTotal(1);
        when(configMapper.selectPage(any(Page.class), any())).thenReturn(result);

        PageResult<ChannelConfigVo> pageResult = service.page(new ChannelConfigQuery());

        String masked = pageResult.getRecords().get(0).getConfigJson();
        assertThat(masked)
                .contains("\"secret\":\"******\"")
                .doesNotContain("enc:")
                .doesNotContain("real-secret");
    }

    /** 保存：请求中未改动的敏感值（******）用库中原值补齐，随后加密落库；地址字段保持明文。 */
    @Test
    void updateMergesMaskedPlaceholdersAndEncryptsSensitiveValues() {
        when(configMapper.selectById(1L)).thenReturn(dingtalkConfig());

        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setId(1L);
        request.setChannelType("DINGTALK");
        request.setChannelName("钉钉机器人(改)");
        request.setConfigJson("{\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\","
                + "\"secret\":\"******\"}");

        service.update(request);

        ArgumentCaptor<SysChannelConfigDO> captor = ArgumentCaptor.forClass(SysChannelConfigDO.class);
        verify(configMapper).updateById(captor.capture());
        String saved = captor.getValue().getConfigJson();
        assertThat(saved)
                .contains("\"secret\":\"enc:")
                .contains("\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\"")
                .doesNotContain("real-secret")
                .doesNotContain("******");
        // 解密还原，确认合并确实补回了库中原值（而非把掩码当新值落库）
        assertThat(cipher.decryptConfig(saved)).contains("\"secret\":\"real-secret\"");
    }

    /** 新建（无 id）：不合并，直接采用请求值；敏感字段落库前加密。 */
    @Test
    void createEncryptsSensitiveValuesWithoutMerge() {
        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setChannelType("DINGTALK");
        request.setChannelName("新渠道");
        request.setConfigJson("{\"webhook\":\"https://x/hook\",\"secret\":\"fresh-secret\"}");

        service.create(request);

        ArgumentCaptor<SysChannelConfigDO> captor = ArgumentCaptor.forClass(SysChannelConfigDO.class);
        verify(configMapper).insert(captor.capture());
        String saved = captor.getValue().getConfigJson();
        assertThat(saved)
                .contains("\"secret\":\"enc:")
                .doesNotContain("fresh-secret");
        assertThat(cipher.decryptConfig(saved)).contains("\"secret\":\"fresh-secret\"");
    }

    // ---------- R4-1.38：渠道发送记录 PII 防护（content/target 加密落库，回显解密/fail-closed 打码） ----------

    /** 发送：sender 拿明文发送，落库的 target/content 必须是 enc: 密文，明文不落库。 */
    @Test
    void sendPersistsEncryptedTargetAndContent() {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setId(1L);
        config.setChannelType("MAIL");
        config.setChannelName("测试邮箱");
        config.setStatus(1);
        config.setConfigJson("{\"username\":\"ops@x.com\",\"password\":\"smtp-pass\"}");
        MessageChannelSender sender = mock(MessageChannelSender.class);
        when(configMapper.selectById(1L)).thenReturn(config);
        // factory.get 按真实枚举解析，发送成功返回 null
        ChannelFactory factory = mock(ChannelFactory.class);
        when(factory.get(ChannelType.MAIL)).thenReturn(sender);
        when(sender.send(any(), any(), any(), any())).thenReturn(null);
        service = new ChannelConfigService(configMapper, logMapper, factory, objectMapper, cipher, secretCipher);

        ChannelSendRequest request = new ChannelSendRequest();
        request.setChannelId(1L);
        request.setTarget("13800138000");
        request.setTitle("验证码");
        request.setContent("您的验证码是 123456，5 分钟内有效");
        service.send(request);

        ArgumentCaptor<SysChannelLogDO> captor = ArgumentCaptor.forClass(SysChannelLogDO.class);
        verify(logMapper).insert(captor.capture());
        SysChannelLogDO saved = captor.getValue();
        assertThat(saved.getTarget()).startsWith("enc:");
        assertThat(saved.getContent()).startsWith("enc:");
        assertThat(saved.getTarget()).doesNotContain("13800138000");
        assertThat(saved.getContent()).doesNotContain("123456");
        // 解密还原，确认可读回显路径数据无损
        assertThat(secretCipher.decrypt(saved.getTarget())).isEqualTo("13800138000");
        assertThat(secretCipher.decrypt(saved.getContent())).isEqualTo("您的验证码是 123456，5 分钟内有效");
        assertThat(saved.getTitle()).isEqualTo("验证码");
    }

    /** 发送日志回显：enc: 密文解密为明文（title 保留明文）。 */
    @Test
    void logPageDecryptsEncryptedContentBackToPlaintext() {
        SysChannelLogDO log = new SysChannelLogDO();
        log.setId(9L);
        log.setChannelType("MAIL");
        log.setChannelId(1L);
        log.setTarget(secretCipher.encrypt("a@example.com"));
        log.setTitle("验证码");
        log.setContent(secretCipher.encrypt("您的验证码是 123456"));
        log.setStatus(1);
        Page<SysChannelLogDO> result = new Page<>();
        result.setRecords(List.of(log));
        result.setTotal(1);
        when(logMapper.selectPage(any(Page.class), any())).thenReturn(result);

        PageResult<ChannelLogVo> pageResult = service.logPage(new ChannelConfigQuery());

        assertThat(pageResult.getRecords()).hasSize(1);
        ChannelLogVo vo = pageResult.getRecords().get(0);
        assertThat(vo.getTarget()).isEqualTo("a@example.com");
        assertThat(vo.getContent()).isEqualTo("您的验证码是 123456");
        assertThat(vo.getTitle()).isEqualTo("验证码");
    }

    /** 存量明文行（V62 之前的数据）fail-closed：不因无 enc: 前缀而回显真实内容，统一打码。 */
    @Test
    void logPageMasksLegacyPlaintextRows() {
        SysChannelLogDO log = new SysChannelLogDO();
        log.setId(10L);
        log.setChannelType("SMS");
        log.setChannelId(1L);
        log.setTarget("13800138000");
        log.setTitle("验证码");
        log.setContent("您的验证码是 123456");
        log.setStatus(1);
        Page<SysChannelLogDO> result = new Page<>();
        result.setRecords(List.of(log));
        result.setTotal(1);
        when(logMapper.selectPage(any(Page.class), any())).thenReturn(result);

        PageResult<ChannelLogVo> pageResult = service.logPage(new ChannelConfigQuery());

        ChannelLogVo vo = pageResult.getRecords().get(0);
        assertThat(vo.getTarget()).isEqualTo("******");
        assertThat(vo.getContent()).isEqualTo("******");
        assertThat(vo.getTitle()).isEqualTo("验证码");
    }
}
