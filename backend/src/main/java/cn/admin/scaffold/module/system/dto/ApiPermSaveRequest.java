package cn.admin.scaffold.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiPermSaveRequest {

    private Long id;

    @NotBlank(message = "HTTP 方法不能为空")
    @Pattern(regexp = "^(GET|POST|PUT|DELETE|PATCH|\\*)$", message = "非法 HTTP 方法")
    private String method;

    @NotBlank(message = "路径模式不能为空")
    @Size(max = 200, message = "路径模式长度不能超过 200")
    private String pathPattern;

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过 100")
    private String permCode;

    private Integer enabled;

    private String remark;
}
