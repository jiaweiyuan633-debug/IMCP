package cn.admin.scaffold.module.importexport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 导入/导出任务创建请求。
 * <ul>
 *   <li>导入：{bizNo, templateCode, fileId}，fileId 为上传后的源文件 id（sys_file.id）；</li>
 *   <li>导出：{bizNo, templateCode, query}，query 为导出筛选参数（当前处理器按空参导出全量）。</li>
 * </ul>
 * bizNo 仅作幂等键（@Idempotent），不落库。
 */
@Data
public class JobCreateRequest {

    @NotBlank(message = "业务单号不能为空")
    @Size(max = 64, message = "业务单号长度不能超过 64")
    private String bizNo;

    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    private Long fileId;

    @Size(max = 255, message = "源文件名长度不能超过 255")
    private String fileName;

    private Map<String, Object> query;
}
