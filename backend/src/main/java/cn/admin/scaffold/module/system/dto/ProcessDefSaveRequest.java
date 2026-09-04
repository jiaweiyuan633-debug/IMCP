package cn.admin.scaffold.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProcessDefSaveRequest {

    private Long id;

    @NotBlank(message = "流程名称不能为空")
    @Size(max = 100)
    private String defName;

    @NotBlank(message = "流程标识不能为空")
    @Size(max = 100)
    private String defKey;

    private String description;
    private Integer status;

    @NotEmpty(message = "至少需要一个审批节点")
    private List<NodeItem> nodes;

    @Data
    public static class NodeItem {
        private Long id;
        @NotBlank(message = "节点名称不能为空")
        private String nodeName;
        @NotBlank(message = "节点标识不能为空")
        private String nodeKey;
        private String nodeType;
        private String conditionExpression;
        private Integer timeoutHours;
        private Integer nodeOrder;
        private Long approverRoleId;
    }
}
