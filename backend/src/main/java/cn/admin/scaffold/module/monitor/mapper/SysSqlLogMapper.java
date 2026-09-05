package cn.admin.scaffold.module.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.monitor.entity.SysSqlLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysSqlLogMapper extends BaseMapper<SysSqlLogDO> {
}

