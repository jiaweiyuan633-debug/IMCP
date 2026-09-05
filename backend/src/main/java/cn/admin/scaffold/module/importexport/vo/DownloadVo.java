package cn.admin.scaffold.module.importexport.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 导出结果下载视图对象。url 形如 /files/{id}?token=xxx，经既有文件 Token 机制访问。
 */
@Data
@Builder
public class DownloadVo {

    private String url;
    private String fileName;
}
