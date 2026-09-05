package cn.admin.scaffold.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_process_node")
public class SysProcessNodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long processDefId;
    private String nodeName;
    private String nodeKey;
    private String nodeType;
    private String conditionExpression;
    private Integer timeoutHours;
    private Integer nodeOrder;
    private Long approverRoleId;
    private LocalDateTime createdAt;
}
