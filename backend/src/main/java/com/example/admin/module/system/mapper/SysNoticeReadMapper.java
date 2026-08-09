package com.example.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.system.entity.SysNoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {

    @Insert("INSERT IGNORE INTO sys_notice_read (user_id, notice_id) VALUES (#{userId}, #{noticeId})")
    int markRead(@Param("userId") Long userId, @Param("noticeId") Long noticeId);
}

