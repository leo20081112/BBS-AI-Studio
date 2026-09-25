package mchorse.bbs_ai.integration.event;

import mchorse.bbs_ai.preview.PreviewContext;

/**
 * 进入预览模式事件
 *
 * <p>预览系统首次暂存数据、进入预览模式时发布。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PreviewModeEnterEvent
{
    /**
     * 触发进入预览的上下文
     */
    public final PreviewContext context;

    public PreviewModeEnterEvent(PreviewContext context)
    {
        this.context = context;
    }
}
