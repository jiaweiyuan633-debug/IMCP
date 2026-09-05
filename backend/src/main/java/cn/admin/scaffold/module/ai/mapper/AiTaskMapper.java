package cn.admin.scaffold.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import cn.admin.scaffold.module.ai.entity.AiTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiTaskMapper extends BaseMapper<AiTaskDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, task_no, biz_type, biz_id, service_code, status,
                   params_json, error_msg, retry_count, max_retry, timeout_seconds,
                   callback_url, created_by, created_at, updated_at
            FROM ai_task WHERE task_no = #{taskNo}
            """)
    AiTaskDO selectByTaskNoIgnoreTenant(@Param("taskNo") String taskNo);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM ai_task")
    List<Long> selectTenantIds();

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT id, tenant_id, task_no, biz_type, biz_id, service_code, status,
                   params_json, error_msg, retry_count, max_retry, timeout_seconds,
                   callback_url, created_by, created_at, updated_at
            FROM ai_task
            WHERE status IN ('PENDING', 'QUEUED', 'RUNNING')
              AND updated_at &lt; #{threshold}
              AND tenant_id = #{tenantId}
            </script>
            """)
    List<AiTaskDO> selectTimeoutTasks(@Param("tenantId") Long tenantId, @Param("threshold") LocalDateTime threshold);
}

