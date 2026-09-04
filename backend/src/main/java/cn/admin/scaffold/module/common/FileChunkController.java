package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.common.dto.ChunkInitRequest;
import cn.admin.scaffold.module.common.vo.ChunkInitResponse;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传 + 秒传端点（批次2c）。
 */
@RestController
@RequestMapping("/api/common/file/chunk")
@RequiredArgsConstructor
public class FileChunkController {

    private final ChunkFileService chunkFileService;

    @PostMapping("/init")
    @PreAuthorize("isAuthenticated()")
    public Result<ChunkInitResponse> init(@Valid @RequestBody ChunkInitRequest request) {
        return Result.success(chunkFileService.init(request));
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    @OperLog(module = "文件管理", action = "分片上传")
    public Result<Void> upload(@RequestParam String uploadId,
                               @RequestParam int index,
                               @RequestParam("file") MultipartFile file) {
        chunkFileService.uploadChunk(uploadId, index, file);
        return Result.success();
    }

    @PostMapping("/complete")
    @PreAuthorize("isAuthenticated()")
    @OperLog(module = "文件管理", action = "分片上传完成")
    public Result<UploadResponse> complete(@RequestParam String uploadId) {
        return Result.success(chunkFileService.complete(uploadId));
    }
}
