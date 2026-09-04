package cn.admin.scaffold.module.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.system.entity.SysDictDataDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictDataDO> {

    /** 共享字典数据（tenant_id=0）：绕过租户拦截器，作为租户覆盖模型的基础层。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM sys_dict_data
            WHERE dict_type = #{dictType} AND status = 1 AND tenant_id = 0
            ORDER BY dict_sort, id
            """)
    List<SysDictDataDO> selectSharedByType(@Param("dictType") String dictType);

    /** 全部共享字典数据（tenant_id=0）：绕过租户拦截器，供全量导出合并共享层。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM sys_dict_data
            WHERE tenant_id = 0
            ORDER BY dict_sort, id
            """)
    List<SysDictDataDO> selectAllShared();

    /** 删除共享字典数据（tenant_id=0），供共享类型删除时清理，绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_dict_data WHERE dict_type = #{dictType} AND tenant_id = 0")
    int deleteSharedByType(@Param("dictType") String dictType);

    /** 按 ID 查共享字典数据（不区分租户）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_dict_data WHERE id = #{id}")
    SysDictDataDO selectByIdIgnoreTenant(@Param("id") Long id);

    /** 更新共享字典数据（tenant_id=0）：绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_dict_data
            SET dict_type = #{dictType}, dict_label = #{dictLabel}, dict_value = #{dictValue},
                dict_sort = #{dictSort}, list_class = #{listClass}, is_default = #{isDefault},
                status = #{status}, remark = #{remark}
            WHERE id = #{id}
            """)
    int updateByIdIgnoreTenant(SysDictDataDO data);

    /** 删除共享字典数据（tenant_id=0）：绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_dict_data WHERE id = #{id}")
    int deleteByIdIgnoreTenant(@Param("id") Long id);
}

