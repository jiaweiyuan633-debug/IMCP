package cn.admin.scaffold.module.report.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 通用「名称-数值」统计项，用于各类图表（饼图/柱状图/折线图）数据。 */
@Data
@AllArgsConstructor
public class NameValueVo {

    private String name;
    private long value;
}
