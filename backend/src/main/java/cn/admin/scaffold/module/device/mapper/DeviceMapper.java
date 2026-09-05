package cn.admin.scaffold.module.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.device.entity.DeviceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<DeviceDO> {
}
