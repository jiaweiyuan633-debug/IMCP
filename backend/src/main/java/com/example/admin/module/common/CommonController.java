package com.example.admin.module.common;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.example.admin.common.BusinessException;
import com.example.admin.common.Result;
import com.example.admin.common.ResultCode;
import com.example.admin.module.common.vo.UploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Set;

@RestController
@RequestMapping("/api/common")
public class CommonController {

    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip");

    @Value("${app.upload-path:uploads}")
    private String uploadPath;

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

        String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
        String filename = IdUtil.fastSimpleUUID() + "." + extension;
        Path dir = Paths.get(uploadPath, datePath);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        file.transferTo(target.toAbsolutePath().toFile());

        String url = "/uploads/" + datePath + "/" + filename;
        return Result.success(UploadResponse.builder()
                .url(url)
                .name(originalName)
                .size(file.getSize())
                .build());
    }
}

