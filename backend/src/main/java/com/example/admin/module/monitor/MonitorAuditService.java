package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.system.entity.SysAuditLogDO;
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

    public PageResult<SysAuditLogDO> page(long pageNum, long pageSize, String module, Integer status) {
        Page<SysAuditLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAuditLogDO> wrapper = new LambdaQueryWrapper<SysAuditLogDO>()
                .like(StringUtils.hasText(module), SysAuditLogDO::getModule, module)
                .eq(status != null, SysAuditLogDO::getStatus, status)
                .orderByDesc(SysAuditLogDO::getId);
        IPage<SysAuditLogDO> result = auditLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        String fileName = URLEncoder.encode("audit-log.csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        List<SysAuditLogDO> records = auditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLogDO>()
                .orderByDesc(SysAuditLogDO::getId)
                .last("LIMIT 10000"));
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write("\uFEFF");
            writer.write("id,module,action,userId,status,createdAt\n");
            for (SysAuditLogDO record : records) {
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
