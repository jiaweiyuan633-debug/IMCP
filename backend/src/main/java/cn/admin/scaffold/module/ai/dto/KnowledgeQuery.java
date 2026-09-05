package cn.admin.scaffold.module.ai.dto;

import lombok.Data;

@Data
public class KnowledgeQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String name;
    private Long baseId;
}
