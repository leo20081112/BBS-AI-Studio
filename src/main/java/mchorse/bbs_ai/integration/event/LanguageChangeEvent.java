package mchorse.bbs_ai.integration.event;

import mchorse.bbs_ai.ui.language.UILanguage;

/**
 * 语言切换事件
 *
 * <p>界面语言变更、文案刷新完成后发布（客户端）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class LanguageChangeEvent
{
    /**
     * 新语言
     */
    public final UILanguage newLanguage;

    /**
     * 旧语言
     */
    public final UILanguage oldLanguage;

    public LanguageChangeEvent(UILanguage newLanguage, UILanguage oldLanguage)
    {
        this.newLanguage = newLanguage;
        this.oldLanguage = oldLanguage;
    }
}
