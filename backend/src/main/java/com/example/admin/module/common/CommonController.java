package com.example.admin.module.common;

import com.example.admin.common.BusinessException;
import com.example.admin.common.FileAccessService;
import com.example.admin.common.Result;
import com.example.admin.common.ResultCode;
import com.example.admin.module.common.vo.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final FileStorageManager fileStorageManager;
    private final FileAccessService fileAccessService;
    private final StorageQuotaService storageQuotaService;

    @GetMapping("/file-token")
    @PreAuthorize("isAuthenticated()")
    public Result<String> fileToken(@RequestParam String url) {
        if (!isAccessiblePath(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "非法文件地址");
        }
        return Result.success(fileAccessService.issue(url));
    }

    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String category) {
        return Result.success(fileStorageManager.store(file, category));
    }

    @GetMapping("/storage-quota")
    @PreAuthorize("isAuthenticated()")
    public Result<StorageQuotaVo> storageQuota() {
        return Result.success(storageQuotaService.usage());
    }

    private boolean isAccessiblePath(String url) {
        if (url == null) {
            return false;
        }
        return url.startsWith("/uploads/") || url.matches("/files/\\d+");
    }
}

