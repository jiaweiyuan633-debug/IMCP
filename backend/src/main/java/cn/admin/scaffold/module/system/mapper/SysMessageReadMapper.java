package cn.admin.scaffold.module.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.system.entity.SysMessageReadDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysMessageReadMapper extends BaseMapper<SysMessageReadDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT IGNORE INTO sys_message_read (tenant_id, message_id, user_id, read_time)
            VALUES (#{tenantId}, #{messageId}, #{userId}, NOW())
            """)
    int markRead(@Param("tenantId") Long tenantId,
                 @Param("messageId") Long messageId,
                 @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT IGNORE INTO sys_message_read (tenant_id, message_id, user_id, read_time)
            SELECT #{tenantId}, m.id, #{userId}, NOW()
            FROM sys_message m
            WHERE m.deleted = 0
              AND m.tenant_id = #{tenantId}
              AND (m.receiver_id = #{userId} OR m.receiver_id IS NULL)
            """)
    int markAllRead(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
