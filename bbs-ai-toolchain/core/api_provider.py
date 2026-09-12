#
# BBS AI Studio 外部工具链
#
# API Provider 抽象与 SSE 流式支持。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import json
from abc import ABC, abstractmethod
from typing import Callable, Dict, Iterator, List, Optional

import requests


class APIError(Exception):
    """API 异常（含用户可读信息）"""

    def __init__(self, message: str, http_code: int = -1):
        super().__init__(message)

        self.http_code = http_code

    def user_message(self) -> str:
        code = self.http_code

        if code == 401:
            return "API Key 无效或已过期（401），请检查密钥配置"

        if code == 429:
            return "触发速率限制（429），请稍后重试"

        if code >= 500:
            return "服务端错误（%d），建议稍后重试" % code

        return str(self)


class AIProviderBase(ABC):
    """API Provider 抽象基类"""

    name = "base"

    def __init__(self, api_key: str, base_url: str, model: str, temperature: float = 0.7, max_tokens: int = 4096, timeout: int = 120):
        self.api_key = api_key
        self.base_url = (base_url or "").rstrip("/")
        self.model = model
        self.temperature = temperature
        self.max_tokens = max_tokens
        self.timeout = timeout

    @abstractmethod
    def build_request(self, system_prompt: str, user_prompt: str, image_base64: Optional[str]) -> Dict:
        """构造 (url, headers, payload)"""

    def generate(self, system_prompt: str, user_prompt: str, image_base64: Optional[str] = None) -> str:
        """阻塞式生成"""

        url, headers, payload = self.build_request(system_prompt, user_prompt, image_base64)

        try:
            response = requests.post(url, headers=headers, json=payload, timeout=self.timeout)
        except requests.exceptions.Timeout as exc:
            raise APIError("请求超时") from exc
        except requests.exceptions.ConnectionError as exc:
            raise APIError("网络不可达：%s" % exc) from exc

        if response.status_code != 200:
            raise APIError(self._error_detail(response), response.status_code)

        return self.parse_response(response.json())

    def stream(self, system_prompt: str, user_prompt: str, image_base64: Optional[str] = None) -> Iterator[str]:
        """SSE 流式生成（逐段产出文本）"""

        url, headers, payload = self.build_request(system_prompt, user_prompt, image_base64)

        payload["stream"] = True
        headers["Accept"] = "text/event-stream"

        try:
            response = requests.post(url, headers=headers, json=payload, timeout=self.timeout, stream=True)
        except requests.exceptions.ConnectionError as exc:
            raise APIError("网络不可达：%s" % exc) from exc

        if response.status_code != 200:
            raise APIError(self._error_detail(response), response.status_code)

        for line in response.iter_lines(decode_unicode=True):
            if not line or not line.startswith("data:"):
                continue

            data = line[5:].strip()

            if data == "[DONE]":
                break

            try:
                chunk = json.loads(data)
            except json.JSONDecodeError:
                continue

            piece = self._extract_stream_delta(chunk)

            if piece:
                yield piece

    def _error_detail(self, response) -> str:
        try:
            error = response.json().get("error", {})

            if isinstance(error, dict):
                return error.get("message", response.text[:200])

            return str(error)
        except Exception:
            return response.text[:200]

    @abstractmethod
    def parse_response(self, data: Dict) -> str:
        """从响应 JSON 提取文本"""

    @abstractmethod
    def _extract_stream_delta(self, chunk: Dict) -> Optional[str]:
        """从流式分块提取增量文本"""


def join_messages(system_prompt: str, user_prompt: str) -> List[Dict]:
    """OpenAI 风格消息列表"""

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]
