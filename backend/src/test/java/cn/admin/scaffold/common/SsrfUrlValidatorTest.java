package cn.admin.scaffold.common;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 出站 Webhook URL 的 SSRF 防护校验。 */
class SsrfUrlValidatorTest {

    // ---------- 静态校验：合法地址 ----------

    @Test
    void acceptsNullAndBlank() {
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl(null));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl(""));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("   "));
    }

    @Test
    void acceptsPublicHttpUrls() {
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("https://example.com/hook"));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("http://example.com/path?q=1"));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("https://oapi.dingtalk.com/robot/send?access_token=abc"));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("http://8.8.8.8/"));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[2606:4700::1111]/"));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[::ffff:8.8.8.8]/"));
    }

    // ---------- 静态校验：非法地址 ----------

    @Test
    void rejectsNonHttpSchemes() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("ftp://example.com/file"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("file:///etc/passwd"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("gopher://example.com"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("example.com/hook"));
    }

    @Test
    void rejectsUserInfoAndMissingHost() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://user:pass@example.com/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://user@example.com/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http:///path"));
    }

    @Test
    void rejectsLocalhostVariants() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://localhost/hook"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://foo.localhost/hook"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://localhost./hook"));
    }

    @Test
    void rejectsIpv4InternalLiterals() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://127.0.0.1/hook"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://127.0.0.1:8080/hook"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://10.0.0.5/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://172.16.5.5/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://192.168.1.1/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://169.254.169.254/latest/meta-data/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://100.64.0.1/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://192.0.0.9/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://192.0.2.9/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://198.18.1.1/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://198.51.100.9/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://203.0.113.9/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://240.0.0.1/"));
    }

    @Test
    void rejectsIpv6InternalLiterals() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[::1]/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[fe80::1]/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[fc00::1]/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[2001:db8::1]/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://[::ffff:127.0.0.1]/"));
    }

    @Test
    void rejectsPercentEncodedInternalHost() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://127%2e0%2e0%2e1/"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrl("http://localhost%2e/hook"));
    }

    // ---------- DNS 复核：静态校验前置，解析复核拦截主机名指向内网 ----------

    @Test
    void withDnsConsultsStaticCheckFirst() {
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns("http://localhost/hook"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns("http://127.0.0.1/hook"));
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns("ftp://example.com"));
    }

    @Test
    void withDnsPassesPublicLiteralWithoutNetwork() {
        // 8.8.8.8 为公网 IP 字面量，getAllByName 本地解析不触发 DNS
        assertNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns("http://8.8.8.8/hook"));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns(null));
        assertNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns(""));
    }

    @Test
    void withDnsRejectsIntegerFormInternalIp() {
        // 2130706433 是 127.0.0.1 的十进制整数形式，本地解析命中回环段
        assertNotNull(SsrfUrlValidator.validateOutboundHttpUrlWithDns("http://2130706433/"));
    }

    // ---------- 网段判定核心（包私有，构造 InetAddress 直测） ----------

    @Test
    void isInternalDetectsIpv4PrivateRanges() throws Exception {
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("127.0.0.1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("10.0.0.5")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("172.16.5.5")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("192.168.1.1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("169.254.169.254")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("100.64.0.1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("192.0.0.9")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("192.0.2.9")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("198.18.1.1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("198.51.100.9")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("203.0.113.9")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("240.0.0.1")));
    }

    @Test
    void isInternalAcceptsPublicIpv4() throws Exception {
        assertFalse(SsrfUrlValidator.isInternalAddress(addr("8.8.8.8")));
        assertFalse(SsrfUrlValidator.isInternalAddress(addr("1.1.1.1")));
        // 100.63 不在 100.64.0.0/10 内，属公网段
        assertFalse(SsrfUrlValidator.isInternalAddress(addr("100.63.0.1")));
    }

    @Test
    void isInternalDetectsIpv6SpecialRanges() throws Exception {
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("::1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("fe80::1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("fc00::1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("2001:db8::1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("::ffff:127.0.0.1")));
        assertTrue(SsrfUrlValidator.isInternalAddress(addr("64:ff9b::7f00:1")));
        assertFalse(SsrfUrlValidator.isInternalAddress(addr("::ffff:8.8.8.8")));
        assertFalse(SsrfUrlValidator.isInternalAddress(addr("2606:4700::1111")));
    }

    private InetAddress addr(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip);
    }
}
