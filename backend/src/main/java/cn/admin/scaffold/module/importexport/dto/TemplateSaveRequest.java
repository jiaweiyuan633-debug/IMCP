package cn.admin.scaffold.module.importexport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 导入导出模板新增/编辑请求。type 取值 import/export 由 Service 校验（PARAM_ERROR），
 * config_json 为列映射配置：{columns:[{key,header,required,dataType}],sheetName}。
 */
@Data
public class TemplateSaveRequest {

    private Long id;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过 100")
    private String name;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过 64")
    private String code;

    @NotBlank(message = "模板类型不能为空")
    @Size(max = 16, message = "模板类型长度不能超过 16")
    private String type;

    @NotBlank(message = "目标实体不能为空")
    @Size(max = 64, message = "目标实体长度不能超过 64")
    private String entityKey;

    @NotBlank(message = "模板列配置不能为空")
    private String configJson;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    private Integer status;

    /** 乐观锁版本号：编辑时由列表/详情回传，冲突时服务端拒绝覆盖；新增不传。 */
    private Integer version;
}
