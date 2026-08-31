package mchorse.bbs_ai.core.api;

import mchorse.bbs_ai.core.AIConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 游戏内 API 模式的 AI Provider（OkHttp 实现）
 *
 * <p>支持 OpenAI 兼容协议（OpenAI / DeepSeek / GLM / 自定义）与 Anthropic Claude 协议。
 * 视觉模式将 BufferedImage Base64 编码后按各协议规范发送；文本模式发送标准
 * chat.completions / messages 请求。</p>
 *
 * <p>超时策略（按规范）：连接超时 10 秒，读取超时 30 秒。
 * 全部网络 IO 均发生在后台线程，绝不阻塞渲染线程。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class InGameAPIProvider implements AIProvider
{
    /**
     * JSON 请求体类型
     */
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * HTTP 客户端（连接超时 10 秒、读取超时 30 秒、写入超时 30 秒）
     */
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    /**
     * 后台执行器
     */
    private final ExecutorService executor = AIProvider.createDefaultExecutor("BBS-AI-API");

    /**
     * 配置来源（实时读取，保证设置变更即时生效）
     */
    private final AIConfig config;

    public InGameAPIProvider(AIConfig config)
    {
        this.config = config;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, BufferedImage image) throws APIException
    {
        if (this.config.getProvider() == AIConfig.Provider.CLAUDE)
        {
            return this.requestClaude(systemPrompt, userPrompt, image);
        }

        return this.requestOpenAICompatible(systemPrompt, userPrompt, image);
    }

    /**
     * 发送 OpenAI 兼容请求（OpenAI / DeepSeek / GLM / 自定义）
     */
    private String requestOpenAICompatible(String systemPrompt, String userPrompt, BufferedImage image) throws APIException
    {
        String url = buildOpenAIUrl(this.config.getProvider(), this.config.getBaseUrl());

        JsonObject body = new JsonObject();

        body.addProperty("model", this.config.getModel());
        body.addProperty("temperature", this.config.getTemperature());
        body.addProperty("max_tokens", this.config.getMaxTokens());
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();

        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);

        JsonObject user = new JsonObject();

        user.addProperty("role", "user");

        if (image != null)
        {
            /* 视觉模式：文本 + 图片混合内容 */
            JsonArray content = new JsonArray();

            JsonObject textPart = new JsonObject();

            textPart.addProperty("type", "text");
            textPart.addProperty("text", userPrompt);
            content.add(textPart);

            JsonObject imagePart = new JsonObject();

            imagePart.addProperty("type", "image_url");

            JsonObject imageUrl = new JsonObject();

            imageUrl.addProperty("url", "data:image/png;base64," + encodeImage(image));
            imagePart.add("image_url", imageUrl);
            content.add(imagePart);

            user.add("content", content);
        }
        else
        {
            user.addProperty("content", userPrompt);
        }

        messages.add(user);
        body.add("messages", messages);

        /* GLM 需要携带 JWT 风格令牌时同样使用 Bearer 头（智谱兼容 OpenAI 鉴权） */
        Request request = this.buildRequest(url, body.toString(), false);

        return this.execute(request, false);
    }

    /**
     * 发送 Anthropic Claude 请求（/v1/messages 协议）
     */
    private String requestClaude(String systemPrompt, String userPrompt, BufferedImage image) throws APIException
    {
        String url = joinUrl(this.config.getBaseUrl(), "/v1/messages");

        JsonObject body = new JsonObject();

        body.addProperty("model", this.config.getModel());
        body.addProperty("max_tokens", this.config.getMaxTokens());
        body.addProperty("temperature", this.config.getTemperature());

        JsonObject system = new JsonObject();

        system.addProperty("type", "text");
        system.addProperty("text", systemPrompt);
        body.add("system", system);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();

        user.addProperty("role", "user");

        if (image != null)
        {
            JsonArray content = new JsonArray();

            JsonObject imageBlock = new JsonObject();

            imageBlock.addProperty("type", "image");

            JsonObject source = new JsonObject();

            source.addProperty("type", "base64");
            source.addProperty("media_type", "image/png");
            source.addProperty("data", encodeImage(image));
            imageBlock.add("source", source);
            content.add(imageBlock);

            JsonObject textBlock = new JsonObject();

            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", userPrompt);
            content.add(textBlock);

            user.add("content", content);
        }
        else
        {
            JsonArray content = new JsonArray();
            JsonObject textBlock = new JsonObject();

            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", userPrompt);
            content.add(textBlock);

            user.add("content", content);
        }

        messages.add(user);
        body.add("messages", messages);

        Request request = this.buildRequest(url, body.toString(), true);

        return this.execute(request, true);
    }

    /**
     * 构建带鉴权头的请求
     *
     * @param claude 是否为 Claude 协议（使用 x-api-key 头）
     */
    private Request buildRequest(String url, String jsonBody, boolean claude)
    {
        Request.Builder builder = new Request.Builder()
            .url(url)
            .post(RequestBody.create(JSON_TYPE, jsonBody))
            .header("Content-Type", "application/json");

        if (claude)
        {
            builder.header("x-api-key", this.config.getApiKey());
            builder.header("anthropic-version", "2023-06-01");
        }
        else
        {
            builder.header("Authorization", "Bearer " + this.config.getApiKey());
        }

        return builder.build();
    }

    /**
     * 执行请求并处理响应 / 错误映射
     *
     * @param claude 是否使用 Claude 响应解析
     */
    private String execute(Request request, boolean claude) throws APIException
    {
        try (Response response = this.client.newCall(request).execute())
        {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();

            if (!response.isSuccessful())
            {
                throw mapHttpError(response.code(), text);
            }

            String content = claude
                ? APIResponseParser.parseClaudeResponse(text)
                : APIResponseParser.parseOpenAIResponse(text);

            if (content == null || content.isEmpty())
            {
                throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "模型返回内容为空");
            }

            return content;
        }
        catch (APIException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            /* OkHttp 对超时与网络不可达都抛 IOException，通过异常链细分 */
            if (e instanceof java.net.SocketTimeoutException)
            {
                throw new APIException(APIException.ErrorCode.TIMEOUT, "请求超时", -1, e);
            }

            if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException)
            {
                throw new APIException(APIException.ErrorCode.NETWORK_UNREACHABLE, "网络不可达：" + e.getMessage(), -1, e);
            }

            throw new APIException(APIException.ErrorCode.NETWORK_UNREACHABLE, "网络请求失败：" + e.getMessage(), -1, e);
        }
    }

    /**
     * 将 HTTP 状态码映射为带用户提示的异常
     */
    private static APIException mapHttpError(int code, String body)
    {
        String detail = "";

        /* 尝试从错误响应中提取 message 字段 */
        try
        {
            JsonObject root = APIResponseParser.parseJsonObject(body);

            if (root.has("error"))
            {
                JsonElement error = root.get("error");

                if (error.isJsonObject() && error.getAsJsonObject().has("message"))
                {
                    detail = error.getAsJsonObject().get("message").getAsString();
                }
                else if (error.isJsonPrimitive())
                {
                    detail = error.getAsString();
                }
            }
        }
        catch (Exception ignored)
        {}

        String suffix = detail.isEmpty() ? "" : "：" + detail;

        if (code == 401 || code == 403)
        {
            return new APIException(APIException.ErrorCode.UNAUTHORIZED, "认证失败（" + code + "）" + suffix, code);
        }

        if (code == 429)
        {
            return new APIException(APIException.ErrorCode.RATE_LIMITED, "速率限制（429）" + suffix, code);
        }

        if (code >= 500)
        {
            return new APIException(APIException.ErrorCode.SERVER_ERROR, "服务端错误（" + code + "）" + suffix, code);
        }

        return new APIException(APIException.ErrorCode.BAD_REQUEST, "请求错误（" + code + "）" + suffix, code);
    }

    /**
     * 将 BufferedImage 编码为 PNG Base64
     */
    private static String encodeImage(BufferedImage image) throws APIException
    {
        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            ImageIO.write(image, "png", output);

            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
        catch (IOException e)
        {
            throw new APIException(APIException.ErrorCode.INVALID_RESPONSE, "图片编码失败：" + e.getMessage(), -1, e);
        }
    }

    /**
     * 根据厂商拼接 OpenAI 兼容端点 URL
     */
    public static String buildOpenAIUrl(AIConfig.Provider provider, String baseUrl)
    {
        String base = baseUrl == null ? "" : baseUrl.trim();

        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }

        switch (provider)
        {
            case OPENAI: return joinUrl(base, "/v1/chat/completions");
            case DEEPSEEK: return joinUrl(base, "/chat/completions");
            case GLM: return joinUrl(base, "/v4/chat/completions");
            default:
                /* 自定义端点：已含 /v1 则直接拼接，否则补全 */
                return base.endsWith("/v1") ? joinUrl(base, "/chat/completions") : joinUrl(base, "/v1/chat/completions");
        }
    }

    /**
     * 拼接基础 URL 与路径
     */
    private static String joinUrl(String base, String path)
    {
        if (base.isEmpty())
        {
            return path;
        }

        return base + path;
    }

    @Override
    public ExecutorService getExecutor()
    {
        return this.executor;
    }

    @Override
    public void shutdown()
    {
        this.executor.shutdownNow();
        this.client.dispatcher().executorService().shutdown();
        this.client.connectionPool().evictAll();
    }
}
