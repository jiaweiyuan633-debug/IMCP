package cn.admin.scaffold.module.device.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 物模型 schema 视图：把 properties/events/services JSON 反序列化为列表，供设备配置 UI 渲染。
 * properties: [{key,name,dataType,unit,mode}]；events/services: [{key,name,params:[...]}]。
 */
@Data
@Builder
public class ThingModelSchemaVo {

    private List<Map<String, Object>> properties;
    private List<Map<String, Object>> events;
    private List<Map<String, Object>> services;
}
