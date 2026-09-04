package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.common.dto.PresignConfirmRequest;
import cn.admin.scaffold.module.common.dto.PresignUploadRequest;
import cn.admin.scaffold.module.common.vo.PresignUploadResponse;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预签名直传/直链端点（批次2c）。
 */
@RestController
@RequestMapping("/api/common/file/presign")
@RequiredArgsConstructor
public class PresignedFileController {

    private final PresignedFileService presignedFileService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public Result<PresignUploadResponse> createUpload(@Valid @RequestBody PresignUploadRequest request) {
        return Result.success(presignedFileService.createUpload(request));
    }

    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    @OperLog(module = "文件管理", action = "预签名直传确认")
    public Result<UploadResponse> confirm(@Valid @RequestBody PresignConfirmRequest request) {
        return Result.success(presignedFileService.confirm(request));
    }

    @GetMapping("/download/{id}")
    @PreAuthorize("hasAuthority('system:file:list')")
    public Result<String> download(@PathVariable Long id) {
        return Result.success(presignedFileService.createDownload(id));
    }
}
