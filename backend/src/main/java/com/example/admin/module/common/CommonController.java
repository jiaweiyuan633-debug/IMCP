package com.example.admin.module.common;

import com.example.admin.common.BusinessException;
import com.example.admin.common.Result;
import com.example.admin.common.ResultCode;
import com.example.admin.common.FileAccessService;
import com.example.admin.module.common.vo.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final FileStorage fileStorage;
    private final FileAccessService fileAccessService;

    @Value("${app.upload.max-size-mb:20}")
    private long maxSizeMb;

    @Value("${app.upload.allowed-extensions:jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,txt,zip}")
    private String allowedExtensions;

    @GetMapping("/file-token")
    @PreAuthorize("isAuthenticated()")
    public Result<String> fileToken(@RequestParam String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "非法文件地址");
        }
        return Result.success(fileAccessService.issue(url));
    }

    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        if (file.getSize() > maxSizeMb * 1024 * 1024) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件大小不能超过 " + maxSizeMb + "MB");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!allowedExtensionSet().contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的文件类型");
        }
        byte[] head = file.getBytes();
        if (head.length > 0 && !isAllowedContent(head, extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件内容与扩展名不匹配");
        }

        return Result.success(fileStorage.store(file));
    }

    private boolean isAllowedContent(byte[] head, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> head.length >= 3
                    && (head[0] & 0xFF) == 0xFF
                    && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            case "png" -> head.length >= 8
                    && (head[0] & 0xFF) == 0x89
                    && head[1] == 'P'
                    && head[2] == 'N'
                    && head[3] == 'G';
            case "gif" -> head.length >= 4
                    && head[0] == 'G'
                    && head[1] == 'I'
                    && head[2] == 'F'
                    && head[3] == '8';
            case "pdf" -> head.length >= 4
                    && head[0] == '%'
                    && head[1] == 'P'
                    && head[2] == 'D'
                    && head[3] == 'F';
            case "zip" -> head.length >= 2
                    && head[0] == 'P'
                    && head[1] == 'K';
            default -> true;
        };
    }

    private Set<String> allowedExtensionSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }
}

