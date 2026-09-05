"""OCR 模块测试：Mock 确定性、Tesseract 探测、注册表选择与回退。"""

import random

import pytest

from app.core.config import Settings
from app.ocr import MockOCRProvider, TesseractOCRProvider, get_ocr_provider


@pytest.mark.asyncio
async def test_mock_ocr_extracts_longest_ascii() -> None:
    """从含 ASCII 文本的字节中提取最长的可打印 ASCII 序列。"""
    provider = MockOCRProvider()
    result = await provider.recognize(b"\x00HELLO OCR\x00")
    assert result == "HELLO OCR"


@pytest.mark.asyncio
async def test_mock_ocr_deterministic_fallback() -> None:
    """无可打印 ASCII 序列时输出确定性指纹（同输入同输出、含 OCR(mock)）。"""
    # 仅含控制字符（0-31），保证不命中可打印 ASCII 分支
    image_bytes = bytes(random.randrange(0, 32) for _ in range(64))
    provider = MockOCRProvider()
    first = await provider.recognize(image_bytes)
    second = await provider.recognize(image_bytes)
    assert first == second
    assert "OCR(mock)" in first
    assert first.startswith("OCR(mock): ")


def test_tesseract_is_available_no_raise() -> None:
    """is_available() 调用不抛异常，返回 bool。"""
    available = TesseractOCRProvider.is_available()
    assert isinstance(available, bool)


def test_registry_default_returns_mock() -> None:
    """默认配置（ocr_provider=mock）返回 MockOCRProvider 实例。"""
    provider = get_ocr_provider(Settings(ocr_provider="mock"))
    assert isinstance(provider, MockOCRProvider)


def test_registry_tesseract_unavailable_falls_back_to_mock(monkeypatch) -> None:
    """tesseract 配置但探测不可用时回退 Mock，不抛异常。"""
    from app.ocr import registry as registry_module

    monkeypatch.setattr(registry_module.TesseractOCRProvider, "is_available", lambda: False)
    provider = get_ocr_provider(Settings(ocr_provider="tesseract"))
    assert isinstance(provider, MockOCRProvider)


def test_registry_tesseract_available_returns_tesseract(monkeypatch) -> None:
    """tesseract 配置且探测可用时返回 TesseractOCRProvider 实例。"""
    from app.ocr import registry as registry_module

    monkeypatch.setattr(
        registry_module.TesseractOCRProvider, "is_available", classmethod(lambda cls: True)
    )
    provider = get_ocr_provider(Settings(ocr_provider="tesseract"))
    assert isinstance(provider, TesseractOCRProvider)


def test_registry_tesseract_fail_fast_raises_when_unavailable(monkeypatch) -> None:
    """ocr_fail_fast=True 且 tesseract 不可用时直接抛错（不静默回退 Mock）。"""
    from app.ocr import registry as registry_module

    monkeypatch.setattr(registry_module.TesseractOCRProvider, "is_available", lambda: False)
    with pytest.raises(RuntimeError):
        get_ocr_provider(Settings(ocr_provider="tesseract", ocr_fail_fast=True))


def test_registry_tesseract_fail_fast_raises_on_construct_failure(monkeypatch) -> None:
    """ocr_fail_fast=True 且构造失败时抛错。"""
    from app.ocr import registry as registry_module

    monkeypatch.setattr(
        registry_module.TesseractOCRProvider, "is_available", classmethod(lambda cls: True)
    )
    monkeypatch.setattr(
        registry_module.TesseractOCRProvider,
        "__init__",
        lambda self: (_ for _ in ()).throw(RuntimeError("boom")),
    )
    with pytest.raises(RuntimeError):
        get_ocr_provider(Settings(ocr_provider="tesseract", ocr_fail_fast=True))
