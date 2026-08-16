package com.example.admin.module.ai;

import com.example.admin.common.BusinessException;
import com.example.admin.common.SseTicketService;
import com.example.admin.module.ai.dto.AiCallbackRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI 回调请求体有界读单测（R4-1.44 批次17）。
 *
 * <p>{@code /api/ai/callback/**} 为 permitAll 且 HMAC 校验发生在整读之后，原
 * {@code readAllBytes()} 无界读入可被未认证者用任意巨大 body 打 OOM；改为
 * {@code readNBytes(MAX+1)} 后超限在进入业务逻辑前即拒绝。
 */
class AiControllerTest {

    private final AiTaskService taskService = mock(AiTaskService.class);
    private final AiController controller = new AiController(
            mock(AiConfigService.class),
            taskService,
            mock(AiTaskStreamService.class),
            mock(SseTicketService.class),
            new ObjectMapper());

    @Test
    void callbackRejectsOversizeBodyBeforeHmac() {
        byte[] oversized = new byte[1024 * 1024 + 1];
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(oversized);

        assertThatThrownBy(() -> controller.callback(request, "ts", "sig"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过大");
        verify(taskService, never()).handleCallback(any(), any(), eq("ts"), eq("sig"));
    }

    @Test
    void callbackParsesBoundedBodyAndDelegates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // AiCallbackRequest 字段为 snake_case（task_no），非驼峰 taskNo
        request.setContent("{\"task_no\":\"T1\",\"status\":\"SUCCEEDED\"}".getBytes());

        assertThat(controller.callback(request, "ts", "sig")).isNotNull();
        verify(taskService).handleCallback(any(AiCallbackRequest.class), any(byte[].class), eq("ts"), eq("sig"));
    }
}
