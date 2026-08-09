package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.PostQuery;
import com.example.admin.module.system.dto.PostSaveRequest;
import com.example.admin.module.system.vo.PostOptionVo;
import com.example.admin.module.system.vo.PostVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/post")
@RequiredArgsConstructor
public class SystemPostController {

    private final SystemPostService postService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:post:list')")
    public Result<PageResult<PostVo>> page(PostQuery query) {
        return Result.success(postService.page(query));
    }

    @GetMapping("/options")
    public Result<List<PostOptionVo>> options() {
        return Result.success(postService.options());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:post:add')")
    @OperLog(module = "岗位管理", action = "新增岗位")
    public Result<Long> create(@Valid @RequestBody PostSaveRequest request) {
        return Result.success(postService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:post:edit')")
    @OperLog(module = "岗位管理", action = "编辑岗位")
    public Result<Void> update(@Valid @RequestBody PostSaveRequest request) {
        postService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:delete')")
    @OperLog(module = "岗位管理", action = "删除岗位")
    public Result<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return Result.success();
    }
}

