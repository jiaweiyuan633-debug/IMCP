package {{package}}.dto;

import lombok.Data;

/**
 * {{comment}}分页查询参数。
 */
@Data
public class {{Entity}}Query {

    private long pageNum = 1;
    private long pageSize = 10;
[[for:fields_query]]
{{item}}
[[/for]]
}
