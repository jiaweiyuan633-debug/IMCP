package com.example.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.system.entity.SysNoticeRead;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO sys_notice_read (tenant_id, user_id, notice_id) VALUES (#{tenantId}, #{userId}, #{noticeId})")
    int markRead(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("noticeId") Long noticeId);
}

