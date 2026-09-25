#
# BBS AI Studio 外部工具链
#
# DeepSeek 适配器（OpenAI 兼容，端点 /chat/completions）。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

from typing import Dict, Optional

from .openai_adapter import OpenAIAdapter


class DeepSeekAdapter(OpenAIAdapter):
    """DeepSeek（base_url 默认 https://api.deepseek.com，路径不带 /v1）"""

    name = "deepseek"

    def build_request(self, system_prompt: str, user_prompt: str, image_base64: Optional[str]) -> Dict:
        url, headers, payload = super().build_request(system_prompt, user_prompt, image_base64)

        # DeepSeek 端点为 {base}/chat/completions
        url = self.base_url + "/chat/completions"

        return url, headers, payload
