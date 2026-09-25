#
# BBS AI Studio 外部工具链
#
# Anthropic Claude 适配器（/v1/messages 协议）。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

from typing import Dict, Optional

from .api_provider import AIProviderBase, APIError


class ClaudeAdapter(AIProviderBase):
    """Claude messages 协议（x-api-key + anthropic-version）"""

    name = "claude"

    def build_request(self, system_prompt: str, user_prompt: str, image_base64: Optional[str]) -> Dict:
        url = "%s/v1/messages" % self.base_url

        if image_base64:
            content = [
                {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": image_base64}},
                {"type": "text", "text": user_prompt},
            ]
        else:
            content = [{"type": "text", "text": user_prompt}]

        payload = {
            "model": self.model,
            "system": [{"type": "text", "text": system_prompt}],
            "messages": [{"role": "user", "content": content}],
            "max_tokens": self.max_tokens,
            "temperature": self.temperature,
        }

        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "Content-Type": "application/json",
        }

        return url, headers, payload

    def parse_response(self, data: Dict) -> str:
        if "error" in data:
            error = data["error"]

            raise APIError("Claude 返回错误：%s" % error.get("message", data["error"]))

        blocks = data.get("content") or []
        text = "".join(block.get("text", "") for block in blocks if block.get("type") == "text")

        if not text:
            raise APIError("Claude 响应文本内容为空")

        return text

    def _extract_stream_delta(self, chunk: Dict) -> Optional[str]:
        if chunk.get("type") == "content_block_delta":
            delta = chunk.get("delta", {}) or {}

            return delta.get("text")

        return None
