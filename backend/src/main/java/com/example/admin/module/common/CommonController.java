package com.example.admin.module.common;

import com.example.admin.common.BusinessException;
import com.example.admin.common.Result;
import com.example.admin.common.ResultCode;
import com.example.admin.module.common.vo.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip");

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件大小不能超过 20MB");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的文件类型");
        }
        byte[] head = file.getBytes();
        if (head.length > 0 && !isAllowedContent(head, extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件内容与扩展名不匹配");
        }

        return Result.success(fileStorageService.store(file));
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
}

