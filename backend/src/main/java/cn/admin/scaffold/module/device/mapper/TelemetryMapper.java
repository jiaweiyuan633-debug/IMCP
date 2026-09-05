package cn.admin.scaffold.module.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.device.entity.TelemetryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TelemetryMapper extends BaseMapper<TelemetryDO> {
}
