package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.UniqueKeyRelease;
import cn.admin.scaffold.common.annotation.FieldAudit;
import cn.admin.scaffold.module.system.dto.PostQuery;
import cn.admin.scaffold.module.system.dto.PostSaveRequest;
import cn.admin.scaffold.module.system.entity.SysPostDO;
import cn.admin.scaffold.module.system.mapper.SysPostMapper;
import cn.admin.scaffold.module.system.vo.PostOptionVo;
import cn.admin.scaffold.module.system.vo.PostVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemPostService {

    private static final int ENABLED = 1;
    private static final int DEFAULT_SORT = 0;

    private final SysPostMapper postMapper;

    public PageResult<PostVo> page(PostQuery query) {
        Page<SysPostDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysPostDO> wrapper = new LambdaQueryWrapper<SysPostDO>()
                .like(StringUtils.hasText(query.getPostCode()), SysPostDO::getPostCode, query.getPostCode())
                .like(StringUtils.hasText(query.getPostName()), SysPostDO::getPostName, query.getPostName())
                .eq(query.getStatus() != null, SysPostDO::getStatus, query.getStatus())
                .orderByAsc(SysPostDO::getSort)
                .orderByAsc(SysPostDO::getId);
        IPage<SysPostDO> result = postMapper.selectPage(page, wrapper);
        List<PostVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public List<PostOptionVo> options() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPostDO>()
                        .eq(SysPostDO::getStatus, ENABLED)
                        .orderByAsc(SysPostDO::getSort))
                .stream()
                .map(post -> PostOptionVo.builder()
                        .id(post.getId())
                        .postCode(post.getPostCode())
                        .postName(post.getPostName())
                        .build())
                .toList();
    }

    public Long create(PostSaveRequest request) {
        checkCodeUnique(request.getPostCode(), null);
        SysPostDO post = toEntity(request);
        postMapper.insert(post);
        return post.getId();
    }

    @FieldAudit(entity = SysPostDO.class, action = "UPDATE", module = "岗位管理")
    public void update(PostSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "岗位 ID 不能为空");
        }
        checkCodeUnique(request.getPostCode(), request.getId());
        postMapper.updateById(toEntity(request));
    }

    public void delete(Long id) {
        // 逻辑删除 + (tenant_id, post_code) 唯一键冲突——删除前释放编码唯一键
        SysPostDO post = postMapper.selectById(id);
        if (post != null) {
            post.setPostCode(UniqueKeyRelease.releaseCode(post.getPostCode()));
            postMapper.updateById(post);
        }
        postMapper.deleteById(id);
    }

    private void checkCodeUnique(String postCode, Long excludeId) {
        SysPostDO exists = postMapper.selectOne(new LambdaQueryWrapper<SysPostDO>()
                .eq(SysPostDO::getPostCode, postCode.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.POST_CODE_EXISTS);
        }
    }

    private SysPostDO toEntity(PostSaveRequest request) {
        SysPostDO post = new SysPostDO();
        post.setId(request.getId());
        post.setPostCode(request.getPostCode().trim());
        post.setPostName(request.getPostName());
        post.setSort(request.getSort() == null ? Integer.valueOf(DEFAULT_SORT) : request.getSort());
        post.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        post.setDescription(request.getDescription());
        return post;
    }

    private PostVo toVo(SysPostDO post) {
        return PostVo.builder()
                .id(post.getId())
                .postCode(post.getPostCode())
                .postName(post.getPostName())
                .sort(post.getSort())
                .status(post.getStatus())
                .description(post.getDescription())
                .createdAt(post.getCreatedAt())
                .build();
    }
}

