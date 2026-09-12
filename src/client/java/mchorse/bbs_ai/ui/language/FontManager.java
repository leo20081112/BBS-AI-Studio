package mchorse.bbs_ai.ui.language;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;

/**
 * 字体渲染管理器（客户端）
 *
 * <p>中文模式下的 UI 适配（项目规范模块 12）：
 * <ul>
 *   <li>英文：BBS 原有字体渲染器</li>
 *   <li>中文：依赖 BBS FontRenderer 的字符回退 + 针对宽字符的度量调整
 *       （按钮最小宽度 60 → 80、面板最小宽度 160 → 200、行高 1.2 → 1.4）</li>
 * </ul>
 * BBS 的 FontRenderer 本身按语言文件渲染任意 Unicode 文本，此处聚焦度量适配。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class FontManager
{
    /**
     * 英文模式按钮最小宽度
     */
    public static final int MIN_BUTTON_WIDTH_EN = 60;

    /**
     * 中文模式按钮最小宽度（CJK 字符较宽）
     */
    public static final int MIN_BUTTON_WIDTH_CJK = 80;

    /**
     * 英文模式面板最小宽度
     */
    public static final int MIN_PANEL_WIDTH_EN = 160;

    /**
     * 中文模式面板最小宽度
     */
    public static final int MIN_PANEL_WIDTH_CJK = 200;

    /**
     * 英文行高系数
     */
    public static final float LINE_HEIGHT_EN = 1.2F;

    /**
     * 中文行高系数
     */
    public static final float LINE_HEIGHT_CJK = 1.4F;

    private FontManager()
    {}

    /**
     * 当前语言是否为中文（CJK 宽字符适配）
     */
    public static boolean isCjk()
    {
        LanguageManager manager = LanguageManager.get();

        return manager.getLanguage().isChinese();
    }

    /**
     * 当前语言下的按钮最小宽度
     */
    public static int minButtonWidth()
    {
        return isCjk() ? MIN_BUTTON_WIDTH_CJK : MIN_BUTTON_WIDTH_EN;
    }

    /**
     * 当前语言下的面板最小宽度
     */
    public static int minPanelWidth()
    {
        return isCjk() ? MIN_PANEL_WIDTH_CJK : MIN_PANEL_WIDTH_EN;
    }

    /**
     * 当前语言下的行高系数
     */
    public static float lineHeight()
    {
        return isCjk() ? LINE_HEIGHT_CJK : LINE_HEIGHT_EN;
    }

    /**
     * 测量文本宽度（基于 BBS FontRenderer 的字符宽度估算，含 CJK 宽字符修正）
     */
    public static int measure(String text, FontRenderer font)
    {
        if (text == null || text.isEmpty())
        {
            return 0;
        }

        int width = font.getWidth(text);

        /* 中文场景下 BBS 字体渲染宽字符实际更宽，预留 15% 余量 */
        return isCjk() && containsCjk(text) ? (int) (width * 1.15F) : width;
    }

    /**
     * 文本是否包含 CJK 字符
     */
    public static boolean containsCjk(String text)
    {
        if (text == null)
        {
            return false;
        }

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (c >= 0x2E80 && c <= 0x9FFF || c >= 0xF900 && c <= 0xFAFF || c >= 0xFF00 && c <= 0xFFEF)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * 为元素应用当前语言的行高（对 column 布局的间距调整）
     */
    public static void applyMetrics(UIElement element)
    {
        /* column 间距随行高系数调整 */
        element.column((int) Math.max(4, 6 * lineHeight() / 1.2F));
    }
}
