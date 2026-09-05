package cn.admin.scaffold.common;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * 请求路径匹配前归一化工具（供 ApiPermRegistry / ApiPermAuthorizationFilter 使用）。
 *
 * <p>与 {@link FileAccessService#normalizePath}（文件令牌签发/校验口径）同思路，但按
 * Servlet 匹配场景扩展：去掉 {@code ;} 矩阵参数 → URL 解码 → 折叠重复斜杠 → 去尾斜杠，
 * 并把历史版本前缀 {@code /api/v1/} 重写为 {@code /api/}（与 ApiVersionFilter 语义一致，
 * 防下游校验口径与版本重写分叉）。统一口径后，同一接口的多态写法（如
 * {@code /api/system/user/1;v=2}、{@code /api//system/user/1/}、URL 编码变体）命中同一条规则，
 * 避免规则漏配被路径变体绕过。
 */
public final class PathNormalizer {

    private static final String API_V1_PREFIX = "/api/v1/";

    private PathNormalizer() {
    }

    /**
     * 归一化请求路径（原始 URI 或已含矩阵参数/编码的变体均可）；非法输入返回 null 由调用方兜底。
     */
    public static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }
        // 1) 剥离 ; 矩阵参数（与 Spring UrlPathHelper removeSemicolonContent 同序）
        String path = rawPath;
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }
        // 2) URL 解码；非法百分号序列不抛异常，保留原样（由后续匹配自然落空或命中宽松模式）
        try {
            path = UriUtils.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 保留未解码路径
        }
        // 3) 折叠重复斜杠：//api//x -> /api/x
        path = collapseDuplicateSlashes(path);
        // 4) 去尾斜杠（根路径除外）
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // 5) 版本前缀重写 /api/v1/ -> /api/（ApiVersionFilter 已做同样重写，此处防御双保险）
        if (path.startsWith(API_V1_PREFIX)) {
            path = "/api/" + path.substring(API_V1_PREFIX.length());
        }
        return path;
    }

    private static String collapseDuplicateSlashes(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        boolean lastSlash = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') {
                if (!lastSlash || sb.length() == 0) {
                    sb.append(c);
                }
                lastSlash = true;
            } else {
                sb.append(c);
                lastSlash = false;
            }
        }
        return sb.toString();
    }
}
