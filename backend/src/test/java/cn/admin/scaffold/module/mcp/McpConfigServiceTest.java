package cn.admin.scaffold.module.mcp;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.module.mcp.dto.McpServerSaveRequest;
import cn.admin.scaffold.module.mcp.entity.SysMcpServerDO;
import cn.admin.scaffold.module.mcp.mapper.SysMcpServerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP Server 配置安全单测。
 *
 * <p>覆盖两点：①保存时静态 SSRF 校验（URL 指向内网/保留 IP 字面量直接拒绝，不落库）；
 * ②authToken 落库加密（SecretCipher，编辑留空保留原密文）。
 */
@ExtendWith(MockitoExtension.class)
class McpConfigServiceTest {

    @Mock
    private SysMcpServerMapper mcpServerMapper;

    @Mock
    private SecretCipher secretCipher;

    @InjectMocks
    private McpConfigService mcpConfigService;

    @Test
    void createEncryptsAuthToken() {
        when(secretCipher.isEncrypted("tok-123")).thenReturn(false);
        when(secretCipher.encrypt("tok-123")).thenReturn("enc:t");

        mcpConfigService.create(request(null, "http://mcp.example.com/sse", "tok-123"));

        ArgumentCaptor<SysMcpServerDO> captor = ArgumentCaptor.forClass(SysMcpServerDO.class);
        verify(mcpServerMapper).insert(captor.capture());
        assertEquals("enc:t", captor.getValue().getAuthToken());
    }

    @Test
    void createRejectsInternalIpUrl() {
        McpServerSaveRequest req = request(null, "http://192.168.1.1:8080/sse", "tok");
        // 静态校验拒绝 IP 字面量（不依赖 DNS），内网地址不得落库
        assertThatThrownBy(() -> mcpConfigService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP Server 地址不合法");
        verify(mcpServerMapper, never()).insert(any(SysMcpServerDO.class));
    }

    @Test
    void createRejectsLoopbackUrl() {
        McpServerSaveRequest req = request(null, "http://localhost:3000/sse", "tok");
        assertThatThrownBy(() -> mcpConfigService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP Server 地址不合法");
        verify(mcpServerMapper, never()).insert(any(SysMcpServerDO.class));
    }

    @Test
    void updateKeepsExistingTokenWhenBlank() {
        SysMcpServerDO existing = new SysMcpServerDO();
        existing.setId(1L);
        existing.setAuthToken("enc:old");
        when(mcpServerMapper.selectById(1L)).thenReturn(existing);

        McpServerSaveRequest req = request(1L, "http://mcp.example.com/sse", null);
        mcpConfigService.update(req);

        ArgumentCaptor<SysMcpServerDO> captor = ArgumentCaptor.forClass(SysMcpServerDO.class);
        verify(mcpServerMapper).updateById(captor.capture());
        assertEquals("enc:old", captor.getValue().getAuthToken());
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    void updateRejectsInternalIpUrl() {
        McpServerSaveRequest req = request(1L, "http://10.0.0.5/sse", "tok");
        assertThatThrownBy(() -> mcpConfigService.update(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP Server 地址不合法");
        verify(mcpServerMapper, never()).updateById(any(SysMcpServerDO.class));
    }

    private McpServerSaveRequest request(Long id, String url, String authToken) {
        McpServerSaveRequest request = new McpServerSaveRequest();
        request.setId(id);
        request.setName("测试服务");
        request.setUrl(url);
        request.setAuthToken(authToken);
        request.setEnabled(1);
        request.setSort(0);
        return request;
    }
}
