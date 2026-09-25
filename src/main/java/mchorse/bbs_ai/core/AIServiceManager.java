package mchorse.bbs_ai.core;

import mchorse.bbs_ai.core.api.AIExceptionHolder;
import mchorse.bbs_ai.core.api.AIProvider;
import mchorse.bbs_ai.core.api.APIException;
import mchorse.bbs_ai.core.api.InGameAPIProvider;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AI 服务总控（单例）
 *
 * <p>管理 AI 配置与 Provider 的生命周期，对外提供统一的生成 / 测试连接入口。
 * 在底层启动链（{@code mchorse.bbs_mod.ai.AICore}，由 BBSMod.onInitialize 调用）中初始化。</p>
 *
 * <p>接口契约：
 * <ul>
 *   <li>{@link #generateAsync}：供分镜生成等模块调用，回调发生在后台线程，
 *       调用方负责把结果切回渲染线程</li>
 *   <li>{@link #updateConfig}：保存配置并重建 Provider，线程安全</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIServiceManager
{
    /**
     * 单例实例
     */
    private static AIServiceManager instance;

    /**
     * AI 配置（volatile 保证跨线程可见）
     */
    private volatile AIConfig config;

    /**
     * 当前 Provider（volatile 保证跨线程可见）
     */
    private volatile AIProvider provider;

    /**
     * 是否已初始化
     */
    private static boolean initialized;

    /**
     * 初始化 AI 核心服务【原版兼容】（在整合初始化最后调用）
     */
    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        instance = new AIServiceManager();
        instance.config = ConfigSyncManager.load();
        instance.provider = createProvider(instance.config);
        initialized = true;

        System.out.println("[BBS AI] AI 核心服务初始化完成（模式：" + instance.config.getMode()
            + "，厂商：" + instance.config.getProvider().title + "）");
    }

    /**
     * 是否已初始化
     */
    public static boolean isInitialized()
    {
        return initialized;
    }

    /**
     * 获取单例
     */
    public static AIServiceManager get()
    {
        if (!initialized)
        {
            /* 延迟兜底初始化，避免调用顺序问题 */
            initialize();
        }

        return instance;
    }

    /**
     * 根据配置创建 Provider
     */
    private static AIProvider createProvider(AIConfig config)
    {
        return new InGameAPIProvider(config);
    }

    /**
     * 获取当前配置（只读视图，请勿直接修改；修改请走 {@link #updateConfig}）
     */
    public AIConfig getConfig()
    {
        return this.config;
    }

    /**
     * 更新配置：深拷贝替换 + 加密持久化 + 重建 Provider（线程安全）
     */
    public synchronized void updateConfig(AIConfig newConfig)
    {
        AIConfig copy = newConfig == null ? new AIConfig() : newConfig.copy();

        this.config = copy;
        ConfigSyncManager.save(copy);

        AIProvider old = this.provider;

        this.provider = createProvider(copy);

        if (old != null)
        {
            old.shutdown();
        }
    }

    /**
     * 异步生成文本补全（线程安全，回调在后台线程执行）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param image        可选图片（视觉模式）
     * @param onSuccess    成功回调（后台线程）
     * @param onError      失败回调（后台线程）
     * @return 可取消的 Future
     */
    public CompletableFuture<String> generateAsync(String systemPrompt, String userPrompt, BufferedImage image, Consumer<String> onSuccess, Consumer<APIException> onError)
    {
        AIProvider current = this.provider;

        return current.generateAsync(systemPrompt, userPrompt, image)
            .thenApply((result) ->
            {
                if (onSuccess != null)
                {
                    onSuccess.accept(result);
                }

                return result;
            })
            .exceptionally((throwable) ->
            {
                APIException exception = AIExceptionHolder.unwrap(throwable);

                if (onError != null && !(exception.getCode() == APIException.ErrorCode.CANCELLED))
                {
                    onError.accept(exception);
                }

                return null;
            });
    }

    /**
     * 异步测试连接
     *
     * @param onSuccess 成功回调（后台线程）
     * @param onError   失败回调（后台线程）
     */
    public CompletableFuture<Boolean> testConnectionAsync(Consumer<Boolean> onSuccess, Consumer<APIException> onError)
    {
        return this.provider.testConnection()
            .thenApply((ok) ->
            {
                if (onSuccess != null)
                {
                    onSuccess.accept(ok);
                }

                return ok;
            })
            .exceptionally((throwable) ->
            {
                APIException exception = AIExceptionHolder.unwrap(throwable);

                if (onError != null)
                {
                    onError.accept(exception);
                }

                return false;
            });
    }

    /**
     * 是否处于 API 模式（分镜等文本生成能力仅 API 模式可用）
     */
    public boolean isApiMode()
    {
        return this.config.getMode() == AIConfig.Mode.API;
    }

    /**
     * 释放全部资源（游戏退出时调用）
     */
    public static synchronized void shutdown()
    {
        if (instance != null && instance.provider != null)
        {
            instance.provider.shutdown();
        }

        initialized = false;
    }
}
