package cn.admin.scaffold.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.system.entity.SysAuditLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLogDO> {
}
