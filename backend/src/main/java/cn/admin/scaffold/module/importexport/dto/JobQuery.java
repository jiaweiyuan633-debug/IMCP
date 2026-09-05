package cn.admin.scaffold.module.importexport.dto;

import lombok.Data;

/**
 * 导入导出任务分页查询参数。
 */
@Data
public class JobQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String templateCode;
    private String type;
    private String status;
}
