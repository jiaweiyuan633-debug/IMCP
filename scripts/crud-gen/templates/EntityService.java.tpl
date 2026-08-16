package {{package}};

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import {{package}}.dto.{{Entity}}Query;
import {{package}}.dto.{{Entity}}SaveRequest;
import {{package}}.entity.{{Entity}}DO;
import {{package}}.mapper.{{Entity}}Mapper;
import {{package}}.vo.{{Entity}}Vo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {{comment}}服务：分页查询、详情、新增、编辑、删除。
 */
@Service
@RequiredArgsConstructor
public class {{Entity}}Service {

    private final {{Entity}}Mapper {{entity}}Mapper;

    public PageResult<{{Entity}}Vo> page({{Entity}}Query query) {
        Page<{{Entity}}DO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<{{Entity}}DO> wrapper = new LambdaQueryWrapper<{{Entity}}DO>()
[[for:where_lines]]{{item}}
[[/for]]                .orderByDesc({{Entity}}DO::getId);
        IPage<{{Entity}}DO> result = {{entity}}Mapper.selectPage(page, wrapper);
        List<{{Entity}}Vo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public {{Entity}}Vo getById(Long id) {
        return toVo(requireById(id));
    }

    public Long create({{Entity}}SaveRequest request) {
        {{Entity}}DO entity = toEntity(request);
        {{entity}}Mapper.insert(entity);
        return entity.getId();
    }

    public void update({{Entity}}SaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "{{comment}} ID 不能为空");
        }
        {{Entity}}DO entity = toEntity(request);
        {{entity}}Mapper.updateById(entity);
    }

    public void delete(Long id) {
        {{entity}}Mapper.deleteById(id);
    }

    private {{Entity}}DO requireById(Long id) {
        {{Entity}}DO entity = {{entity}}Mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return entity;
    }

    private {{Entity}}DO toEntity({{Entity}}SaveRequest request) {
        {{Entity}}DO entity = new {{Entity}}DO();
        entity.setId(request.getId());
[[for:set_lines]]{{item}}
[[/for]]        return entity;
    }

    private {{Entity}}Vo toVo({{Entity}}DO entity) {
        return {{Entity}}Vo.builder()
[[for:vo_set_lines]]{{item}}
[[/for]]                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
