package mchorse.bbs_ai.integration.event;

import mchorse.bbs_ai.preview.PreviewContext;

/**
 * AI 生成完成事件
 *
 * <p>在 AI 生成（视频识别 / 分镜生成）完成并进入预览系统后发布。
 * 供 BBS addon 插件通过 {@code @Subscribe} 监听扩展【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIGenerationCompleteEvent
{
    /**
     * 生成结果进入的预览上下文
     */
    public final PreviewContext context;

    /**
     * 来源描述（如源视频文件名 / 分镜文本摘要）
     */
    public final String sourceDescription;

    public AIGenerationCompleteEvent(PreviewContext context, String sourceDescription)
    {
        this.context = context;
        this.sourceDescription = sourceDescription;
    }
}
