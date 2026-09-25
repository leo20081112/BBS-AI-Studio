package mchorse.bbs_ai.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * API 响应解析器
 *
 * <p>负责解析各家厂商返回的 JSON 响应（OpenAI 兼容格式与 Anthropic 格式），
 * 并提供模型输出的清洗工具（剥离 Markdown 代码围栏、提取嵌入的 JSON 对象）。
 * 同时兼容流式（SSE data: 行）与非流式两种响应形态。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class APIResponseParser
{
    private APIResponseParser()
    {}

    /**
     * 解析 OpenAI 兼容格式的 chat.completions 响应，提取首条消息文本
     *
     * @param body 响应 JSON 文本
     * @return 消息文本
     * @throws APIException 响应结构异常或内容为空
     */
    public static String parseOpenAIResponse(String body) throws APIException
    {
        JsonObject root = parseJsonObject(body);

        /* 错误响应：{"error": {"message": ...}} */
        if (root.has("error"))
        {
            JsonElement error = root.get("error");
            String message = "未知错误";

            if (error.isJsonObject())
            {
                message = error.getAsJsonObject().get("message") == null
                    ? message
                    : error.getAsJsonObject().get("message").getAsString();
            }
            else if (error.isJsonPrimitive())
            {
                message = error.getAsString();
            }

            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "API 返回错误：" + message);
        }

        JsonArray choices = root.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0)
        {
            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "响应中没有 choices 内容");
        }

        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.getAsJsonObject("message");

        if (message != null && message.has("content"))
        {
            JsonElement content = message.get("content");

            /* 部分兼容服务会返回数组形式的 content */
            if (content.isJsonPrimitive())
            {
                return content.getAsString();
            }
            else if (content.isJsonArray())
            {
                StringBuilder builder = new StringBuilder();

                for (JsonElement part : content.getAsJsonArray())
                {
                    if (part.isJsonObject() && part.getAsJsonObject().has("text"))
                    {
                        builder.append(part.getAsJsonObject().get("text").getAsString());
                    }
                }

                return builder.toString();
            }
        }

        /* 兼容旧版 completions 格式 */
        if (first.has("text") && first.get("text").isJsonPrimitive())
        {
            return first.get("text").getAsString();
        }

        throw new APIException(APIException.ErrorCode.PARSE_ERROR, "无法从响应中提取消息内容");
    }

    /**
     * 解析 Anthropic Claude 的 /v1/messages 响应
     *
     * @param body 响应 JSON 文本
     * @return 拼接后的全部文本块
     * @throws APIException 响应结构异常或内容为空
     */
    public static String parseClaudeResponse(String body) throws APIException
    {
        JsonObject root = parseJsonObject(body);

        if (root.has("error"))
        {
            JsonObject error = root.getAsJsonObject("error");
            String message = error.has("message") ? error.get("message").getAsString() : "未知错误";

            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "Claude API 返回错误：" + message);
        }

        JsonArray content = root.getAsJsonArray("content");

        if (content == null || content.size() == 0)
        {
            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "Claude 响应中没有 content 内容");
        }

        StringBuilder builder = new StringBuilder();

        for (JsonElement element : content)
        {
            if (element.isJsonObject())
            {
                JsonObject block = element.getAsJsonObject();

                if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text"))
                {
                    builder.append(block.get("text").getAsString());
                }
            }
        }

        String result = builder.toString();

        if (result.isEmpty())
        {
            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "Claude 响应文本内容为空");
        }

        return result;
    }

    /**
     * 从 SSE 流式响应的完整文本中提取内容（兼容流式转发代理）
     * 每行形如 {@code data: {...}}，delta 内容位于 choices[0].delta.content
     */
    public static String parseOpenAIStream(String body) throws APIException
    {
        StringBuilder builder = new StringBuilder();

        for (String line : body.split("\n"))
        {
            line = line.trim();

            if (!line.startsWith("data:"))
            {
                continue;
            }

            String payload = line.substring(5).trim();

            if (payload.isEmpty() || "[DONE]".equals(payload))
            {
                continue;
            }

            try
            {
                JsonObject root = parseJsonObject(payload);
                JsonArray choices = root.getAsJsonArray("choices");

                if (choices != null && choices.size() > 0)
                {
                    JsonObject first = choices.get(0).getAsJsonObject();

                    if (first.has("delta"))
                    {
                        JsonObject delta = first.getAsJsonObject("delta");

                        if (delta.has("content") && delta.get("content").isJsonPrimitive())
                        {
                            builder.append(delta.get("content").getAsString());
                        }
                    }
                }
            }
            catch (Exception e)
            {
                /* 单个分块解析失败不影响整体 */
            }
        }

        String result = builder.toString();

        if (result.isEmpty())
        {
            throw new APIException(APIException.ErrorCode.PARSE_ERROR, "流式响应中没有可提取的内容");
        }

        return result;
    }

    /**
     * 解析 JSON 对象，失败时抛出 PARSE_ERROR
     */
    public static JsonObject parseJsonObject(String body) throws APIException
    {
        if (body == null || body.trim().isEmpty())
        {
            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "响应内容为空");
        }

        try
        {
            JsonElement element = JsonParser.parseString(body);

            if (!element.isJsonObject())
            {
                throw new APIException(APIException.ErrorCode.PARSE_ERROR, "响应不是 JSON 对象");
            }

            return element.getAsJsonObject();
        }
        catch (APIException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new APIException(APIException.ErrorCode.PARSE_ERROR, "JSON 解析失败：" + e.getMessage(), -1, e);
        }
    }

    /**
     * 清洗模型输出：剥离 Markdown 代码围栏（```json ... ```）等装饰文本，
     * 提取首个平衡的 JSON 对象子串
     *
     * @param raw 模型原始输出
     * @return 干净的 JSON 文本；找不到 JSON 时返回原始输出
     */
    public static String extractJsonBlock(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        String text = raw.trim();

        /* 先剥离代码围栏 */
        if (text.startsWith("```"))
        {
            int firstNewline = text.indexOf('\n');

            if (firstNewline > 0)
            {
                text = text.substring(firstNewline + 1);
            }

            int fenceEnd = text.lastIndexOf("```");

            if (fenceEnd >= 0)
            {
                text = text.substring(0, fenceEnd);
            }

            text = text.trim();
        }

        /* 已是完整 JSON */
        if (text.startsWith("{") && text.endsWith("}"))
        {
            return text;
        }

        /* 提取首个平衡的大括号区块 */
        int start = text.indexOf('{');

        if (start < 0)
        {
            return text;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start, count = text.length(); i < count; i++)
        {
            char c = text.charAt(i);

            if (escaped)
            {
                escaped = false;

                continue;
            }

            if (c == '\\')
            {
                escaped = true;

                continue;
            }

            if (c == '"')
            {
                inString = !inString;

                continue;
            }

            if (inString)
            {
                continue;
            }

            if (c == '{')
            {
                depth++;
            }
            else if (c == '}')
            {
                depth--;

                if (depth == 0)
                {
                    return text.substring(start, i + 1);
                }
            }
        }

        return text;
    }
}
