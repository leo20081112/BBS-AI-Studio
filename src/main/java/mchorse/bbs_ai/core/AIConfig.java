package mchorse.bbs_ai.core;

/**
 * AI 配置数据类
 *
 * <p>保存 AI 服务的全部配置项：工作模式（本地 / API）、厂商、API Key、
 * Base URL、模型名称、采样参数等。配置持久化由 {@link ConfigSyncManager} 负责，
 * 该类本身只是纯数据载体 + 序列化辅助。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIConfig
{
    /**
     * 工作模式
     */
    public enum Mode
    {
        LOCAL, API
    }

    /**
     * 支持的 API 厂商
     */
    public enum Provider
    {
        OPENAI("OpenAI", "https://api.openai.com", "gpt-4o-mini"),
        CLAUDE("Claude", "https://api.anthropic.com", "claude-3-5-sonnet-latest"),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
        GLM("GLM (智谱)", "https://open.bigmodel.cn/api/paas", "glm-4-flash"),
        CUSTOM("自定义", "http://localhost:8000", "custom-model");

        /**
         * 厂商显示名称
         */
        public final String title;

        /**
         * 厂商默认 Base URL
         */
        public final String defaultBaseUrl;

        /**
         * 厂商默认模型
         */
        public final String defaultModel;

        Provider(String title, String defaultBaseUrl, String defaultModel)
        {
            this.title = title;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultModel = defaultModel;
        }
    }

    /**
     * 当前工作模式（默认 API 模式，本地 ONNX 需要额外模型文件）
     */
    private Mode mode = Mode.API;

    /**
     * 当前 API 厂商
     */
    private Provider provider = Provider.OPENAI;

    /**
     * API Key（内存中为明文，持久化时由 EncryptionUtil 加密）
     */
    private String apiKey = "";

    /**
     * API Base URL（支持自定义代理）
     */
    private String baseUrl = Provider.OPENAI.defaultBaseUrl;

    /**
     * 模型名称
     */
    private String model = Provider.OPENAI.defaultModel;

    /**
     * 采样温度（0.0 ~ 2.0）
     */
    private float temperature = 0.7F;

    /**
     * 最大生成 token 数（256 ~ 8192）
     */
    private int maxTokens = 4096;

    public Mode getMode()
    {
        return this.mode;
    }

    public void setMode(Mode mode)
    {
        this.mode = mode == null ? Mode.API : mode;
    }

    public Provider getProvider()
    {
        return this.provider;
    }

    public void setProvider(Provider provider)
    {
        this.provider = provider == null ? Provider.OPENAI : provider;
    }

    public String getApiKey()
    {
        return this.apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getBaseUrl()
    {
        return this.baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl == null || baseUrl.isEmpty() ? this.provider.defaultBaseUrl : baseUrl;
    }

    public String getModel()
    {
        return this.model;
    }

    public void setModel(String model)
    {
        this.model = model == null || model.isEmpty() ? this.provider.defaultModel : model;
    }

    public float getTemperature()
    {
        return this.temperature;
    }

    public void setTemperature(float temperature)
    {
        this.temperature = Math.max(0.0F, Math.min(2.0F, temperature));
    }

    public int getMaxTokens()
    {
        return this.maxTokens;
    }

    public void setMaxTokens(int maxTokens)
    {
        this.maxTokens = Math.max(256, Math.min(8192, maxTokens));
    }

    /**
     * 切换厂商时自动补全默认 Base URL 与模型（若当前值为空或属于上一个厂商的默认值）
     */
    public void applyProvider(Provider provider)
    {
        this.setProvider(provider);
        this.setBaseUrl(provider.defaultBaseUrl);
        this.setModel(provider.defaultModel);
    }

    /**
     * 从另一个配置对象复制全部字段
     */
    public void copyFrom(AIConfig other)
    {
        if (other == null)
        {
            return;
        }

        this.setMode(other.getMode());
        this.setProvider(other.getProvider());
        this.setApiKey(other.getApiKey());
        this.setBaseUrl(other.getBaseUrl());
        this.setModel(other.getModel());
        this.setTemperature(other.getTemperature());
        this.setMaxTokens(other.getMaxTokens());
    }

    /**
     * 创建一份深拷贝
     */
    public AIConfig copy()
    {
        AIConfig config = new AIConfig();

        config.copyFrom(this);

        return config;
    }
}
