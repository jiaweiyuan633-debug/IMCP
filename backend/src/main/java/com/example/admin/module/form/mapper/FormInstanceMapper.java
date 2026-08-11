package com.example.admin.module.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.form.entity.FormInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 表单提交记录 Mapper。审批为条件状态流转（CAS），form_instance 不设 version 乐观锁
 * （V50 设计），用 WHERE status = 'SUBMITTED' 原子化抢占，杜绝并发重复审批。
 * 租户隔离由 TenantLineInnerInterceptor 对原生 SQL 自动注入 tenant_id。
 */
@Mapper
public interface FormInstanceMapper extends BaseMapper<FormInstanceDO> {

    /**
     * 审批条件状态流转（CAS）：仅当记录处于 SUBMITTED 时才流转到目标状态，返回受影响行数。
     * 并发审批时仅一个请求能命中 1 行，其余请求命中 0 行由服务层拒绝，保证状态机单向流转。
     */
    @Update("""
            UPDATE form_instance
            SET status = #{status}, updated_at = NOW()
            WHERE id = #{id} AND status = 'SUBMITTED' AND deleted = 0
            """)
    int casStatus(@Param("id") Long id, @Param("status") String status);
}
