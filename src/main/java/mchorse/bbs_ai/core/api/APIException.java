package mchorse.bbs_ai.core.api;

/**
 * AI API 异常体系
 *
 * <p>携带错误码与面向用户的提示信息，覆盖规范要求的全部错误场景：
 * 网络不可达、401、429、5xx、超时、JSON 解析失败等。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class APIException extends Exception
{
    /**
     * 错误码枚举
     */
    public enum ErrorCode
    {
        /**
         * 网络不可达
         */
        NETWORK_UNREACHABLE,

        /**
         * 认证失败（HTTP 401）
         */
        UNAUTHORIZED,

        /**
         * 速率限制（HTTP 429）
         */
        RATE_LIMITED,

        /**
         * 服务端错误（HTTP 5xx）
         */
        SERVER_ERROR,

        /**
         * 客户端请求错误（其他 4xx）
         */
        BAD_REQUEST,

        /**
         * 请求超时
         */
        TIMEOUT,

        /**
         * JSON 解析失败 / 模型返回格式异常
         */
        PARSE_ERROR,

        /**
         * 响应内容为空或结构不完整
         */
        INVALID_RESPONSE,

        /**
         * 本地推理失败（模型加载失败等）
         */
        LOCAL_INFERENCE_ERROR,

        /**
         * 用户取消
         */
        CANCELLED
    }

    /**
     * 错误码
     */
    private final ErrorCode code;

    /**
     * HTTP 状态码（非 HTTP 错误时为 -1）
     */
    private final int httpCode;

    public APIException(ErrorCode code, String message)
    {
        this(code, message, -1, null);
    }

    public APIException(ErrorCode code, String message, int httpCode)
    {
        this(code, message, httpCode, null);
    }

    public APIException(ErrorCode code, String message, int httpCode, Throwable cause)
    {
        super(message, cause);

        this.code = code;
        this.httpCode = httpCode;
    }

    public ErrorCode getCode()
    {
        return this.code;
    }

    public int getHttpCode()
    {
        return this.httpCode;
    }

    /**
     * 生成面向用户的提示信息（中文）
     */
    public String getUserMessage()
    {
        switch (this.code)
        {
            case NETWORK_UNREACHABLE: return "网络不可达，请检查网络连接与代理设置";
            case UNAUTHORIZED: return "API Key 无效或已过期（401），请检查密钥配置";
            case RATE_LIMITED: return "触发速率限制（429），请稍后重试";
            case SERVER_ERROR: return "服务端错误（" + this.httpCode + "），建议稍后重试";
            case BAD_REQUEST: return "请求被拒绝（" + this.httpCode + "）：请检查模型名称与参数";
            case TIMEOUT: return "请求超时，请检查网络或稍后重试";
            case PARSE_ERROR: return "模型返回格式异常，无法解析响应";
            case INVALID_RESPONSE: return "模型返回内容为空";
            case LOCAL_INFERENCE_ERROR: return "本地推理失败：" + this.getMessage();
            case CANCELLED: return "操作已取消";
            default: return this.getMessage();
        }
    }
}
