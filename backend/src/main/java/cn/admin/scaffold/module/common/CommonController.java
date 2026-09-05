package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.FileAccessService;
import cn.admin.scaffold.common.MessageBizType;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.common.vo.UploadResponse;
import cn.admin.scaffold.module.system.SystemMessageService;
import cn.admin.scaffold.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final FileStorageManager fileStorageManager;
    private final FileAccessService fileAccessService;
    private final StorageQuotaService storageQuotaService;
    private final SystemMessageService messageService;

    @GetMapping("/file-token")
    @PreAuthorize("isAuthenticated()")
    public Result<String> fileToken(@RequestParam String url) {
        // 签发/校验共用同一规范化口径（FileAccessService.normalizePath），防止 ; 矩阵参数等
        // 路径变体绕过 isAccessiblePath 白名单（与 FileAccessFilter 对齐）
        String normalized = FileAccessService.normalizePath(url);
        if (!isAccessiblePath(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "非法文件地址");
        }
        // 文件内容令牌签发前校验文件归属，防止为其他租户的文件签发访问令牌（跨租户文件读取）
        Long fileId = parseFileId(normalized);
        if (fileId != null) {
            fileStorageManager.getOwnedOrThrow(fileId);
        } else {
            // 历史 /uploads/{objectKey} 存量文件：按 URL 精确匹配 + 租户校验，与 /files/{id} 对齐——
            // 否则任何人登录即可为任意 objectKey 签发令牌，猜中对象键即可跨租户读历史文件
            fileStorageManager.getOwnedByLegacyUrlOrThrow(normalized);
        }
        return Result.success(fileAccessService.issue(normalized, SecurityUtils.getUserId()));
    }

    private Long parseFileId(String url) {
        if (url.startsWith("/files/")) {
            String idPart = url.substring("/files/".length());
            if (idPart.matches("\\d+")) {
                return Long.valueOf(idPart);
            }
        }
        return null;
    }

    @PostMapping("/upload")
    @OperLog(module = "文件管理", action = "上传文件")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String category) {
        UploadResponse response = fileStorageManager.store(file, category);
        notifyUploader(response);
        return Result.success(response);
    }

    private void notifyUploader(UploadResponse response) {
        Long userId = SecurityUtils.tryGetUserId();
        if (userId == null) {
            return;
        }
        messageService.sendSystemToUsers(List.of(userId), TenantContext.getTenantId(),
                "文件上传成功",
                "文件「" + response.getName() + "」已上传成功。",
                MessageBizType.FILE, response.getId());
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

