package com.example.admin.module.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleDeptMapper {

    @Insert("""
            <script>
            INSERT INTO sys_role_dept (role_id, dept_id) VALUES
            <foreach collection="deptIds" item="deptId" separator=",">
                (#{roleId}, #{deptId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}

