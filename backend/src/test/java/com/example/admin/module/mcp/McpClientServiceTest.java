package com.example.admin.module.mcp;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.SecretCipher;
import com.example.admin.common.SsrfUrlValidator;
import com.example.admin.module.mcp.entity.SysMcpServerDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * MCP Client 投递 SSRF 复核单测（R4-1.40 批次13）。
 *
 * <p>保存时的静态校验兜不住「主机名解析到内网 IP」与「保存后 DNS 变更」，连接前必须按
 * 解析后的全部地址复核。此处 mock 掉 DNS 校验以确定性返回内部地址判定，断言连接被拒。
 */
@ExtendWith(MockitoExtension.class)
class McpClientServiceTest {

    @Mock
    private McpConfigService configService;

    @Mock
    private SecretCipher secretCipher;

    @Test
    void connectRejectsDnsResolvedInternalAddress() {
        SysMcpServerDO server = new SysMcpServerDO();
        server.setId(1L);
        server.setUrl("http://internal.example.com/sse");
        when(configService.requireEnabled(1L)).thenReturn(server);

        McpClientService clientService = new McpClientService(configService, new ObjectMapper(), secretCipher);

        try (MockedStatic<SsrfUrlValidator> validator = mockStatic(SsrfUrlValidator.class)) {
            validator.when(() -> SsrfUrlValidator.validateOutboundHttpUrlWithDns(anyString()))
                    .thenReturn("URL 主机解析到内部/保留地址");
            assertThatThrownBy(() -> clientService.listTools(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getCode())
                    .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        }
    }

    @Test
    void connectRejectsBlankUrl() {
        SysMcpServerDO server = new SysMcpServerDO();
        server.setId(2L);
        server.setUrl("  ");
        when(configService.requireEnabled(2L)).thenReturn(server);

        McpClientService clientService = new McpClientService(configService, new ObjectMapper(), secretCipher);

        assertThatThrownBy(() -> clientService.listTools(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("地址不能为空");
    }
}
