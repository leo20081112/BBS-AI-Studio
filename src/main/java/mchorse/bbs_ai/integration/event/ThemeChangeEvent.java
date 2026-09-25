package mchorse.bbs_ai.integration.event;

import mchorse.bbs_ai.ui.theme.UITheme;

/**
 * 主题切换事件
 *
 * <p>界面主题变更、UI 重排完成后发布（客户端）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ThemeChangeEvent
{
    /**
     * 新主题
     */
    public final UITheme newTheme;

    /**
     * 旧主题
     */
    public final UITheme oldTheme;

    public ThemeChangeEvent(UITheme newTheme, UITheme oldTheme)
    {
        this.newTheme = newTheme;
        this.oldTheme = oldTheme;
    }
}
