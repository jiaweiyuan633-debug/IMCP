package cn.admin.scaffold.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 大屏模板保存请求：id 为空表示新增（内置模板仅可另存为新模板，不可直接覆盖）。
 * code 可选，缺省由服务端按时间戳生成。
 */
@Data
public class ScreenTemplateSaveRequest {

    private Long id;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String code;

    /** 分类：comprehensive/device/operation */
    private String category;

    private String theme = "dark";

    @NotBlank(message = "布局配置不能为空")
    private String layout;

    private String remark;
}
