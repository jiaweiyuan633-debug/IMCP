package cn.admin.scaffold.module.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.module.device.entity.DeviceDO;
import cn.admin.scaffold.module.device.mapper.DeviceMapper;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 平台 MCP Server 工具集：聚合内置平台工具与外部 {@link PlatformToolProvider} 扩展工具，
 * 供 Claude Desktop 等外部 MCP 客户端安全调用。
 *
 * <p>内置工具为只读查询（用户 / 设备 / 平台统计）；新增工具请实现
 * {@link PlatformToolProvider} 并注册为 Spring Bean，将自动聚合到本集合。
 */
@Component
@RequiredArgsConstructor
public class McpPlatformTools {

    private final SysUserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;
    private final List<PlatformToolProvider> externalToolProviders;

    public List<SyncToolSpecification> toolSpecifications() {
        List<SyncToolSpecification> specs = new ArrayList<>(platformTools());
        externalToolProviders.forEach(provider -> specs.addAll(provider.toolSpecifications()));
        return specs;
    }

    /** 内置平台只读工具（本类自持，避免工具逻辑散落）。 */
    private List<SyncToolSpecification> platformTools() {
        return List.of(
                tool("list_users", "查询用户列表", "分页查询平台用户，可带用户名/昵称关键词过滤",
                        List.of(
                                schemaField("pageNum", "integer", "页码，默认 1"),
                                schemaField("pageSize", "integer", "每页条数，默认 20"),
                                schemaField("keyword", "string", "用户名/昵称关键词")),
                        this::listUsers),
                tool("get_user", "查询用户详情", "按用户 ID 或用户名查询单个用户",
                        List.of(
                                schemaField("userId", "integer", "用户 ID"),
                                schemaField("username", "string", "用户名")),
                        this::getUser),
                tool("list_devices", "查询设备列表", "分页查询平台设备，可带状态过滤",
                        List.of(
                                schemaField("pageNum", "integer", "页码，默认 1"),
                                schemaField("pageSize", "integer", "每页条数，默认 20"),
                                schemaField("status", "integer", "设备状态 1启用 0停用")),
                        this::listDevices),
                tool("get_platform_stats", "获取平台统计", "统计平台用户数、设备数等只读指标", List.of(), this::platformStats));
    }

    // ---------- 工具定义 ----------

    private SyncToolSpecification tool(String name, String title, String description,
                                       List<Map<String, Object>> fields,
                                       BiFunction<McpSyncServerExchange, Map<String, Object>, CallToolResult> handler) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            properties.put(String.valueOf(field.get("name")), field.get("schema"));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(schema);
        } catch (Exception exception) {
            schemaJson = "{\"type\":\"object\"}";
        }
        Tool tool = Tool.builder()
                .name(name)
                .title(title)
                .description(description)
                .inputSchema(schemaJson)
                .build();
        return new SyncToolSpecification(tool, handler);
    }

    private Map<String, Object> schemaField(String name, String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return Map.of("name", name, "schema", schema);
    }

    // ---------- 工具实现 ----------

    private CallToolResult listUsers(McpSyncServerExchange exchange, Map<String, Object> args) {
        try {
            long pageNum = longArg(args, "pageNum", 1L);
            long pageSize = Math.min(longArg(args, "pageSize", 20L), 100L);
            String keyword = stringArg(args, "keyword");
            Page<SysUserDO> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<SysUserDO>()
                    .and(StringUtils.hasText(keyword), w -> w
                            .like(SysUserDO::getUsername, keyword)
                            .or().like(SysUserDO::getNickname, keyword))
                    .orderByDesc(SysUserDO::getId);
            IPage<SysUserDO> result = userMapper.selectPage(page, wrapper);
            List<Map<String, Object>> list = result.getRecords().stream().map(u -> userBrief(u)).toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("total", result.getTotal());
            payload.put("list", list);
            return ok(payload);
        } catch (Exception exception) {
            return error(exception);
        }
    }

    private CallToolResult getUser(McpSyncServerExchange exchange, Map<String, Object> args) {
        try {
            SysUserDO user;
            Object userId = args.get("userId");
            if (userId != null) {
                user = userMapper.selectById(Long.valueOf(String.valueOf(userId)));
            } else {
                String username = stringArg(args, "username");
                if (!StringUtils.hasText(username)) {
                    return errorMessage("必须提供 userId 或 username 参数");
                }
                user = userMapper.selectOne(new LambdaQueryWrapper<SysUserDO>()
                        .eq(SysUserDO::getUsername, username));
            }
            if (user == null) {
                return errorMessage("用户不存在");
            }
            return ok(userDetail(user));
        } catch (Exception exception) {
            return error(exception);
        }
    }

    private CallToolResult listDevices(McpSyncServerExchange exchange, Map<String, Object> args) {
        try {
            long pageNum = longArg(args, "pageNum", 1L);
            long pageSize = Math.min(longArg(args, "pageSize", 20L), 100L);
            Integer status = intArg(args, "status");
            Page<DeviceDO> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<DeviceDO> wrapper = new LambdaQueryWrapper<DeviceDO>()
                    .eq(status != null, DeviceDO::getStatus, status)
                    .orderByDesc(DeviceDO::getId);
            IPage<DeviceDO> result = deviceMapper.selectPage(page, wrapper);
            List<Map<String, Object>> list = result.getRecords().stream().map(this::deviceBrief).toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("total", result.getTotal());
            payload.put("list", list);
            return ok(payload);
        } catch (Exception exception) {
            return error(exception);
        }
    }

    private CallToolResult platformStats(McpSyncServerExchange exchange, Map<String, Object> args) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userCount", userMapper.selectCount(new LambdaQueryWrapper<>()));
            payload.put("deviceCount", deviceMapper.selectCount(new LambdaQueryWrapper<>()));
            return ok(payload);
        } catch (Exception exception) {
            return error(exception);
        }
    }

    // ---------- 序列化辅助 ----------

    private Map<String, Object> userBrief(SysUserDO user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("status", user.getStatus());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    private Map<String, Object> userDetail(SysUserDO user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("deptId", user.getDeptId());
        map.put("status", user.getStatus());
        map.put("lastLoginTime", user.getLastLoginTime());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    private Map<String, Object> deviceBrief(DeviceDO device) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", device.getId());
        map.put("deviceCode", device.getDeviceCode());
        map.put("deviceName", device.getDeviceName());
        map.put("deviceType", device.getDeviceType());
        map.put("location", device.getLocation());
        map.put("status", device.getStatus());
        map.put("createdAt", device.getCreatedAt());
        return map;
    }

    private CallToolResult ok(Object data) {
        try {
            return new CallToolResult(objectMapper.writeValueAsString(data), false);
        } catch (Exception exception) {
            return error(exception);
        }
    }

    private CallToolResult error(Exception exception) {
        return errorMessage(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
    }

    private CallToolResult errorMessage(String message) {
        return new CallToolResult("{\"error\":\"" + message.replace("\"", "'") + "\"}", true);
    }

    private long longArg(Map<String, Object> args, String key, long defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private Integer intArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
