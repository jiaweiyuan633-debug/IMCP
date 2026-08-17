package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.UniqueKeyRelease;
import com.example.admin.common.annotation.FieldAudit;
import com.example.admin.module.system.dto.PostQuery;
import com.example.admin.module.system.dto.PostSaveRequest;
import com.example.admin.module.system.entity.SysPostDO;
import com.example.admin.module.system.mapper.SysPostMapper;
import com.example.admin.module.system.vo.PostOptionVo;
import com.example.admin.module.system.vo.PostVo;
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
        // 批次4（R4-1.50）：逻辑删除 + (tenant_id, post_code) 唯一键冲突——删除前释放编码唯一键
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
        post.setSort(request.getSort() == null ? DEFAULT_SORT : request.getSort());
        post.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
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

