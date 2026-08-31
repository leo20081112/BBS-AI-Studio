#
# BBS AI Studio 外部工具链
#
# OpenAI 兼容适配器（OpenAI / 自定义端点，DeepSeek / GLM 见各自文件）。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

from typing import Dict, Optional

from .api_provider import AIProviderBase, APIError, join_messages


class OpenAIAdapter(AIProviderBase):
    """OpenAI chat.completions（视觉走 image_url data URI）"""

    name = "openai"

    def build_request(self, system_prompt: str, user_prompt: str, image_base64: Optional[str]) -> Dict:
        url = "%s/v1/chat/completions" % self.base_url

        messages = join_messages(system_prompt, user_prompt)

        if image_base64:
            messages[1]["content"] = [
                {"type": "text", "text": user_prompt},
                {"type": "image_url", "image_url": {"url": "data:image/png;base64," + image_base64}},
            ]

        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "stream": False,
        }

        headers = {
            "Authorization": "Bearer " + self.api_key,
            "Content-Type": "application/json",
        }

        return url, headers, payload

    def parse_response(self, data: Dict) -> str:
        if "error" in data:
            raise APIError("API 返回错误：%s" % data["error"])

        choices = data.get("choices") or []

        if not choices:
            raise APIError("响应中没有 choices 内容")

        message = choices[0].get("message", {})
        content = message.get("content", "")

        if isinstance(content, list):
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))

        return content or ""

    def _extract_stream_delta(self, chunk: Dict) -> Optional[str]:
        choices = chunk.get("choices") or []

        if not choices:
            return None

        delta = choices[0].get("delta", {}) or {}

        return delta.get("content")
