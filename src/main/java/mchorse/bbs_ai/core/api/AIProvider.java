package mchorse.bbs_ai.core.api;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * AI 服务统一接口
 *
 * <p>所有 AI 能力（文本生成分镜、视觉识别等）都通过本接口调用。
 * 实现类必须保证 {@link #generate(String, String, BufferedImage)} 可以在
 * 任意工作线程调用，绝不触碰 Minecraft 渲染线程。</p>
 *
 * <p>接口契约：
 * <ul>
 *   <li>{@code generate}：阻塞式调用，必须在后台线程调用；失败时抛出 {@link APIException}</li>
 *   <li>{@code generateAsync}：默认方法，提交到实现类的后台线程池，通过
 *       {@link CompletableFuture} 回调，不阻塞渲染线程</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface AIProvider
{
    /**
     * 生成补全结果（阻塞式，必须在后台线程调用）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param image        可选图片（视觉模式）；null 表示纯文本模式
     * @return 模型返回的文本内容
     * @throws APIException 网络 / 认证 / 解析等异常
     */
    String generate(String systemPrompt, String userPrompt, BufferedImage image) throws APIException;

    /**
     * 异步生成补全结果（线程安全，可在渲染线程调用）
     */
    default CompletableFuture<String> generateAsync(String systemPrompt, String userPrompt, BufferedImage image)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                return generate(systemPrompt, userPrompt, image);
            }
            catch (APIException e)
            {
                throw new RuntimeException(e);
            }
        }, getExecutor());
    }

    /**
     * 测试连接：发送一次最小请求验证配置是否可用
     *
     * @return 成功时完成的 Future；失败时以 {@link APIException} 异常完成
     */
    default CompletableFuture<Boolean> testConnection()
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                String reply = generate("You are a connection tester. Reply with exactly: pong", "ping", null);

                return reply != null && !reply.isEmpty();
            }
            catch (APIException e)
            {
                throw new RuntimeException(e);
            }
        }, getExecutor());
    }

    /**
     * 获取提供者的后台执行器（用于异步回调）
     */
    ExecutorService getExecutor();

    /**
     * 释放资源（线程池、HTTP 客户端等）
     */
    void shutdown();

    /**
     * 创建一个单线程执行器的默认实现（供实现类复用）
     */
    static ExecutorService createDefaultExecutor(String name)
    {
        return Executors.newSingleThreadExecutor((runnable) ->
        {
            Thread thread = new Thread(runnable, name);

            thread.setDaemon(true);

            return thread;
        });
    }
}
