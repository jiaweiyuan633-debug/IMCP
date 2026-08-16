package {{package}}.vo;

import lombok.Builder;
import lombok.Data;
{{imports}}
import java.time.LocalDateTime;

/**
 * {{comment}}展示对象。
 */
@Data
@Builder
public class {{Entity}}Vo {

    private Long id;
[[for:fields_vo]]
{{item}}
[[/for]]
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
