package {{package}}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import {{package}}.entity.{{Entity}}DO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface {{Entity}}Mapper extends BaseMapper<{{Entity}}DO> {
}
