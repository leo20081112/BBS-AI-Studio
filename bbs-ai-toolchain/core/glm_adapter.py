#
# BBS AI Studio 外部工具链
#
# 智谱 GLM 适配器（OpenAI 兼容，端点 /v4/chat/completions）。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

from typing import Dict, Optional

from .openai_adapter import OpenAIAdapter


class GLMAdapter(OpenAIAdapter):
    """智谱 GLM（base_url 默认 https://open.bigmodel.cn/api/paas，路径 /v4/chat/completions）"""

    name = "glm"

    def build_request(self, system_prompt: str, user_prompt: str, image_base64: Optional[str]) -> Dict:
        url, headers, payload = super().build_request(system_prompt, user_prompt, image_base64)

        # GLM 端点为 {base}/v4/chat/completions
        url = self.base_url + "/v4/chat/completions"

        return url, headers, payload
