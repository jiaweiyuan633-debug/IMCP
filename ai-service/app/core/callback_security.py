"""回调 URL SSRF 防护（R1-1.3）。

攻击面：任务 API 的 ``callback_url`` 由调用方任意指定，任务工作线程完成后
无条件出站 POST 到该地址。不做校验时，持有 AUTH_TOKEN 的上游或被攻陷的调用方
可让 AI 服务访问云元数据（169.254.169.254）、链路本地、私网或本机服务
（SSRF），并可能把任务结果中的敏感数据投递给任意目标。

防护策略：
- 双模式：未配置 ``callback_allowed_origins`` 时仅允许回环（localhost /
  127.0.0.1 / ::1，本地开发默认）；配置后按 origin 精确白名单（生产推荐，
  白名单即部署契约，指向后端回调入口）。
- 危险段硬拒：未指定 / 链路本地（含云元数据） / 组播 / 保留 / 广播地址无论何种
  模式一律拒绝；域名解析出的任一 IP 落入危险段即拒绝（防 DNS 重绑定到元数据）。
- 双点校验：建单时失败快速（400），执行回调前重检（防御纵深，兼容修复前已入库
  的任务）。

设计取舍：
- 默认（无白名单）按“主机名闸门”放行回环名，而非仅看解析结果——即使某域名当前
  解析到回环，攻击者可控制其 DNS 随时指向内网，故非回环名一律不放行；IP 字面量
  则直接按地址段判定（无需解析，避免环境依赖）。
- 白名单模式下域名解析为“尽力而为”：解析失败不阻断（集群内 Service 瞬时未就绪、
  瞬时 DNS 抖动等，交由回调重试），但成功解析出的危险段 IP 一律拒绝。
"""

from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urlsplit

from app.core.config import Settings

_ALLOWED_SCHEMES = frozenset({"http", "https"})
_DEFAULT_PORTS = {"http": 80, "https": 443}

_LOOPBACK_HOSTNAMES = frozenset({"localhost", "localhost.localdomain"})

IPAddr = ipaddress.IPv4Address | ipaddress.IPv6Address


def _is_hard_blocked(ip: IPAddr) -> bool:
    """危险段判定：任何回调目标都无合法用途的地址段。
    IPv4：未指定 0.0.0.0/8、链路本地 169.254.0.0/16（含云元数据 169.254.169.254）、
    组播 224.0.0.0/4、保留 240.0.0.0/4（含 255.255.255.255 广播）。
    IPv6：未指定 ::、链路本地 fe80::/10、组播 ff00::/8、保留段。
    回环（127.0.0.0/8、::1）必须先在硬拒前排除——IPv6 的 ::1 位于保留的 ::/8 内，
    其 ``is_reserved`` 为 True，但作为本地开发唯一放行目标不得被硬拒。
    """
    if ip.is_loopback:
        return False
    return ip.is_unspecified or ip.is_link_local or ip.is_multicast or ip.is_reserved


def _try_ip_literal(host: str) -> IPAddr | None:
    try:
        return ipaddress.ip_address(host)
    except ValueError:
        return None


def _is_loopback_hostname(host: str) -> bool:
    host = host.lower()
    return host in _LOOPBACK_HOSTNAMES or host.endswith(".localhost")


def _valid_port(port: int) -> bool:
    return 1 <= port <= 65535


def _extract_origin(url: str) -> tuple[str, str, int | None] | None:
    """提取 (scheme, 小写 host, port)。非法结构（非 http/https、缺主机名、
    内嵌凭据、坏端口）返回 None，调用方统一拒绝。"""
    try:
        parts = urlsplit(url)
        if parts.scheme not in _ALLOWED_SCHEMES or not parts.hostname:
            return None
        # URL 内嵌 user:pass 既是凭据泄露点，也可用作主机混淆，一律拒绝
        if parts.username is not None or parts.password is not None:
            return None
        port = parts.port
        if port is not None and not _valid_port(port):
            return None
        return parts.scheme, parts.hostname.lower(), port
    except ValueError:
        return None


def _origin_key(scheme: str, host: str, port: int | None) -> tuple[str, str]:
    """origin 归一化键：scheme + 小写 host[:port]，默认端口折叠（:80/:443）。"""
    if port is not None and _DEFAULT_PORTS.get(scheme) == port:
        port = None
    return scheme, f"{host}:{port}" if port is not None else host


class CallbackUrlGuard:
    """回调 URL 校验守卫。仅构造时读取配置，评估过程无共享可变状态，可跨线程复用。"""

    def __init__(self, settings: Settings) -> None:
        self._allowed = self._parse_allowed(settings.callback_allowed_origins)

    @staticmethod
    def _parse_allowed(origins: list[str]) -> frozenset[tuple[str, str]]:
        allowed: set[tuple[str, str]] = set()
        for origin in origins:
            extracted = _extract_origin(origin)
            if extracted is None:
                # 白名单配置错误必须立即暴露，避免静默放宽回调范围
                raise ValueError(
                    f"非法回调白名单 origin（需为不含凭据的 http/https 地址）: {origin!r}"
                )
            scheme, host, port = extracted
            allowed.add(_origin_key(scheme, host, port))
        return frozenset(allowed)

    def validate(self, url: str) -> None:
        """校验回调地址；不合法抛 ValueError（建单时调用，失败快速返回 400）。"""
        ok, reason = self.evaluate(url)
        if not ok:
            raise ValueError(reason)

    def is_safe(self, url: str) -> bool:
        """回调地址是否安全（执行回调前调用，防御纵深）。"""
        return self.evaluate(url)[0]

    def evaluate(self, url: str) -> tuple[bool, str]:
        """评估回调地址，返回 (是否安全, 原因)。纯函数，可在任意线程调用。"""
        extracted = _extract_origin(url)
        if extracted is None:
            return False, "回调地址非法：需为不含凭据的 http/https URL"
        scheme, host, port = extracted

        if self._allowed:
            # 生产模式：origin 精确匹配白名单；解析结果仅做危险段兜底（尽力而为）
            if _origin_key(scheme, host, port) not in self._allowed:
                return False, f"回调 origin 不在白名单（callback_allowed_origins）: {url}"
            return self._check_resolved(host, loopback_only=False, fail_open=True)

        # 默认（本地开发）模式：回环字面量 / 回环域名闸门 + 解析结果必须全部为回环
        literal = _try_ip_literal(host)
        if not ((literal is not None and literal.is_loopback) or _is_loopback_hostname(host)):
            return False, (
                f"未配置回调白名单（callback_allowed_origins），"
                f"仅允许 localhost / 127.0.0.1 等回环地址: {host!r}"
            )
        return self._check_resolved(host, loopback_only=True, fail_open=False)

    def _check_resolved(
        self, host: str, loopback_only: bool, fail_open: bool
    ) -> tuple[bool, str]:
        literal = _try_ip_literal(host)
        if literal is not None:
            return self._judge([literal], loopback_only)
        try:
            infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
        except socket.gaierror:
            if fail_open:
                # 白名单模式：解析失败不阻断（瞬时 DNS/Service 未就绪），交给回调重试
                return True, "ok"
            return False, f"回调主机名无法解析: {host!r}"
        ips = {ipaddress.ip_address(info[4][0]) for info in infos}
        return self._judge(sorted(ips, key=str), loopback_only)

    @staticmethod
    def _judge(ips: list[IPAddr], loopback_only: bool) -> tuple[bool, str]:
        for ip in ips:
            if _is_hard_blocked(ip):
                return False, f"回调解析到危险地址: {ip}"
        if loopback_only:
            for ip in ips:
                if not ip.is_loopback:
                    return False, f"未配置白名单时仅允许回环，解析到非回环地址: {ip}"
        return True, "ok"
