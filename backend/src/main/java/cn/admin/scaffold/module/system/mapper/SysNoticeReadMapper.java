package cn.admin.scaffold.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.system.entity.SysNoticeReadDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeReadDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO sys_notice_read (tenant_id, user_id, notice_id) VALUES (#{tenantId}, #{userId}, #{noticeId})")
    int markRead(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("noticeId") Long noticeId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT IGNORE INTO sys_notice_read (tenant_id, user_id, notice_id, read_time)
            SELECT #{tenantId}, #{userId}, id, NOW()
            FROM sys_notice
            WHERE status = 1 AND tenant_id = #{tenantId}
            """)
    int markAllRead(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}

