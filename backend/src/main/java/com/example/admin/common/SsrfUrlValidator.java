package com.example.admin.common;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 出站 HTTP(S) URL 的 SSRF 防护校验（R4-1.13）。
 *
 * <p>告警 Webhook、通用 Webhook 渠道等入口允许管理员配置任意 URL，若服务端直接请求，
 * 恶意或误配的地址可把服务端当跳板探测/攻击内网（云元数据 169.254.169.254、内网管理面、
 * 本机服务等）。本校验分两层拦截：
 * <ol>
 *   <li>静态校验 {@link #validateOutboundHttpUrl}：协议白名单（仅 http/https）、禁止携带用户信息、
 *       主机名必须存在、拒绝 localhost 及回环/链路本地/站点本地/保留/文档等 IP 字面量。
 *       不发起 DNS 解析，适合保存时快速反馈。</li>
 *   <li>投递时校验 {@link #validateOutboundHttpUrlWithDns}：在静态校验之上解析主机名，
 *       任一解析地址落在内部/保留网段即拒绝，兜住"主机名指向内网 IP"与"保存后 DNS 变更"两类绕过。</li>
 * </ol>
 * 空串视为合法（"未配置 Webhook"的语义由调用方处理）。返回 null 表示校验通过，否则返回错误消息。
 */
public final class SsrfUrlValidator {

    private SsrfUrlValidator() {
    }

    /** 静态校验：协议/主机/凭据/IP 字面量。不发起 DNS，返回错误消息或 null（通过）。 */
    public static String validateOutboundHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String host;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return "仅支持 http/https 协议";
            }
            if (uri.getUserInfo() != null) {
                return "不允许 URL 携带用户名密码";
            }
            host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "URL 缺少主机名";
            }
        } catch (IllegalArgumentException e) {
            return "URL 格式不合法";
        }
        if (!isSafeHostname(host)) {
            return "URL 主机为内部/回环/保留地址";
        }
        return null;
    }

    /** 投递时校验：静态校验 + DNS 解析复核，任一解析地址落在内部网段即拒绝。 */
    public static String validateOutboundHttpUrlWithDns(String url) {
        String staticError = validateOutboundHttpUrl(url);
        if (staticError != null || url == null || url.isBlank()) {
            return staticError;
        }
        String host = normalizeHost(URI.create(url.trim()).getHost());
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternalAddress(address)) {
                    return "URL 主机解析到内部/保留地址: " + address.getHostAddress();
                }
            }
        } catch (UnknownHostException e) {
            return "URL 主机无法解析: " + host;
        }
        return null;
    }

    /** 主机名静态安全判定：不发起 DNS，仅处理字面量。 */
    private static boolean isSafeHostname(String host) {
        String h = normalizeHost(host);
        if (h.isBlank()) {
            return false;
        }
        if (h.equalsIgnoreCase("localhost") || h.endsWith(".localhost")) {
            return false;
        }
        if (h.indexOf(':') >= 0 || h.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            // IPv6 字面量或点分十进制 IPv4：本地解析（不发 DNS）校验网段
            try {
                return !isInternalAddress(InetAddress.getByName(h));
            } catch (UnknownHostException e) {
                return false;
            }
        }
        // 其余主机名（含整数形式的 IPv4，如 2130706433=127.0.0.1）交由 DNS 阶段拦截
        return true;
    }

    /** 归一化主机名：去尾点 → 解码 %XX → 去 IPv6 方括号 → 再检查尾点（防编码绕过）。 */
    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String h = host.trim();
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        h = decodeHost(h);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        return h;
    }

    /** 仅解码 %XX 转义；' + ' 等非转义字符原样保留（URLDecoder 会把 '+' 当空格，故不用）。 */
    private static String decodeHost(String host) {
        if (!host.contains("%")) {
            return host;
        }
        StringBuilder sb = new StringBuilder(host.length());
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '%' && i + 2 < host.length()) {
                int hi = Character.digit(host.charAt(i + 1), 16);
                int lo = Character.digit(host.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    sb.append((char) ((hi << 4) | lo));
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 判断 IP 是否落在内部/保留网段（包私有便于单测直接构造 InetAddress 验证）。
     *
     * <p>Java 内建覆盖 any/loopback/link-local/site-local/multicast；
     * 手工补 IPv4 的 0/8、100.64/10 CGNAT、192.0.0.0/24、192.0.2.0/24、198.18.0.0/15、
     * 198.51.100.0/24、203.0.113.0/24、240/4 保留，以及 IPv6 的 IPv4-mapped、64:ff9b::/96
     * NAT64、2001:db8::/32 文档地址。
     */
    static boolean isInternalAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address inet4) {
            byte[] b = inet4.getAddress();
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            int third = b[2] & 0xFF;
            if (first == 0) {
                return true;                                      // 0.0.0.0/8
            }
            if (first == 100 && (second & 0xC0) == 0x40) {
                return true;                                      // 100.64.0.0/10 CGNAT
            }
            if (first == 192 && second == 0) {
                return true;                                      // 192.0.0.0/24 与 192.0.2.0/24
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return true;                                      // 198.18.0.0/15 基准测试
            }
            if (first == 198 && second == 51 && third == 100) {
                return true;                                      // 198.51.100.0/24
            }
            if (first == 203 && second == 0 && third == 113) {
                return true;                                      // 203.0.113.0/24
            }
            return first >= 240;                                  // 240.0.0.0/4 保留
        }
        if (address instanceof Inet6Address inet6) {
            byte[] b = inet6.getAddress();
            boolean ipv4Mapped = b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0
                    && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0
                    && b[8] == 0 && b[9] == 0
                    && b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
            if (ipv4Mapped) {
                return isEmbeddedIpv4Internal(b, 12);
            }
            boolean nat64 = b[0] == 0 && b[1] == 0x64
                    && b[2] == (byte) 0xFF && b[3] == (byte) 0x9B
                    && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0
                    && b[8] == 0 && b[9] == 0 && b[10] == 0 && b[11] == 0;
            if (nat64) {
                return isEmbeddedIpv4Internal(b, 12);
            }
            // fc00::/7 唯一本地地址（ULA）。Java 的 isSiteLocalAddress 仅覆盖 fec0::/10，需显式补。
            if ((b[0] & 0xFE) == 0xFC) {
                return true;
            }
            // 2001:db8::/32 文档地址
            if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01
                    && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8) {
                return true;
            }
            return false;
        }
        return true; // 未知类型一律拒绝（fail-closed）
    }

    /** 从 IPv6 字节中取出内嵌的 32 位 IPv4 并递归判定。 */
    private static boolean isEmbeddedIpv4Internal(byte[] bytes, int offset) {
        try {
            byte[] ipv4 = new byte[]{bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]};
            return isInternalAddress(InetAddress.getByAddress(ipv4));
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
