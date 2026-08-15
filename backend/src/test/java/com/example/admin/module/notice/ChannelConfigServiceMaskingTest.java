package com.example.admin.module.notice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.notice.channel.ChannelFactory;
import com.example.admin.module.notice.dto.ChannelConfigQuery;
import com.example.admin.module.notice.dto.ChannelConfigSaveRequest;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.example.admin.module.notice.mapper.SysChannelConfigMapper;
import com.example.admin.module.notice.mapper.SysChannelLogMapper;
import com.example.admin.module.notice.vo.ChannelConfigVo;
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
 * 渠道配置脱敏（批8d）：回显打码敏感配置 + 保存合并打码占位保留真实密钥。
 */
class ChannelConfigServiceMaskingTest {

    private SysChannelConfigMapper configMapper;
    private ChannelConfigService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        configMapper = mock(SysChannelConfigMapper.class);
        SysChannelLogMapper logMapper = mock(SysChannelLogMapper.class);
        ChannelFactory factory = mock(ChannelFactory.class);
        service = new ChannelConfigService(configMapper, logMapper, factory, objectMapper);
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

    /** 保存：请求中未改动的敏感值（******）用库中原值补齐，避免回写覆盖真实密钥。 */
    @Test
    void updateMergesMaskedPlaceholdersWithExistingSecrets() {
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
        assertThat(captor.getValue().getConfigJson())
                .contains("\"secret\":\"real-secret\"")
                .contains("\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\"");
    }

    /** 新建（无 id）：不合并，直接采用请求值。 */
    @Test
    void createDoesNotMerge() {
        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setChannelType("DINGTALK");
        request.setChannelName("新渠道");
        request.setConfigJson("{\"webhook\":\"https://x/hook\",\"secret\":\"fresh-secret\"}");

        service.create(request);

        ArgumentCaptor<SysChannelConfigDO> captor = ArgumentCaptor.forClass(SysChannelConfigDO.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getConfigJson()).contains("\"secret\":\"fresh-secret\"");
    }
}
