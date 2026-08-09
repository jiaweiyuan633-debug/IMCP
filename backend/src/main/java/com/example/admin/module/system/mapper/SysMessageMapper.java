package com.example.admin.module.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.module.system.entity.SysMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessageDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT m.*, CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS read_flag
            FROM sys_message m
            LEFT JOIN sys_message_read r ON r.message_id = m.id AND r.user_id = #{userId}
            WHERE m.deleted = 0
              AND m.tenant_id = #{tenantId}
              AND (m.receiver_id = #{userId} OR m.receiver_id IS NULL)
              AND (#{messageType} IS NULL OR m.message_type = #{messageType})
              AND (#{readStatus} IS NULL OR (CASE WHEN r.id IS NULL THEN 0 ELSE 1 END) = #{readStatus})
            ORDER BY m.id DESC
            """)
    IPage<SysMessageDO> selectMessagePage(Page<SysMessageDO> page,
                                          @Param("tenantId") Long tenantId,
                                          @Param("userId") Long userId,
                                          @Param("messageType") String messageType,
                                          @Param("readStatus") Integer readStatus);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT m.*, CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS read_flag
            FROM sys_message m
            LEFT JOIN sys_message_read r ON r.message_id = m.id AND r.user_id = #{userId}
            WHERE m.deleted = 0
              AND m.tenant_id = #{tenantId}
              AND (m.receiver_id = #{userId} OR m.receiver_id IS NULL)
            ORDER BY m.id DESC
            LIMIT #{limit}
            """)
    List<SysMessageDO> selectLatest(@Param("tenantId") Long tenantId,
                                    @Param("userId") Long userId,
                                    @Param("limit") int limit);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*)
            FROM sys_message m
            LEFT JOIN sys_message_read r ON r.message_id = m.id AND r.user_id = #{userId}
            WHERE m.deleted = 0
              AND m.tenant_id = #{tenantId}
              AND (m.receiver_id = #{userId} OR m.receiver_id IS NULL)
              AND r.id IS NULL
            """)
    long selectUnreadCount(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
