package cn.admin.scaffold.module.importexport.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.importexport.entity.ImportExportJobDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 导入导出任务 Mapper。处理器轮询为跨租户扫描，需绕过租户拦截器（@InterceptorIgnore），
 * 与 AiTaskMapper 的跨租户定时扫描保持一致。
 */
@Mapper
public interface ImportExportJobMapper extends BaseMapper<ImportExportJobDO> {

    /**
     * 取一条待处理任务（跨租户，供定时处理器轮询）。deleted 逻辑删除需在原生 SQL 中自行过滤。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM import_export_job WHERE status = 'PENDING' AND deleted = 0 ORDER BY id LIMIT 1")
    ImportExportJobDO selectOnePendingIgnoreTenant();

    /**
     * 条件状态流转（CAS）：仅当当前状态等于 expectStatus 时才置为新状态，返回受影响行数。
     * 多副本并发轮询时，仅一个实例能把任务从 PENDING 抢到 PROCESSING，其余实例命中 0 行直接放弃。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE import_export_job
            SET status = #{status}, updated_at = NOW()
            WHERE id = #{id} AND status = #{expectStatus} AND deleted = 0
            """)
    int casStatus(@Param("id") Long id, @Param("expectStatus") String expectStatus,
                  @Param("status") String status);

    /**
     * 回收卡死的 PROCESSING 任务：处理进程崩溃/长时间无心跳后，updated_at 早于 cutoff 的
     * PROCESSING 任务重置为 PENDING 重新排队，避免状态永久卡死、任务永不执行。
     * 跨租户扫描与轮询一致，需绕过租户拦截器。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE import_export_job
            SET status = 'PENDING', updated_at = NOW()
            WHERE status = 'PROCESSING' AND updated_at < #{cutoff} AND deleted = 0
            """)
    int recycleStaleProcessing(@Param("cutoff") LocalDateTime cutoff);
}
