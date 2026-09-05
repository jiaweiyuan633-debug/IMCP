"""PII 检测与脱敏模块测试。"""

from app.pii import PII_RULES
from app.pii.masker import StreamMasker, detect, mask, mask_fields


def test_detect_phone() -> None:
    """手机号命中的位置与 value 正确，且 400 固话不误命中。"""
    text = "联系我 13812345678 或 400-123456"
    spans = detect(text)
    phones = [s for s in spans if s["kind"] == "phone"]
    assert len(phones) == 1
    assert phones[0]["value"] == "13812345678"
    assert (phones[0]["start"], phones[0]["end"]) == (4, 15)


def test_detect_id_card() -> None:
    """18 位身份证（末位 X）命中的位置与 value 正确。"""
    text = "身份证 11010519491231002X 已登记"
    spans = detect(text)
    id_spans = [s for s in spans if s["kind"] == "id_card"]
    assert len(id_spans) == 1
    assert id_spans[0]["value"] == "11010519491231002X"
    assert (id_spans[0]["start"], id_spans[0]["end"]) == (4, 22)


def test_detect_id_card_15() -> None:
    """15 位身份证可被识别。"""
    spans = detect("旧证 110105491231002")
    id_spans = [s for s in spans if s["kind"] == "id_card"]
    assert len(id_spans) == 1
    assert id_spans[0]["value"] == "110105491231002"


def test_phone_not_inside_id_card() -> None:
    """身份证内部含 1[3-9] 开头的 11 位连续数字，不应被误判为手机号。"""
    text = "证件号 110105194912310021"
    kinds = {s["kind"] for s in detect(text)}
    assert "id_card" in kinds
    assert "phone" not in kinds


def test_id_card_beats_bank_card() -> None:
    """18 位纯数字身份证同时满足银行卡的 16-19 位，应合并为 id_card 一条。"""
    spans = detect("110105194912310021")
    assert len(spans) == 1
    assert spans[0]["kind"] == "id_card"
    assert spans[0]["value"] == "110105194912310021"


def test_detect_email() -> None:
    """多个邮箱均被识别。"""
    text = "邮箱 user.name+tag@example.com.cn，备用 bob@test.org"
    spans = detect(text)
    emails = [s for s in spans if s["kind"] == "email"]
    assert [s["value"] for s in emails] == [
        "user.name+tag@example.com.cn",
        "bob@test.org",
    ]


def test_detect_bank_card() -> None:
    """银行卡号命中的位置与 value 正确。"""
    text = "卡号 6222021234567890"
    spans = detect(text)
    cards = [s for s in spans if s["kind"] == "bank_card"]
    assert len(cards) == 1
    assert cards[0]["value"] == "6222021234567890"
    assert (cards[0]["start"], cards[0]["end"]) == (3, 19)


def test_bank_card_no_duplicate_with_credit_card() -> None:
    """16 位数字同时满足 bank_card 与 credit_card，只保留优先级更高的 bank_card。"""
    kinds = [s["kind"] for s in detect("6222021234567890")]
    assert kinds.count("bank_card") == 1
    assert "credit_card" not in kinds


def test_detect_ip() -> None:
    """IPv4 地址命中的位置与 value 正确。"""
    text = "来源 192.168.1.10"
    spans = detect(text)
    ips = [s for s in spans if s["kind"] == "ip"]
    assert len(ips) == 1
    assert ips[0]["value"] == "192.168.1.10"
    assert ips[0]["start"] == 3


def test_detect_passport() -> None:
    """护照号两种格式均可识别。"""
    spans = detect("护照 G12345678 和 P1234567")
    kinds = [s["kind"] for s in spans if s["kind"] == "passport"]
    assert len(kinds) == 2


def test_detect_merged_sorted_non_overlapping() -> None:
    """detect 结果按 start 排序且互不重叠。"""
    text = "手机 13812345678 身份证 11010519491231002X 邮箱 a@b.cn"
    spans = detect(text)
    starts = [s["start"] for s in spans]
    assert starts == sorted(starts)
    for i in range(1, len(spans)):
        assert spans[i - 1]["end"] <= spans[i]["start"]


def test_mask_replaces_length_and_content() -> None:
    """脱敏后原文不出现，替换长度与区间长度一致。"""
    text = "手机 13812345678 邮箱 a@b.cn"
    out = mask(text)
    assert "13812345678" not in out
    assert "a@b.cn" not in out
    assert out == "手机 *********** 邮箱 ******"


def test_mask_custom_char() -> None:
    """支持自定义掩码字符，长度不变。"""
    out = mask("号码13812345678结束", mask_char="#")
    assert out == "号码###########结束"


def test_mask_kinds_filter() -> None:
    """按 kinds 过滤时只脱敏指定类型，其余原样保留。"""
    text = "手机 13812345678 邮箱 a@b.cn"
    out = mask(text, kinds=("email",))
    assert "a@b.cn" not in out
    assert "13812345678" in out


def test_mask_kinds_empty_returns_original() -> None:
    """kinds 为空时不做任何替换。"""
    text = "手机 13812345678"
    assert mask(text, kinds=()) == text


def test_mask_fields_nested_structure() -> None:
    """嵌套 dict + list 结构脱敏后结构一致，普通 PII 走常规掩码。"""
    data = {
        "user": {"name": "张三", "phone": "13812345678"},
        "items": [
            {"email": "a@b.cn", "count": 3},
            "plain text",
        ],
        "password": "my-pass-123",
        "Token": "secret-token",
        "safe": "13812345678",
    }
    out = mask_fields(data)
    assert out["user"]["name"] == "张三"
    assert out["user"]["phone"] == "***********"
    assert out["items"][0]["email"] == "******"
    assert out["items"][0]["count"] == 3
    assert out["items"][1] == "plain text"
    assert out["safe"] == "***********"
    # 敏感键整体打码，不透出原文
    assert out["password"] == "********"
    assert out["Token"] == "********"
    assert "my-pass-123" not in out["password"]


def test_mask_fields_sensitive_wholesale() -> None:
    """敏感键即使值为结构体/数字，也整体替换为 8 个掩码字符。"""
    data = {"password": {"inner": "13812345678"}, "authorization": 12345}
    out = mask_fields(data)
    assert out["password"] == "********"
    assert out["authorization"] == "********"
    assert "13812345678" not in str(out)


def test_no_pii_detect_empty_and_mask_unchanged() -> None:
    """普通文本：detect 为空，mask 原样返回。"""
    text = "这是一个普通文本，没有任何敏感信息。"
    assert detect(text) == []
    assert mask(text) == text


def test_all_kinds_match_rules_order() -> None:
    """ALL_KINDS 与 PII_RULES 的 kind 顺序一致。"""
    from app.pii import ALL_KINDS

    assert ALL_KINDS == tuple(rule.kind for rule in PII_RULES)


# ---------- 流式脱敏（跨分片 PII 不泄漏） ----------


def _run_stream_masker(text: str, chunk: int = 3) -> str:
    """把文本按 chunk 长度分片喂给 StreamMasker，拼装全部发射分片 + flush。"""
    masker = StreamMasker()
    parts: list[str] = []
    for i in range(0, len(text), chunk):
        parts.extend(masker.emit(text[i : i + chunk]))
    tail = masker.flush()
    if tail:
        parts.append(tail)
    return "".join(parts)


def test_stream_masker_split_phone_is_masked() -> None:
    """手机号逐字吐出（任何单字符都不是完整模式）仍被完整脱敏，不泄漏。"""
    text = "联系我 13812345678 结束"
    out = _run_stream_masker(text, chunk=1)
    assert "13812345678" not in out
    assert out == mask(text)


def test_stream_masker_output_matches_mask() -> None:
    """流式拼装结果与一次性 mask 完全一致（仅分片边界可能不同）。"""
    text = "手机 13812345678 邮箱 a@b.cn 身份证 11010519491231002X"
    assert _run_stream_masker(text, chunk=2) == mask(text)
    assert _run_stream_masker(text, chunk=5) == mask(text)
    assert _run_stream_masker(text, chunk=11) == mask(text)


def test_stream_masker_short_input_flushed_as_is() -> None:
    """短输入全部留在缓冲，flush 时一次性返回；不完整号码不算 PII 原样透出。"""
    masker = StreamMasker()
    assert masker.emit("138") == []  # 不足 HOLD，暂不发射
    assert masker.flush() == "138"  # 不完整号码未命中，原样返回


def test_stream_masker_long_text_chunks_flow() -> None:
    """长文本触发滚动缓冲：中段即发射部分分片（而非全部憋到 flush），结果仍一致。"""
    text = "，".join(f"客户{i} 手机 13812345678 邮箱 a{i}@b.cn" for i in range(30))
    chunks = _run_stream_masker(text, chunk=5)
    assert "13812345678" not in chunks
    assert chunks == mask(text)
    assert chunks != text  # 确实发生了脱敏（mask 为 1:1 替换，长度不变但内容已变）
    assert "*" in chunks  # 中段已发射脱敏分片，非全部憋到 flush


def test_stream_masker_does_not_mutate_plain_text() -> None:
    """普通文本流式拼装与原文一致（无 PII 时零改动）。"""
    text = "这是一个完全没有敏感信息的普通段落。"
    assert _run_stream_masker(text, chunk=4) == text
