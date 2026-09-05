package cn.admin.scaffold.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档（可整篇或按分块存储，chunk_index 标记分块序号）。 */
@Data
@TableName("ai_knowledge_doc")
public class AiKnowledgeDocDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long baseId;
    private String title;
    private String content;
    private Integer chunkIndex;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
