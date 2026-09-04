package cn.admin.scaffold.module.importexport.handler;

import java.util.List;
import java.util.Map;

/**
 * 导入导出处理器 SPI：按目标实体（entityKey）路由，使导入导出中心可扩展。
 *
 * <p>行数据统一以 Map 承载：key 为模板 config_json.columns[].key（映射到目标实体字段），
 * value 为单元格字符串（导入）或实体属性值（导出）。配置列由
 * {@link cn.admin.scaffold.module.importexport.ImportExportTemplateService#parseColumns} 解析。
 */
public interface ImportExportHandler {

    /** 目标实体标识，用于路由（如 dict-data）。 */
    String entityKey();

    /**
     * 按查询参数导出数据行。
     *
     * @param queryParams 导出筛选参数（当前无落库，处理器轮询时传空 Map）
     * @param configJson  模板列映射配置 JSON
     * @return 数据行，每行 key 与 config_json.columns[].key 对应
     */
    List<Map<String, Object>> export(Map<String, Object> queryParams, String configJson);

    /**
     * 导入数据行，返回成功行数；任一行校验/落库失败抛异常整批失败（任务置 FAILED）。
     */
    int importRows(List<Map<String, Object>> rows, String configJson);
}
