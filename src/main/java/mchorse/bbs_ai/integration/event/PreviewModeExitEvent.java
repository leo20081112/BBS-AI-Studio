package mchorse.bbs_ai.integration.event;

/**
 * 退出预览模式事件
 *
 * <p>烘焙（写入正式 Film）或放弃预览时发布。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PreviewModeExitEvent
{
    /**
     * 是否为烘焙退出（true = 已写入正式数据；false = 放弃预览）
     */
    public final boolean baked;

    public PreviewModeExitEvent(boolean baked)
    {
        this.baked = baked;
    }
}
