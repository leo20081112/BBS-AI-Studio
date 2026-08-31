#
# BBS AI Studio 外部工具链
#
# 配置加载器：解密读取游戏内的 ai_providers.json（AES-GCM，UUID 派生密钥），
# 与 Java 端 ConfigSyncManager / EncryptionUtil 完全同构。
#
# 加密契约：
#   key = SHA-256("bbs_ai_studio_" + minecraft_uuid)
#   AES/GCM/NoPadding，随机 12 字节 IV 附在密文前，整体 Base64
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import base64
import hashlib
import json
import os
from dataclasses import dataclass
from typing import Optional

KEY_PREFIX = "bbs_ai_studio_"
IV_LENGTH = 12


@dataclass
class AIConfig:
    """AI 服务配置（与 Java 端 AIConfig 字段一致）"""

    mode: str = "API"                # LOCAL / API
    provider: str = "OPENAI"         # OPENAI / CLAUDE / DEEPSEEK / GLM / CUSTOM
    api_key: str = ""
    base_url: str = ""
    model: str = ""
    temperature: float = 0.7
    max_tokens: int = 4096


def derive_key(uuid: str) -> bytes:
    """UUID 派生 AES-256 密钥"""

    return hashlib.sha256((KEY_PREFIX + uuid).encode("utf-8")).digest()


def decrypt_payload(encrypted_b64: str, uuid: str) -> str:
    """解密配置载荷（Base64(iv + ciphertext) → 明文 JSON 字符串）"""

    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError as exc:
        raise ImportError("需要 cryptography 库：pip install cryptography") from exc

    data = base64.b64decode(encrypted_b64)

    if len(data) <= IV_LENGTH:
        raise ValueError("加密数据长度不合法")

    iv, ciphertext = data[:IV_LENGTH], data[IV_LENGTH:]
    aesgcm = AESGCM(derive_key(uuid))

    return aesgcm.decrypt(iv, ciphertext, None).decode("utf-8")


def encrypt_payload(plain: str, uuid: str) -> str:
    """加密配置载荷（供写回配置）"""

    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError as exc:
        raise ImportError("需要 cryptography 库：pip install cryptography") from exc

    iv = os.urandom(IV_LENGTH)
    aesgcm = AESGCM(derive_key(uuid))
    ciphertext = aesgcm.encrypt(iv, plain.encode("utf-8"), None)

    return base64.b64encode(iv + ciphertext).decode("ascii")


def find_config_file(search_paths: Optional[list] = None) -> Optional[str]:
    """查找 ai_providers.json"""

    if search_paths is None:
        candidates = [
            os.environ.get("BBS_AI_CONFIG", ""),
            os.path.join(os.getcwd(), "config", "bbs", "settings", "ai_providers.json"),
            os.path.join(os.getcwd(), "..", "config", "bbs", "settings", "ai_providers.json"),
            os.path.join(os.getcwd(), "..", "..", "config", "bbs", "settings", "ai_providers.json"),
        ]

        search_paths = [c for c in candidates if c]

    for path in search_paths:
        if path and os.path.isfile(path):
            return path

    return None


def load_config(uuid: str, config_path: Optional[str] = None) -> AIConfig:
    """读取（解密）AI 配置；文件不存在或解密失败时返回默认配置"""

    path = config_path or find_config_file()

    if not path:
        return AIConfig()

    try:
        with open(path, "r", encoding="utf-8") as handle:
            envelope = json.load(handle)

        if not envelope.get("encrypted", False):
            payload = envelope.get("payload", {})

            return _config_from_dict(payload if isinstance(payload, dict) else {})

        plain = decrypt_payload(envelope.get("payload", ""), uuid)
        inner = json.loads(plain)

        return _config_from_dict(inner)
    except FileNotFoundError:
        return AIConfig()
    except Exception as exc:
        print("[bbs-ai-toolchain] 配置解密失败（将使用默认配置）：%s" % exc)

        return AIConfig()


def _config_from_dict(data: dict) -> AIConfig:
    return AIConfig(
        mode=data.get("mode", "API"),
        provider=data.get("provider", "OPENAI"),
        api_key=data.get("api_key", ""),
        base_url=data.get("base_url", ""),
        model=data.get("model", ""),
        temperature=float(data.get("temperature", 0.7)),
        max_tokens=int(data.get("max_tokens", 4096)),
    )


def save_config(config: AIConfig, uuid: str, config_path: str) -> None:
    """加密写回配置"""

    envelope = {
        "format": "bbs_ai_studio_config_v1",
        "encrypted": True,
        "payload": encrypt_payload(
            json.dumps({
                "mode": config.mode,
                "provider": config.provider,
                "api_key": config.api_key,
                "base_url": config.base_url,
                "model": config.model,
                "temperature": config.temperature,
                "max_tokens": config.max_tokens,
            }, ensure_ascii=False),
            uuid,
        ),
    }

    os.makedirs(os.path.dirname(os.path.abspath(config_path)), exist_ok=True)

    with open(config_path, "w", encoding="utf-8") as handle:
        json.dump(envelope, handle, ensure_ascii=False, indent=2)
