package cn.admin.scaffold.module.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.system.entity.SysDictTypeDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictTypeDO> {

    /** 全局共享字典类型（tenant_id=0）：绕过租户拦截器，供所有租户复用。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM sys_dict_type
            WHERE tenant_id = 0 AND status = 1
            ORDER BY id
            """)
    List<SysDictTypeDO> selectSharedTypes();

    /** 全部共享字典类型（is_shared=1，含停用，供共享字典管理页分页/编辑）：绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM sys_dict_type
            WHERE is_shared = 1
            ORDER BY id
            """)
    List<SysDictTypeDO> selectSharedTypeAll();

    /** 按类型编码查字典类型（不区分租户，用于判断是否共享类型）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM sys_dict_type
            WHERE dict_type = #{dictType}
            LIMIT 1
            """)
    SysDictTypeDO selectByTypeIgnoreTenant(@Param("dictType") String dictType);

    /** 按 ID 查字典类型（不区分租户，供共享类型管理）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_dict_type WHERE id = #{id}")
    SysDictTypeDO selectByIdIgnoreTenant(@Param("id") Long id);

    /** 更新共享字典类型（tenant_id=0）：绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_dict_type
            SET dict_name = #{dictName}, dict_type = #{dictType}, status = #{status},
                is_shared = #{isShared}, remark = #{remark}
            WHERE id = #{id}
            """)
    int updateByIdIgnoreTenant(SysDictTypeDO type);

    /** 删除共享字典类型（tenant_id=0）：绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_dict_type WHERE id = #{id}")
    int deleteByIdIgnoreTenant(@Param("id") Long id);
}

