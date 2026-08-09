package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.system.entity.SysAuditLog;
import com.example.admin.module.system.mapper.SysAuditLogMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorAuditService {

    private final SysAuditLogMapper auditLogMapper;

    public PageResult<SysAuditLog> page(long pageNum, long pageSize, String module, Integer status) {
        Page<SysAuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<SysAuditLog>()
                .like(StringUtils.hasText(module), SysAuditLog::getModule, module)
                .eq(status != null, SysAuditLog::getStatus, status)
                .orderByDesc(SysAuditLog::getId);
        IPage<SysAuditLog> result = auditLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        String fileName = URLEncoder.encode("audit-log.csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        List<SysAuditLog> records = auditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLog>()
                .orderByDesc(SysAuditLog::getId)
                .last("LIMIT 10000"));
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write("id,module,action,userId,status,createdAt\n");
            for (SysAuditLog record : records) {
                writer.write(String.join(",",
                        String.valueOf(record.getId()),
                        safe(record.getModule()),
                        safe(record.getAction()),
                        record.getUserId() == null ? "" : String.valueOf(record.getUserId()),
                        String.valueOf(record.getStatus()),
                        safe(String.valueOf(record.getCreatedAt()))) + "\n");
            }
            writer.flush();
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
