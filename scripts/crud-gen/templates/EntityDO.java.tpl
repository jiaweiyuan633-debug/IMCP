package {{package}}.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
{{imports}}
import java.time.LocalDateTime;

/**
 * {{comment}}实体。租户隔离（tenant_id 由拦截器自动注入），逻辑删除 + 乐观锁（version）。
 */
@Data
@TableName("{{table}}")
public class {{Entity}}DO {

    @TableId(type = IdType.AUTO)
    private Long id;
[[for:fields_do]]
{{item}}
[[/for]]
    private Long tenantId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
