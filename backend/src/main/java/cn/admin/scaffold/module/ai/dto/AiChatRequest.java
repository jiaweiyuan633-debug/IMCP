package cn.admin.scaffold.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatRequest {

    /** 模型服务配置 code（ai_service_config.code） */
    @NotBlank(message = "模型服务不能为空")
    private String serviceCode;

    /** 可选：覆盖配置中的模型名称 */
    private String model;

    /** 可选：先按 Prompt 模板编码渲染出 system 指令，再拼接到消息列表前 */
    private String templateCode;

    /** 模板渲染参数 */
    private Map<String, Object> templateParams;

    /** 可选：启用 RAG 知识检索，检索片段注入上下文 */
    private Boolean useKnowledge;

    /** 知识库 ID（useKnowledge 时必填） */
    private Long knowledgeBaseId;

    /** 检索 TOP K */
    private Integer topK;

    private Double temperature;

    @Size(max = 20, message = "消息数量不能超过 20")
    private List<ChatMessage> messages;

    /** 对话消息 */
    @Data
    public static class ChatMessage {

        @NotBlank(message = "消息角色不能为空")
        private String role;

        @NotBlank(message = "消息内容不能为空")
        private String content;
    }
}
