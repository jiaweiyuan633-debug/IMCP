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
public interface SysUserPostMapper {

    @Insert("INSERT INTO sys_user_post (user_id, post_id) VALUES (#{userId}, #{postId})")
    int insert(@Param("userId") Long userId, @Param("postId") Long postId);

    @Delete("DELETE FROM sys_user_post WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT user_id, post_id FROM sys_user_post
            WHERE user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            </script>
            """)
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectByUserIds(@Param("userIds") Collection<Long> userIds);
}

