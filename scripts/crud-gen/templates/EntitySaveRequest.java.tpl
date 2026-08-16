package {{package}}.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * {{comment}}新增/编辑请求。
 */
@Data
public class {{Entity}}SaveRequest {

    private Long id;
[[for:fields_request]]
{{item}}
[[/for]]
}
