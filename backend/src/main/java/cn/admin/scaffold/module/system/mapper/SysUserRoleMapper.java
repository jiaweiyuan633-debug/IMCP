package cn.admin.scaffold.module.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

import java.util.List;
import java.util.Map;
import java.util.Collection;

@Mapper
public interface SysUserRoleMapper {

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT user_id, role_id FROM sys_user_role
            WHERE user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            </script>
            """)
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectByUserIds(@Param("userIds") Collection<Long> userIds);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT DISTINCT user_id FROM sys_user_role
            WHERE role_id IN
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
            </foreach>
            </script>
            """)
    List<Long> selectUserIdsByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}

