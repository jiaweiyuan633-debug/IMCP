package com.example.admin.module.importexport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.importexport.entity.ImportExportTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入导出模板 Mapper。
 */
@Mapper
public interface ImportExportTemplateMapper extends BaseMapper<ImportExportTemplateDO> {
}
