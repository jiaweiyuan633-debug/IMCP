package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.PostQuery;
import com.example.admin.module.system.dto.PostSaveRequest;
import com.example.admin.module.system.entity.SysPost;
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

    private final SysPostMapper postMapper;

    public PageResult<PostVo> page(PostQuery query) {
        Page<SysPost> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .like(StringUtils.hasText(query.getPostCode()), SysPost::getPostCode, query.getPostCode())
                .like(StringUtils.hasText(query.getPostName()), SysPost::getPostName, query.getPostName())
                .eq(query.getStatus() != null, SysPost::getStatus, query.getStatus())
                .orderByAsc(SysPost::getSort)
                .orderByAsc(SysPost::getId);
        IPage<SysPost> result = postMapper.selectPage(page, wrapper);
        List<PostVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public List<PostOptionVo> options() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPost>()
                        .eq(SysPost::getStatus, 1)
                        .orderByAsc(SysPost::getSort))
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
        SysPost post = toEntity(request);
        postMapper.insert(post);
        return post.getId();
    }

    public void update(PostSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "岗位 ID 不能为空");
        }
        checkCodeUnique(request.getPostCode(), request.getId());
        postMapper.updateById(toEntity(request));
    }

    public void delete(Long id) {
        postMapper.deleteById(id);
    }

    private void checkCodeUnique(String postCode, Long excludeId) {
        SysPost exists = postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, postCode.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.POST_CODE_EXISTS);
        }
    }

    private SysPost toEntity(PostSaveRequest request) {
        SysPost post = new SysPost();
        post.setId(request.getId());
        post.setPostCode(request.getPostCode().trim());
        post.setPostName(request.getPostName());
        post.setSort(request.getSort() == null ? 0 : request.getSort());
        post.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        post.setDescription(request.getDescription());
        return post;
    }

    private PostVo toVo(SysPost post) {
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

